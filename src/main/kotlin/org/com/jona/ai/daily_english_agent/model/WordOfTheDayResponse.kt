package org.com.jona.ai.daily_english_agent.model

data class WordOfTheDayResponse(
    val formattedContent: String,
    val errorMessage: String? = null
)

