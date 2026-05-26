plugins {
	kotlin("jvm") version "2.3.10"
	kotlin("plugin.spring") version "2.3.10"
	kotlin("plugin.jpa") version "2.3.10"
	kotlin("plugin.serialization") version "2.3.10"
	war
	id("org.springframework.boot") version "4.0.6"
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

	// Spring Core & Context (explicit to ensure full transitive resolution)
	implementation("org.springframework:spring-core")
	implementation("org.springframework:spring-context")

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

	// Coroutines (upgraded to latest stable for better stability)
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.10.2")

	// Serialization
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

	// Discord Integration
	// reactor-netty-http is intentionally NOT declared here; Spring Boot BOM manages
	// its version. The configurations block below forces Discord4J's transitive
	// reactor-netty dependencies to align with the BOM version, preventing the
	// NoSuchMethodError caused by mixing 1.1.x and 1.3.x jars on the classpath.
	implementation("com.discord4j:discord4j-core:3.2.7")

}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
	// Spring annotations – ensures Kotlin generates non-final proxiable classes
	annotation("org.springframework.stereotype.Component")
	annotation("org.springframework.stereotype.Service")
	annotation("org.springframework.stereotype.Repository")
	annotation("org.springframework.transaction.annotation.Transactional")
}

// Force all reactor-netty artifacts to the version managed by the Spring Boot BOM.
// Discord4J 3.2.x pins reactor-netty to 1.0/1.1, but Spring Boot 4.x needs 1.3.x.
// Without this, reactor-netty-http:1.1.x and reactor-netty-core:1.3.x end up on the
// classpath together, causing NoSuchMethodError at runtime.
configurations.all {
	resolutionStrategy.eachDependency {
		if (requested.group == "io.projectreactor.netty") {
			useVersion("1.3.3")
			because("Align with Spring Boot 4.x BOM — prevents binary incompatibility with Discord4J")
		}
	}
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
