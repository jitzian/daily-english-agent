package org.com.jona.ai.daily_english_agent.repository.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(
    name = "words_history",
    indexes = [
        Index(name = "idx_word", columnList = "word"),
        Index(name = "idx_is_active", columnList = "isActive"),
        Index(name = "idx_fetched_at", columnList = "fetchedAt")
    ]
)
data class WordOfTheDayEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    val id: UUID? = null,

    @Column(unique = true, nullable = false)
    val word: String,

    @Column(nullable = false)
    val partOfSpeech: String,

    @Column(nullable = false)
    val synonyms: String,

    @Column(length = 500, nullable = false)
    val exampleEnglish: String,

    @Column(length = 500, nullable = false)
    val exampleSpanish: String,

    @Column(nullable = false)
    val fetchedAt: LocalDateTime,

    @Column(nullable = false)
    val isActive: Boolean = false,

    @Column(length = 1000)
    val errorMessage: String? = null
)

