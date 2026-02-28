package org.com.jona.ai.daily_english_agent.service.agent

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
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
    }

    suspend fun generateWord(excludeWords: List<String>): WordOfTheDayData = withContext(Dispatchers.IO) {
        try {
            withTimeout(timeoutSeconds * 1000) {
                logger.info("Generating new vocabulary word (excluding ${excludeWords.size} words)...")

                val excludeList = if (excludeWords.isEmpty()) "none" else excludeWords.joinToString(", ")

                val prompt = """
                    Generate ONE unique English vocabulary word for daily learning.
                    
                    EXCLUDED WORDS (do not use any of these): $excludeList
                    
                    Return ONLY the information in this EXACT format (one line, pipe-separated):
                    word|part_of_speech|synonym1 | synonym2 | synonym3|english_example_sentence|spanish_translation_sentence
                    
                    Requirements:
                    - Choose an intermediate to advanced level word
                    - Provide exactly 3 synonyms separated by " | " (with spaces around pipes)
                    - English example should be a complete sentence using the word
                    - Spanish example should be the translation of the English sentence
                    - Return ONLY the formatted line, no additional text
                    
                    Example format:
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

                // Parse the JSON response
                val ollamaResponse = Json.decodeFromString<OllamaResponse>(responseText)
                logger.info("Parsed response: ${ollamaResponse.response.take(100)}...")

                parseResponse(ollamaResponse.response)
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
            // Clean the response - remove any markdown formatting, extra whitespace, etc.
            val cleaned = response.trim()
                .replace("```", "")
                .replace("`", "")
                .lines()
                .firstOrNull { it.contains("|") && it.split("|").size >= 5 }
                ?: throw IllegalArgumentException("No valid pipe-separated data found in response")

            val parts = cleaned.split("|")

            if (parts.size < 5) {
                throw IllegalArgumentException("Response does not have enough parts: ${parts.size}")
            }

            return WordOfTheDayData(
                word = parts[0].trim(),
                partOfSpeech = parts[1].trim(),
                synonyms = parts[2].trim(),
                exampleEnglish = parts.getOrNull(3)?.trim() ?: "",
                exampleSpanish = parts.getOrNull(4)?.trim() ?: ""
            )
        } catch (e: Exception) {
            logger.error("Error parsing LLM response: ${e.message}. Response was: $response", e)
            throw RuntimeException("Failed to parse vocabulary word response", e)
        }
    }
}


