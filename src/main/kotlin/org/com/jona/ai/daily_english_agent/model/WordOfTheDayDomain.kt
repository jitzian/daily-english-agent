package org.com.jona.ai.daily_english_agent.model

data class WordOfTheDayDomain(
    val word: String,
    val partOfSpeech: String,
    val synonyms: List<String>,
    val exampleEnglish: String,
    val exampleSpanish: String
)

