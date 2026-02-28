package org.com.jona.ai.daily_english_agent.model

class WordOfTheDayMapper : (WordOfTheDayData) -> WordOfTheDayDomain {
    override fun invoke(data: WordOfTheDayData): WordOfTheDayDomain = with(data) {
        WordOfTheDayDomain(
            word = word,
            partOfSpeech = partOfSpeech,
            synonyms = synonyms.split("|").map { it.trim() },
            exampleEnglish = exampleEnglish,
            exampleSpanish = exampleSpanish
        )
    }
}

