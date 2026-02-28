package org.com.jona.ai.daily_english_agent.service.ollama

import jakarta.annotation.PostConstruct
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.InputStreamReader

@Service
class OllamaModelService(
    @Value("\${ollama.model.name}") private val modelName: String,
    @Value("\${ollama.timeout.seconds}") private val timeoutSeconds: Long,
    @Value("\${vocabulary.retry.max.attempts}") private val maxAttempts: Int,
    @Value("\${vocabulary.retry.delay.seconds}") private val retryDelaySeconds: Long
) {
    private val logger = LoggerFactory.getLogger(OllamaModelService::class.java)

    @PostConstruct
    fun validateAndPullModel() {
        runBlocking {
            var attempt = 0
            var success = false

            while (attempt < maxAttempts && !success) {
                attempt++
                logger.info("Attempt $attempt/$maxAttempts: Validating Ollama model...")

                try {
                    if (checkModelExists()) {
                        logger.info("Ollama model $modelName is already available")
                        success = true
                    } else {
                        logger.info("Ollama model $modelName not found. Attempting to pull...")
                        if (pullModel()) {
                            logger.info("Successfully pulled Ollama model $modelName")
                            success = true
                        } else {
                            logger.warn("Failed to pull Ollama model $modelName on attempt $attempt")
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Error during Ollama model validation on attempt $attempt: ${e.message}", e)
                }

                if (!success && attempt < maxAttempts) {
                    logger.info("Waiting ${retryDelaySeconds}s before retry...")
                    delay(retryDelaySeconds * 1000)
                }
            }

            if (!success) {
                logger.error("Failed to validate/pull Ollama model after $maxAttempts attempts")
                throw RuntimeException("Ollama model $modelName is not available and could not be pulled")
            }
        }
    }

    suspend fun checkModelExists(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("ollama", "list")
                .redirectErrorStream(true)
                .start()

            val output = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.readText()
            }

            process.waitFor()
            output.contains(modelName)
        } catch (e: Exception) {
            logger.error("Error checking if model exists: ${e.message}", e)
            false
        }
    }

    suspend fun pullModel(): Boolean = withContext(Dispatchers.IO) {
        try {
            withTimeout(timeoutSeconds * 1000) {
                logger.info("Pulling Ollama model $modelName (timeout: ${timeoutSeconds}s)...")

                val process = ProcessBuilder("ollama", "pull", modelName)
                    .redirectErrorStream(true)
                    .start()

                val output = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    val lines = mutableListOf<String>()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let {
                            lines.add(it)
                            if (it.contains("success") || it.contains("pulling")) {
                                logger.info("Pull progress: $it")
                            }
                        }
                    }
                    lines.joinToString("\n")
                }

                val exitCode = process.waitFor()
                if (exitCode == 0) {
                    logger.info("Model pull completed successfully")
                    true
                } else {
                    logger.error("Model pull failed with exit code $exitCode: $output")
                    false
                }
            }
        } catch (e: TimeoutCancellationException) {
            logger.error("Timeout while pulling model $modelName after ${timeoutSeconds}s")
            false
        } catch (e: Exception) {
            logger.error("Error pulling model: ${e.message}", e)
            false
        }
    }
}

