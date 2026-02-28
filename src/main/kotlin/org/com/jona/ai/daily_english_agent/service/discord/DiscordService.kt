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
     * Retries up to [maxRetryAttempts] times with [retryDelaySeconds] gaps.
     * Never throws — failures are logged and swallowed so the caller is
     * not affected.
     */
    suspend fun postWordOfTheDay(word: WordOfTheDayDomain) {
        if (!discordEnabled) {
            logger.info("Discord is disabled — skipping post for word '${word.word}'")
            return
        }

        val client = gatewayClient
        if (client == null) {
            logger.warn("Discord client is not connected — skipping post for word '${word.word}'")
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
                logger.error("✗ Discord post attempt $attempt failed: ${e.message}")

                if (attempt < maxRetryAttempts) {
                    logger.info("  Waiting ${retryDelaySeconds}s before retry...")
                    delay(retryDelaySeconds * 1_000)
                }
            }
        }

        if (!success) {
            logger.error("Failed to post word '${word.word}' to Discord after $maxRetryAttempts attempts")
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

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





