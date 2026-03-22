import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.4.3"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.spring") version "2.1.10"
    kotlin("plugin.jpa") version "2.1.10"
}

group = "com.poc.temporal"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Temporal SDK
    implementation("io.temporal:temporal-sdk:1.30.1")
    implementation("io.temporal:temporal-spring-boot-starter:1.30.1")

    // PostgreSQL driver
    runtimeOnly("org.postgresql:postgresql")

    // Starts a local Docker Compose stack automatically when running the app (not during tests)
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")

    // H2 kept for test-only use (no real DB needed: all tests mock repositories)
    testRuntimeOnly("com.h2database:h2")

    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.temporal:temporal-testing:1.30.1")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
