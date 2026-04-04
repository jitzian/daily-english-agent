package org.com.jona.ai.daily_english_agent.service.model

import kotlinx.serialization.Serializable

@Serializable
data class OllamaTagsResponse(val models: List<OllamaModel> = emptyList())