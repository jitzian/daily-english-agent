package org.com.jona.ai.daily_english_agent.api.ktor

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.com.jona.ai.daily_english_agent.service.VocabularyService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class KtorServer(
    @Value("\${ktor.port}") private val port: Int,
    private val vocabularyService: VocabularyService
) {
    private val logger = LoggerFactory.getLogger(KtorServer::class.java)
    private lateinit var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>

    @PostConstruct
    fun start() {
        GlobalScope.launch(Dispatchers.IO) {
            logger.info("Starting Ktor server on port $port...")

            server = embeddedServer(Netty, port = port) {
                install(ContentNegotiation) {
                    json()
                }

                routing {
                    get("/word-of-the-day") {
                        try {
                            val response = vocabularyService.getCurrentWord()

                            val fullResponse = if (response.errorMessage != null) {
                                "${response.formattedContent}\n\n[Error: ${response.errorMessage}]"
                            } else {
                                response.formattedContent
                            }

                            call.respondText(fullResponse)
                        } catch (e: Exception) {
                            logger.error("Error handling /word-of-the-day request: ${e.message}", e)
                            call.respondText("Error: ${e.message ?: "Unknown error occurred"}")
                        }
                    }

                    get("/health") {
                        call.respondText("Ktor server is running")
                    }
                }
            }

            server.start(wait = false)
            logger.info("Ktor server started successfully on port $port")
        }
    }

    @PreDestroy
    fun stop() {
        logger.info("Stopping Ktor server...")
        if (::server.isInitialized) {
            server.stop(1000, 2000)
            logger.info("Ktor server stopped")
        }
    }
}


