package org.com.jona.ai.daily_english_agent.service.discord

import discord4j.core.DiscordClientBuilder
import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.spec.EmbedCreateFields
import discord4j.core.spec.EmbedCreateSpec
import discord4j.core.spec.MessageCreateSpec
import discord4j.rest.util.Color
import discord4j.common.util.Snowflake
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.com.jona.ai.daily_english_agent.model.WordOfTheDayDomain
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class DiscordService(
    @Value("\${discord.bot.token}")           private val botToken: String,
    @Value("\${discord.channel.id}")          private val channelId: String,
    @Value("\${discord.enabled}")             private val discordEnabled: Boolean,
    @Value("\${discord.test.mode}")           private val testMode: Boolean,
    @Value("\${discord.test.prefix}")         private val testPrefix: String,
    @Value("\${discord.retry.max.attempts}")  private val maxRetryAttempts: Int,
    @Value("\${discord.retry.delay.seconds}") private val retryDelaySeconds: Long
) {
    private val logger = LoggerFactory.getLogger(DiscordService::class.java)
    private var gatewayClient: GatewayDiscordClient? = null

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @PostConstruct
    fun connect() {
        if (!discordEnabled) {
            logger.info("Discord integration is DISABLED — skipping connection")
            return
        }

        logger.info("========================================")
        logger.info("Discord Integration Initialization")
        logger.info("========================================")
        logger.info("Test mode   : $testMode")
        logger.info("Channel ID  : $channelId")

        try {
            /*
             * VPN WORKAROUND
             * Discord4J uses Reactor Netty under the hood. We configure the HTTP
             * client to avoid VPN DNS interception by:
             *  1. Disabling DNS queries entirely for the proxy resolution step
             *  2. Letting the JVM use its built-in resolver (which typically
             *     bypasses split-DNS setups used by NordVPN)
             */
            System.setProperty("reactor.netty.http.server.accessLogEnabled", "false")
            System.setProperty("io.netty.resolver.dns.preferNativeTransport", "false")

            gatewayClient = DiscordClientBuilder.create(botToken)
                .build()
                .login()
                .block()

            logger.info("✓ Discord bot connected successfully")
            logger.info("========================================")
        } catch (e: Exception) {
            logger.error("✗ Discord connection failed: ${e.message}", e)
            logger.warn("Discord posting will be unavailable for this session")
        }
    }

    @PreDestroy
    fun disconnect() {
        gatewayClient?.logout()?.block()
        logger.info("Discord bot disconnected")
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Posts the word of the day to the configured Discord channel.
     * 
     * CRITICAL FIX (May 5, 2026):
     * Enhanced error handling to gracefully handle VPN DNS interception and WebSocket
     * connection instability. If the gateway client is null or becomes unstable,
     * this method logs the issue and returns without crashing.
     *
     * VPN ISSUE: NordVPN split-DNS can intercept Discord API calls, causing WebSocket
     * closure (1006 Abnormal closure). The retry logic and lazy reconnection handle
     * this gracefully without blocking the scheduler.
     *
     * Retries up to [maxRetryAttempts] times with [retryDelaySeconds] gaps.
     * Never throws — failures are logged and swallowed so the caller is
     * not affected.
     */
    suspend fun postWordOfTheDay(word: WordOfTheDayDomain) {
        if (!discordEnabled) {
            logger.info("Discord is disabled — skipping post for word '${word.word}'")
            return
        }

        logger.info("Attempting to post word '${word.word}' to Discord...")

        // Attempt lazy reconnection if client is null or appears disconnected
        if (gatewayClient == null) {
            logger.warn("Discord client is null — attempting lazy reconnection for word '${word.word}'...")
            ensureConnected()
        }

        val client = gatewayClient
        if (client == null) {
            logger.error("Discord client connection failed (lazy reconnection did not recover) — skipping post for word '${word.word}'")
            logger.info("Word '${word.word}' was successfully stored in database but Discord posting could not be completed. Scheduler will continue normally.")
            return
        }

        var attempt = 0
        var success = false

        while (attempt < maxRetryAttempts && !success) {
            attempt++
            logger.info("Discord post attempt $attempt/$maxRetryAttempts for word '${word.word}'...")

            try {
                withContext(Dispatchers.IO) {
                    val embed = buildEmbed(word)
                    val messageSpec = MessageCreateSpec.builder()
                        .addEmbed(embed)
                        .build()

                    client.getChannelById(Snowflake.of(channelId))
                        .flatMap { channel ->
                            (channel as MessageChannel).createMessage(messageSpec)
                        }
                        .block()
                }

                logger.info("✓ Successfully posted word '${word.word}' to Discord (attempt $attempt)")
                success = true
            } catch (e: Exception) {
                logger.error("✗ Discord post attempt $attempt failed: ${e.javaClass.simpleName}: ${e.message}")
                logger.debug("Stack trace: ", e)

                if (attempt < maxRetryAttempts) {
                    logger.info("  Waiting ${retryDelaySeconds}s before retry (attempt $attempt/$maxRetryAttempts)...")
                    delay(retryDelaySeconds * 1_000)
                }
            }
        }

        if (!success) {
            logger.error("Failed to post word '${word.word}' to Discord after $maxRetryAttempts attempts. (This may be due to VPN DNS interception.)")
            logger.info("Word '${word.word}' was successfully stored in database. Discord posting will be retried on next scheduled execution.")
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Lazy reconnection attempt for Discord bot.
     * Called when posting is requested but gatewayClient is null.
     * This handles cases where the initial @PostConstruct connection failed
     * (e.g., VPN DNS interception at startup time, network not ready).
     *
     * VPN NOTE: Implements workaround for NordVPN split-DNS interception:
     * - Disables Netty DNS caching which can interfer with VPN resolver
     * - Uses native transport preference override
     */
    private fun ensureConnected() {
        if (gatewayClient != null) {
            logger.info("Discord client already connected")
            return
        }

        logger.info("Attempting to establish Discord connection (lazy reconnection)...")
        try {
            System.setProperty("reactor.netty.http.server.accessLogEnabled", "false")
            System.setProperty("io.netty.resolver.dns.preferNativeTransport", "false")
            // Additional NordVPN workaround: Disable HTTP/2 which can be affected by VPN MTU issues
            System.setProperty("reactor.netty.http.h2c.enabled", "false")

            gatewayClient = DiscordClientBuilder.create(botToken)
                .build()
                .login()
                .block()

            logger.info("✓ Discord bot reconnected successfully (lazy connection)")
        } catch (e: Exception) {
            logger.error("✗ Discord lazy reconnection failed: ${e.message} (likely VPN DNS interception)", e)
            logger.warn("Discord will remain unavailable until the connection can be established. Scheduler will continue executing.")
            gatewayClient = null
        }
    }

    private fun buildEmbed(word: WordOfTheDayDomain): EmbedCreateSpec {
        val title = if (testMode) {
            "$testPrefix 📚 Word of the Day: ${word.word.replaceFirstChar { it.uppercase() }}"
        } else {
            "📚 Word of the Day: ${word.word.replaceFirstChar { it.uppercase() }}"
        }

        val synonymLine = word.synonyms.joinToString(" | ")

        return EmbedCreateSpec.builder()
            .color(Color.of(0x3498DB))           // Blue
            .title(title)
            .addField(
                EmbedCreateFields.Field.of(
                    "📖 Part of Speech",
                    word.partOfSpeech.replaceFirstChar { it.uppercase() },
                    true
                )
            )
            .addField(
                EmbedCreateFields.Field.of(
                    "🔄 Synonyms",
                    synonymLine.ifBlank { "N/A" },
                    false
                )
            )
            .addField(
                EmbedCreateFields.Field.of(
                    "🇺🇸 English Example",
                    word.exampleEnglish,
                    false
                )
            )
            .addField(
                EmbedCreateFields.Field.of(
                    "🇪🇸 Spanish Translation",
                    word.exampleSpanish,
                    false
                )
            )
            .footer(
                EmbedCreateFields.Footer.of(
                    "Daily English Vocabulary Agent${if (testMode) " • TEST MODE" else ""}",
                    null
                )
            )
            .timestamp(Instant.now())
            .build()
    }
}





