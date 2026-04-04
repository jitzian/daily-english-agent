package org.com.jona.ai.daily_english_agent.controller

import org.com.jona.ai.daily_english_agent.service.VocabularyService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller for manual vocabulary operations.
 * Used for testing and manual triggers.
 */
@RestController
@RequestMapping("/api/vocabulary")
class VocabularyController(
    private val vocabularyService: VocabularyService
) {
    
    /**
     * Manually trigger vocabulary generation.
     * This endpoint calls the same logic as the scheduled task.
     * 
     * POST http://localhost:8090/api/vocabulary/generate
     */
    @PostMapping("/generate")
    fun generateWord(): Map<String, String> {
        return try {
            vocabularyService.fetchAndStoreNewWord()
            mapOf(
                "status" to "success",
                "message" to "New word generated and stored successfully"
            )
        } catch (e: Exception) {
            mapOf<String, String>(
                "status" to "error",
                "message" to (e.message ?: "Unknown error")
            )
        }
    }
}
