package org.com.jona.ai.daily_english_agent.repository

import org.com.jona.ai.daily_english_agent.repository.entity.WordOfTheDayEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Repository
interface WordHistoryRepository : JpaRepository<WordOfTheDayEntity, UUID> {

    fun existsByWord(word: String): Boolean

    fun findFirstByIsActiveTrueOrderByFetchedAtDesc(): WordOfTheDayEntity?

    fun findAllByOrderByFetchedAtDesc(): List<WordOfTheDayEntity>

    @Modifying
    @Transactional
    @Query("UPDATE WordOfTheDayEntity w SET w.isActive = false")
    fun deactivateAllWords()

    @Query("SELECT w.word FROM WordOfTheDayEntity w")
    fun findAllWords(): List<String>
}

