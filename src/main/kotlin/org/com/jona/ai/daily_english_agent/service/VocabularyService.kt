package org.com.jona.ai.daily_english_agent.service

import jakarta.annotation.PostConstruct
import kotlinx.coroutines.*
import org.com.jona.ai.daily_english_agent.model.EntityToDomainMapper
import java.util.concurrent.atomic.AtomicBoolean
import org.com.jona.ai.daily_english_agent.model.WordOfTheDayMapper
import org.com.jona.ai.daily_english_agent.model.WordOfTheDayResponse
import org.com.jona.ai.daily_english_agent.repository.WordHistoryRepository
import org.com.jona.ai.daily_english_agent.repository.entity.WordOfTheDayEntity
import org.com.jona.ai.daily_english_agent.service.agent.VocabularyAgentService
import org.com.jona.ai.daily_english_agent.service.discord.DiscordService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class VocabularyService(
    private val wordHistoryRepository: WordHistoryRepository,
    private val vocabularyAgentService: VocabularyAgentService,
    private val discordService: DiscordService,
    @Value("\${vocabulary.retry.max.attempts}") private val maxAttempts: Int,
    @Value("\${vocabulary.retry.delay.seconds}") private val retryDelaySeconds: Long
) {
    private val logger = LoggerFactory.getLogger(VocabularyService::class.java)
    private val wordMapper = WordOfTheDayMapper()
    private val entityMapper = EntityToDomainMapper()

    /**
     * Dedicated coroutine scope for background fetch operations.
     *
     * WHY NOT runBlocking:
     * The @Scheduled method must NEVER block its caller thread (Spring's vocab-scheduler-N).
     * Using runBlocking would occupy the scheduler thread for the full Ollama round-trip
     * (up to 180 s) — or indefinitely if the Mac goes to sleep mid-request with an active
     * TCP connection, causing the coroutine/socket timeouts to be suspended alongside the JVM.
     * This resulted in the "scheduler stops for days" bug observed Apr 29 – May 1.
     *
     * Using CoroutineScope.launch means the @Scheduled method returns immediately and the
     * scheduler thread is free to fire the next 7 AM cron regardless of what the previous
     * task is doing.
     *
     * SupervisorJob: exceptions in one fetch do not cancel the scope or future coroutines.
     */
    private val fetchScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Prevents overlapping fetches if a previous one is still in progress.
    private val fetchInProgress = AtomicBoolean(false)

    @PostConstruct
    fun initializeFirstWord() {
        logger.info("========================================")
        logger.info("Database Connection Verification")
        logger.info("========================================")

        try {
            val count = wordHistoryRepository.count()
            logger.info("✓ Database connection successful")
            logger.info("Current word count in database: $count")

            if (count == 0L) {
                logger.info("Database empty, fetching initial word...")
                runBlocking {
                    fetchWithRetry()
                }
            } else {
                logger.info("Database has $count words, skipping initial fetch")
            }
        } catch (e: Exception) {
            logger.error("✗ Database connection failed: ${e.message}", e)
            throw RuntimeException("Failed to connect to database", e)
        }

        logger.info("========================================")
    }

    /**
     * Scheduled word fetch — fires daily at the configured cron time (default 7 AM EST).
     *
     * CRITICAL: this method returns IMMEDIATELY by launching work on [fetchScope].
     * It must NEVER block the caller (Spring's vocab-scheduler-N thread).
     *
     * Background: when Mac sleeps mid-Ollama-request the JVM is also suspended,
     * so the Ktor/coroutine timeouts are frozen too.  If the scheduler thread were
     * blocked (e.g., via runBlocking) it would stay blocked for days after the Mac
     * wakes, silently dropping every subsequent 7 AM trigger.  The separate scope
     * eliminates that risk completely.
     */
    @Scheduled(cron = "\${vocabulary.fetch.cron}")
    fun fetchAndStoreNewWord() {
        logger.info("Scheduled word fetch triggered — launching on background scope...")

        if (!fetchInProgress.compareAndSet(false, true)) {
            logger.warn("A previous word fetch is still in progress — skipping this trigger to avoid overlap.")
            return
        }

        fetchScope.launch {
            try {
                logger.info("Background word fetch started...")

                val usedWords = withContext(Dispatchers.IO) { wordHistoryRepository.findAllWords() }
                logger.info("Found ${usedWords.size} words already in database")

                var attempts = 0
                var generatedWord = vocabularyAgentService.generateWord(usedWords)

                // Application-level duplicate validation with retry
                while (wordHistoryRepository.existsByWord(generatedWord.word) && attempts < 3) {
                    attempts++
                    logger.warn("Generated word '${generatedWord.word}' already exists. Retry attempt $attempts/3")
                    generatedWord = vocabularyAgentService.generateWord(usedWords)
                }

                if (wordHistoryRepository.existsByWord(generatedWord.word)) {
                    logger.error("Failed to generate unique word after 3 attempts")
                    throw RuntimeException("Could not generate unique word")
                }

                // Save and post — run on Dispatchers.IO thread; Spring Data's own
                // @Transactional on SimpleJpaRepository.save() wraps each persistence call.
                withContext(Dispatchers.IO) {
                    saveNewWord(
                        generatedWord.word, generatedWord.partOfSpeech, generatedWord.synonyms,
                        generatedWord.exampleEnglish, generatedWord.exampleSpanish, null
                    )
                }

                logger.info("Successfully fetched and stored new word: ${generatedWord.word}")

                val domain = wordMapper(generatedWord)
                discordService.postWordOfTheDay(domain)

            } catch (e: Exception) {
                logger.error("Error fetching new word: ${e.message}", e)
                withContext(Dispatchers.IO) { saveErrorWord(e.message ?: "Unknown error") }
            } finally {
                fetchInProgress.set(false)
                logger.info("Background word fetch complete — scheduler thread was never blocked.")
            }
        }
    }

    private suspend fun fetchWithRetry() {
        var attempt = 0
        var success = false

        while (attempt < maxAttempts && !success) {
            attempt++
            logger.info("Fetch attempt $attempt/$maxAttempts...")

            try {
                // Fetch word data in coroutine context
                val wordData = withContext(Dispatchers.IO) {
                    val usedWords = wordHistoryRepository.findAllWords()
                    vocabularyAgentService.generateWord(usedWords)
                }

                // Save word OUTSIDE coroutine context where @Transactional works
                saveNewWord(wordData.word, wordData.partOfSpeech, wordData.synonyms,
                           wordData.exampleEnglish, wordData.exampleSpanish, null)

                logger.info("Successfully fetched initial word: ${wordData.word}")

                // Post to Discord AFTER the transaction is committed
                val domain = wordMapper(wordData)
                discordService.postWordOfTheDay(domain)

                success = true
            } catch (e: Exception) {
                logger.error("Attempt $attempt failed: ${e.message}", e)

                if (attempt < maxAttempts) {
                    logger.info("Waiting ${retryDelaySeconds}s before retry...")
                    delay(retryDelaySeconds * 1000)
                }
            }
        }

        if (!success) {
            logger.error("Failed to fetch initial word after $maxAttempts attempts")
            saveErrorWord("Failed to fetch initial word after $maxAttempts attempts")
        }
    }

    @Transactional
    fun saveNewWord(word: String, partOfSpeech: String, synonyms: String,
                    exampleEnglish: String, exampleSpanish: String, errorMessage: String?) {
        logger.info("→ Starting transaction to save word: '$word'")

        // Deactivate all historical words
        val deactivatedCount = wordHistoryRepository.count()
        wordHistoryRepository.deactivateAllWords()
        logger.info("  Deactivated $deactivatedCount historical words")

        // Save new word as active
        val entity = WordOfTheDayEntity(
            word = word,
            partOfSpeech = partOfSpeech,
            synonyms = synonyms,
            exampleEnglish = exampleEnglish,
            exampleSpanish = exampleSpanish,
            fetchedAt = LocalDateTime.now(),
            isActive = true,
            errorMessage = errorMessage
        )

        val savedEntity = wordHistoryRepository.save(entity)
        logger.info("  Saved entity with ID: ${savedEntity.id}")
        logger.info("✓ Transaction committed successfully for word: '$word'")

        // Verify the save
        val verifyCount = wordHistoryRepository.count()
        logger.info("  Database now contains $verifyCount total words")
    }

    @Transactional
    fun saveErrorWord(errorMessage: String) {
        logger.info("→ Starting transaction to save error record")

        // Remove any existing ERROR placeholder before inserting a new one
        // to avoid hitting the unique constraint on the 'word' column
        if (wordHistoryRepository.existsByWord("ERROR")) {
            wordHistoryRepository.deleteByWord("ERROR")
            logger.info("  Removed previous ERROR placeholder")
        }

        wordHistoryRepository.deactivateAllWords()

        val entity = WordOfTheDayEntity(
            word = "ERROR",
            partOfSpeech = "N/A",
            synonyms = "N/A",
            exampleEnglish = "Error occurred",
            exampleSpanish = "Ocurrió un error",
            fetchedAt = LocalDateTime.now(),
            isActive = true,
            errorMessage = errorMessage
        )

        val savedEntity = wordHistoryRepository.save(entity)
        logger.info("✓ Transaction committed successfully for error record with ID: ${savedEntity.id}")
    }

    fun getCurrentWord(): WordOfTheDayResponse {
        val currentEntity = wordHistoryRepository.findFirstByIsActiveTrueOrderByFetchedAtDesc()

        if (currentEntity == null) {
            logger.warn("No active word found in database")
            return WordOfTheDayResponse(
                formattedContent = "No word available yet. Please wait for the first scheduled fetch.",
                errorMessage = "Database is empty"
            )
        }

        // If the current word is an error placeholder, get the last valid word
        if (currentEntity.word == "ERROR" && currentEntity.errorMessage != null) {
            val lastValidWord = wordHistoryRepository.findAllByOrderByFetchedAtDesc()
                .firstOrNull { it.word != "ERROR" }

            if (lastValidWord != null) {
                val domain = entityMapper(lastValidWord)
                val content = formatWordResponse(domain.word, domain.partOfSpeech,
                                                domain.synonyms, domain.exampleEnglish,
                                                domain.exampleSpanish)
                return WordOfTheDayResponse(
                    formattedContent = content,
                    errorMessage = "Latest fetch failed: ${currentEntity.errorMessage}. Showing last cached word."
                )
            } else {
                return WordOfTheDayResponse(
                    formattedContent = "No valid word available",
                    errorMessage = currentEntity.errorMessage
                )
            }
        }

        val domain = entityMapper(currentEntity)
        val content = formatWordResponse(domain.word, domain.partOfSpeech,
                                        domain.synonyms, domain.exampleEnglish,
                                        domain.exampleSpanish)

        return WordOfTheDayResponse(
            formattedContent = content,
            errorMessage = currentEntity.errorMessage
        )
    }

    private fun formatWordResponse(word: String, partOfSpeech: String, synonyms: List<String>,
                                   exampleEnglish: String, exampleSpanish: String): String {
        return """
            Hello, the word of the day is "$word"
            
            $partOfSpeech
            ${synonyms.joinToString(" | ")}
            
            English: $exampleEnglish
            Spanish: $exampleSpanish
        """.trimIndent()
    }
}

