package org.com.jona.ai.daily_english_agent

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.transaction.annotation.EnableTransactionManagement

@SpringBootApplication
@EnableScheduling
@EnableJpaRepositories(basePackages = ["org.com.jona.ai.daily_english_agent.repository"])
@EnableTransactionManagement
@ComponentScan(basePackages = ["org.com.jona.ai.daily_english_agent"])
class DailyEnglishAgentApplication {

	companion object {
		private val logger = LoggerFactory.getLogger(DailyEnglishAgentApplication::class.java)
	}

	@PostConstruct
	fun logStartup() {
		logger.info("=".repeat(80))
		logger.info("Daily English Vocabulary Agent - Starting Up")
		logger.info("=".repeat(80))
		logger.info("Application: daily-english-agent")
		logger.info("Spring Boot Port: 8090")
		logger.info("Ktor API Port: 8091")
		logger.info("PostgreSQL Database: englishWordsDB (container: vocabulary_db)")
		logger.info("")
		logger.info("⚠️  IMPORTANT: Server timezone must be EST for 7 AM daily execution")
		logger.info("")
		logger.info("API Endpoints:")
		logger.info("  - GET http://localhost:8091/word-of-the-day")
		logger.info("  - GET http://localhost:8091/health")
		logger.info("")
		logger.info("Health Check:")
		logger.info("  - GET http://localhost:8090/actuator/health")
		logger.info("=".repeat(80))
	}
}

fun main(args: Array<String>) {
	runApplication<DailyEnglishAgentApplication>(*args)
}
