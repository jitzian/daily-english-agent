package org.com.jona.ai.daily_english_agent.service.agent

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.com.jona.ai.daily_english_agent.model.WordOfTheDayData
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Serializable
data class OllamaRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean = false
)

@Serializable
data class OllamaResponse(
    val response: String
)

@Service
class VocabularyAgentService(
    @Value("\${ollama.base.url}") private val ollamaBaseUrl: String,
    @Value("\${ollama.model.name}") private val modelName: String,
    @Value("\${ollama.timeout.seconds}") private val timeoutSeconds: Long
) {
    private val logger = LoggerFactory.getLogger(VocabularyAgentService::class.java)

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        // Explicit timeouts so the CIO engine never fires before our coroutine
        // withTimeout does.  requestTimeoutMillis covers the full round-trip
        // (connect + send + receive), which is what we want for slow LLM responses.
        // socketTimeoutMillis is set generously to avoid cutting streaming tokens.
        install(HttpTimeout) {
            requestTimeoutMillis = timeoutSeconds * 1_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis  = timeoutSeconds * 1_000
        }
    }

    suspend fun generateWord(excludeWords: List<String>): WordOfTheDayData = withContext(Dispatchers.IO) {
        try {
            withTimeout(timeoutSeconds * 1000) {
                logger.info("Generating new vocabulary word (excluding ${excludeWords.size} words)...")

                val excludeList = if (excludeWords.isEmpty()) "none" else excludeWords.joinToString(", ")

                val prompt = """
                    You are a vocabulary assistant. Output ONLY a single line in this exact pipe-separated format with NO extra text, NO explanations, NO blank lines:
                    word|part_of_speech|synonym1 | synonym2 | synonym3|English example sentence|Spanish translation sentence
                    
                    Rules:
                    - word: one English word, intermediate to advanced level
                    - part_of_speech: noun, verb, adjective, or adverb
                    - synonyms: exactly 3 words separated by " | "
                    - English sentence: one complete sentence using the word
                    - Spanish sentence: translation of the English sentence
                    - DO NOT use any of these words: $excludeList
                    - OUTPUT ONLY THE SINGLE LINE, NOTHING ELSE
                    
                    Example (copy this exact structure):
                    incredible|adjective|unbelievable | extraordinary | remarkable|An incredible story of triumph and tragedy|Una historia increíble de triunfo y tragedia
                """.trimIndent()

                val response = httpClient.post("$ollamaBaseUrl/api/generate") {
                    contentType(ContentType.Application.Json)
                    setBody(OllamaRequest(
                        model = modelName,
                        prompt = prompt,
                        stream = false
                    ))
                }

                val responseText: String = response.body()
                logger.info("Received raw response from LLM: ${responseText.take(200)}...")

                // Ollama returns NDJSON even with stream=false — one JSON object per line.
                // Each line's "response" field is a single token (word or punctuation).
                // We join them directly (no separator) because Ollama already includes
                // any needed whitespace inside the token itself.
                val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }
                val fullResponse = responseText
                    .lines()
                    .filter { it.isNotBlank() }
                    .mapNotNull { line ->
                        try { lenientJson.decodeFromString<OllamaResponse>(line).response }
                        catch (_: Exception) { null }
                    }
                    .joinToString("")   // tokens already carry their own spacing

                logger.info("Assembled LLM response: ${fullResponse.take(300)}...")
                parseResponse(fullResponse)
            }
        } catch (e: TimeoutCancellationException) {
            logger.error("Timeout generating word after ${timeoutSeconds}s")
            throw RuntimeException("Timeout generating vocabulary word", e)
        } catch (e: Exception) {
            logger.error("Error generating word: ${e.message}", e)
            throw RuntimeException("Failed to generate vocabulary word", e)
        }
    }

    private fun parseResponse(response: String): WordOfTheDayData {
        try {
            val cleaned = response.trim()
                .replace("```", "")
                .replace("`", "")

            // --- Strategy 1: expected pipe-separated single line ---
            val pipeLine = cleaned
                .lines()
                .firstOrNull { it.contains("|") && it.split("|").size >= 5 }

            if (pipeLine != null) {
                val parts = pipeLine.split("|")
                // The synonym field itself uses " | " separators (e.g. "syn1 | syn2 | syn3"),
                // giving us 7 total parts instead of 5.  Re-join the middle parts.
                val word         = parts[0].trim()
                val pos          = parts[1].trim()
                // Last 2 parts are always English + Spanish examples
                val exampleEs    = parts.last().trim()
                val exampleEn    = parts[parts.size - 2].trim()
                // Everything in between is the synonyms field (may be 1 or 3 tokens)
                val synonyms     = parts.subList(2, parts.size - 2).joinToString(" | ") { it.trim() }
                logger.info("Parsed via pipe format: $word")
                return WordOfTheDayData(
                    word           = word,
                    partOfSpeech   = pos,
                    synonyms       = synonyms,
                    exampleEnglish = exampleEn,
                    exampleSpanish = exampleEs
                )
            }

            // --- Strategy 2: label-based multiline fallback ---
            // e.g. "Word: serendipity\nPart of speech: noun\n..."
            val lines = cleaned.lines().map { it.trim() }.filter { it.isNotBlank() }
            fun findLabel(vararg labels: String): String? =
                lines.firstNotNullOfOrNull { line ->
                    labels.firstNotNullOfOrNull { lbl ->
                        if (line.startsWith(lbl, ignoreCase = true))
                            line.substringAfter(":").trim().also {}
                        else null
                    }
                }

            val word    = findLabel("Word")
            val pos     = findLabel("Part of speech", "Part-of-speech", "Type", "POS")
            val syns    = findLabel("Synonym", "Synonyms")
            val engEx   = findLabel("English", "Example", "Sentence")
            val spaEx   = findLabel("Spanish", "Translation")

            if (word != null && pos != null) {
                logger.info("Parsed via label format: $word")
                return WordOfTheDayData(
                    word          = word,
                    partOfSpeech  = pos,
                    synonyms      = syns ?: "",
                    exampleEnglish = engEx ?: "",
                    exampleSpanish = spaEx ?: ""
                )
            }

            // --- Strategy 3: first word on first non-empty line (last resort) ---
            if (lines.isNotEmpty()) {
                val firstWord = lines[0].split(" ", "\t").first().trim()
                logger.warn("Falling back to first-word extraction: '$firstWord'")
                return WordOfTheDayData(
                    word          = firstWord,
                    partOfSpeech  = lines.getOrNull(1) ?: "unknown",
                    synonyms      = lines.getOrNull(2) ?: "",
                    exampleEnglish = lines.getOrNull(3) ?: "",
                    exampleSpanish = lines.getOrNull(4) ?: ""
                )
            }

            throw IllegalArgumentException("Could not extract any word data from LLM response")
        } catch (e: Exception) {
            logger.error("Error parsing LLM response: ${e.message}. Response was: $response", e)
            throw RuntimeException("Failed to parse vocabulary word response", e)
        }
    }
}


