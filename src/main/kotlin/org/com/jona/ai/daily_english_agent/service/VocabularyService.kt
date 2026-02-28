package org.com.jona.ai.daily_english_agent.service

import jakarta.annotation.PostConstruct
import kotlinx.coroutines.*
import org.com.jona.ai.daily_english_agent.model.EntityToDomainMapper
import org.com.jona.ai.daily_english_agent.model.WordOfTheDayMapper
import org.com.jona.ai.daily_english_agent.model.WordOfTheDayResponse
import org.com.jona.ai.daily_english_agent.repository.WordHistoryRepository
import org.com.jona.ai.daily_english_agent.repository.entity.WordOfTheDayEntity
import org.com.jona.ai.daily_english_agent.service.agent.VocabularyAgentService
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
    @Value("\${vocabulary.retry.max.attempts}") private val maxAttempts: Int,
    @Value("\${vocabulary.retry.delay.seconds}") private val retryDelaySeconds: Long
) {
    private val logger = LoggerFactory.getLogger(VocabularyService::class.java)
    private val wordMapper = WordOfTheDayMapper()
    private val entityMapper = EntityToDomainMapper()

    @PostConstruct
    fun initializeFirstWord() {
        logger.info("Checking if database needs initial word...")
        if (wordHistoryRepository.count() == 0L) {
            logger.info("Database empty, fetching initial word...")
            runBlocking {
                fetchWithRetry()
            }
        } else {
            logger.info("Database has ${wordHistoryRepository.count()} words, skipping initial fetch")
        }
    }

    @Scheduled(cron = "\${vocabulary.fetch.cron}")
    fun fetchAndStoreNewWord() {
        logger.info("Starting scheduled word fetch...")
        runBlocking {
            withContext(Dispatchers.IO) {
                try {
                    val usedWords = wordHistoryRepository.findAllWords()
                    logger.info("Found ${usedWords.size} words already in database")

                    var attempts = 0
                    var wordData = vocabularyAgentService.generateWord(usedWords)

                    // Application-level duplicate validation with retry
                    while (wordHistoryRepository.existsByWord(wordData.word) && attempts < 3) {
                        attempts++
                        logger.warn("Generated word '${wordData.word}' already exists. Retry attempt $attempts/3")
                        wordData = vocabularyAgentService.generateWord(usedWords)
                    }

                    if (wordHistoryRepository.existsByWord(wordData.word)) {
                        logger.error("Failed to generate unique word after 3 attempts")
                        throw RuntimeException("Could not generate unique word")
                    }

                    saveNewWord(wordData.word, wordData.partOfSpeech, wordData.synonyms,
                               wordData.exampleEnglish, wordData.exampleSpanish, null)

                    logger.info("Successfully fetched and stored new word: ${wordData.word}")
                } catch (e: Exception) {
                    logger.error("Error fetching new word: ${e.message}", e)
                    saveErrorWord(e.message ?: "Unknown error")
                }
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
                withContext(Dispatchers.IO) {
                    val usedWords = wordHistoryRepository.findAllWords()
                    val wordData = vocabularyAgentService.generateWord(usedWords)

                    saveNewWord(wordData.word, wordData.partOfSpeech, wordData.synonyms,
                               wordData.exampleEnglish, wordData.exampleSpanish, null)

                    logger.info("Successfully fetched initial word: ${wordData.word}")
                    success = true
                }
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
        // Deactivate all historical words
        wordHistoryRepository.deactivateAllWords()

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

        wordHistoryRepository.save(entity)
        logger.info("Saved word '$word' to database")
    }

    @Transactional
    fun saveErrorWord(errorMessage: String) {
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

        wordHistoryRepository.deactivateAllWords()
        wordHistoryRepository.save(entity)
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

