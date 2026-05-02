package org.com.jona.ai.daily_english_agent.config

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

/**
 * Configures Spring's task scheduler to use a thread pool instead of the
 * default single-thread executor.
 *
 * WHY THIS MATTERS:
 * The default Spring @Scheduled executor uses a SINGLE thread ("scheduling-1").
 * If a scheduled task blocks that thread (e.g., a stuck network call to Ollama while
 * the Mac is asleep), ALL future cron triggers are dropped silently — they fire but
 * find no free thread and are discarded.
 *
 * With poolSize = 5, even if 4 threads are stuck waiting for a slow/zombie Ollama
 * connection, the 5th thread will still fire the next day's 7 AM cron.
 *
 * This alone prevents the "scheduler stops for days" bug observed in production.
 *
 * NOTE: fetchAndStoreNewWord() should also be non-blocking (see VocabularyService).
 * This config is the safety net in case any scheduling method accidentally blocks.
 */
@Configuration
class SchedulingConfig {

    private val logger = LoggerFactory.getLogger(SchedulingConfig::class.java)

    @Bean
    fun taskScheduler(): TaskScheduler {
        val scheduler = ThreadPoolTaskScheduler()

        // Pool of 5 threads so stuck tasks never starve future cron triggers.
        scheduler.poolSize = 5

        // Meaningful thread names make thread-dumps and logs easy to read.
        scheduler.setThreadNamePrefix("vocab-scheduler-")

        // Log every unhandled exception thrown by a scheduled task instead of
        // swallowing it silently (Spring's default behaviour).
        scheduler.setErrorHandler { throwable ->
            logger.error(
                "Uncaught exception in scheduled task — task will reschedule normally at next cron trigger. " +
                    "Error: ${throwable.message}",
                throwable
            )
        }

        scheduler.initialize()
        logger.info("TaskScheduler configured with pool size 5 (thread prefix: vocab-scheduler-)")
        return scheduler
    }
}

