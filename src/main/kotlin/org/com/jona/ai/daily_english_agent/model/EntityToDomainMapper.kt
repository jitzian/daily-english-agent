package org.com.jona.ai.daily_english_agent.model

import org.com.jona.ai.daily_english_agent.repository.entity.WordOfTheDayEntity

class EntityToDomainMapper : (WordOfTheDayEntity) -> WordOfTheDayDomain {
    override fun invoke(entity: WordOfTheDayEntity): WordOfTheDayDomain = with(entity) {
        WordOfTheDayDomain(
            word = word,
            partOfSpeech = partOfSpeech,
            synonyms = synonyms.split("|").map { it.trim() },
            exampleEnglish = exampleEnglish,
            exampleSpanish = exampleSpanish
        )
    }
}

