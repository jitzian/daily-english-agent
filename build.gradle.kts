plugins {
	kotlin("jvm") version "2.3.10"
	kotlin("plugin.spring") version "2.3.10"
	kotlin("plugin.jpa") version "2.3.10"
	kotlin("plugin.serialization") version "2.3.10"
	war
	id("org.springframework.boot") version "4.1.0-SNAPSHOT"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "org.com.jona.ai"
version = "0.0.1-SNAPSHOT"
description = "Daily learn an english word"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
	maven { url = uri("https://repo.spring.io/snapshot") }
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	providedRuntime("org.springframework.boot:spring-boot-starter-tomcat-runtime")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")

	// AI & Ollama
	implementation("ai.koog:koog-agents:0.6.3")

	// Database
	implementation("org.postgresql:postgresql")

	// Ktor - use version 3.2.x to match Spring Boot dependencies
	val ktorVersion = "3.2.3"
	implementation("io.ktor:ktor-server-netty:$ktorVersion")
	implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
	implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

	// Ktor Client for Ollama API
	implementation("io.ktor:ktor-client-core:$ktorVersion")
	implementation("io.ktor:ktor-client-cio:$ktorVersion")
	implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

	// Coroutines
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.7.3")

	// Serialization
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
