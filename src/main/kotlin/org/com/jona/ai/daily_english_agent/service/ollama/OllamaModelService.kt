package org.com.jona.ai.daily_english_agent.service.ollama

import jakarta.annotation.PostConstruct
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.HttpURLConnection
import java.net.URI

/**
 * Manages the Ollama model lifecycle via the Ollama HTTP API.
 *
 * Uses GET  /api/tags  to check whether the model is already present.
 * Uses POST /api/pull  to pull the model when it is missing.
 *
 * The ollama CLI binary is NOT used — it does not exist inside the
 * Docker container.  All communication goes through the HTTP endpoint
 * configured via ollama.base.url (host.docker.internal:11434 in Docker,
 * 127.0.0.1:11434 in local dev).
 */
@Service
class OllamaModelService(
    @Value("\${ollama.base.url}") private val ollamaBaseUrl: String,
    @Value("\${ollama.model.name}") private val modelName: String,
    @Value("\${ollama.timeout.seconds}") private val timeoutSeconds: Long,
    @Value("\${vocabulary.retry.max.attempts}") private val maxAttempts: Int,
    @Value("\${vocabulary.retry.delay.seconds}") private val retryDelaySeconds: Long
) {
    private val logger = LoggerFactory.getLogger(OllamaModelService::class.java)
    private val lenientJson = Json { ignoreUnknownKeys = true }

    @Serializable data class OllamaModel(val name: String)
    @Serializable data class OllamaTagsResponse(val models: List<OllamaModel> = emptyList())

    @PostConstruct
    fun validateAndPullModel() {
        runBlocking {
            var attempt = 0
            var success = false

            while (attempt < maxAttempts && !success) {
                attempt++
                logger.info("Attempt $attempt/$maxAttempts: Validating Ollama model via HTTP API...")

                try {
                    if (checkModelExists()) {
                        logger.info("Ollama model $modelName is already available")
                        success = true
                    } else {
                        logger.info("Ollama model $modelName not found. Attempting to pull via HTTP API...")
                        if (pullModel()) {
                            logger.info("Successfully pulled Ollama model $modelName")
                            success = true
                        } else {
                            logger.warn("Failed to pull Ollama model $modelName on attempt $attempt")
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Error during Ollama model validation on attempt $attempt: ${e.message}")
                }

                if (!success && attempt < maxAttempts) {
                    logger.info("Waiting ${retryDelaySeconds}s before retry...")
                    delay(retryDelaySeconds * 1000)
                }
            }

            if (!success) {
                // Non-fatal: the app continues to start.
                // Word generation will fail gracefully and retry on the next cron tick.
                logger.warn("Could not validate/pull Ollama model after $maxAttempts attempts — continuing startup")
            }
        }
    }

    suspend fun checkModelExists(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URI("$ollamaBaseUrl/api/tags").toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5_000
            conn.readTimeout = 10_000

            if (conn.responseCode != 200) {
                logger.warn("Ollama /api/tags returned HTTP ${conn.responseCode}")
                conn.disconnect()
                return@withContext false
            }

            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val tagsResponse = lenientJson.decodeFromString<OllamaTagsResponse>(body)
            // Match on the base name (before ':') to handle "llama3.2:latest" vs "llama3.2"
            val baseModel = modelName.substringBefore(":")
            tagsResponse.models.any { it.name.startsWith(baseModel) }
        } catch (e: Exception) {
            logger.error("Error checking Ollama model via HTTP API: ${e.message}")
            false
        }
    }

    suspend fun pullModel(): Boolean = withContext(Dispatchers.IO) {
        try {
            withTimeout(timeoutSeconds * 1000) {
                logger.info("Pulling Ollama model $modelName via HTTP API (timeout: ${timeoutSeconds}s)...")

                val url = URI("$ollamaBaseUrl/api/pull").toURL()
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 10_000
                conn.readTimeout = (timeoutSeconds * 1000).toInt()

                val payload = """{"name":"$modelName","stream":false}"""
                conn.outputStream.bufferedWriter().use { it.write(payload) }

                val responseCode = conn.responseCode
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                if (responseCode == 200) {
                    logger.info("Model pull succeeded: ${body.take(200)}")
                    true
                } else {
                    logger.error("Model pull failed — HTTP $responseCode: ${body.take(200)}")
                    false
                }
            }
        } catch (e: TimeoutCancellationException) {
            logger.error("Timeout pulling model $modelName after ${timeoutSeconds}s")
            false
        } catch (e: Exception) {
            logger.error("Error pulling model via HTTP API: ${e.message}")
            false
        }
    }
}

