import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("plugin.serialization")
    kotlin("jvm") version "1.5.31"
    id("com.github.johnrengelman.shadow")
}

group = "me.scharxidev.odin"
version = "1.0-SNAPSHOT"

repositories {
    google()
    mavenCentral()

    maven {
        name = "Sonatype Snapshots"
        url = uri("https://oss.sonatype.org/content/repositories/snapshots")
    }

    maven {
        name = "Kotlin Discord"
        url = uri("https://maven.kotlindiscord.com/repository/maven-public/")
    }
}

dependencies {
    implementation(libs.kord.extensions)
    implementation(libs.kord.phishing)

    implementation(libs.kotlin.stdlib)
    implementation(libs.kx.ser)

    // Logging dependencies
    implementation(libs.groovy)
    implementation(libs.jansi)
    implementation(libs.logback)
    implementation(libs.logging)

    // TOML
    implementation(libs.ktoml)
    implementation(libs.ktomlf)

    // Database
    implementation("mysql:mysql-connector-java:8.0.28")
    implementation(libs.hikari)

    // Exposed
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.time)

    // Arrow
    implementation("io.arrow-kt:arrow-core:1.0.2-SNAPSHOT")
    implementation("io.arrow-kt:arrow-fx-coroutines:1.0.2-SNAPSHOT")
    implementation("io.arrow-kt:arrow-fx-stm:1.0.2-SNAPSHOT")
}

application {
    @Suppress("DEPRECATION")
    mainClassName = "me.scharxidev.odin.OdinBotKt"
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "16"

    kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "me.scharxidev.odin.OdinBotKt"
        )
    }
}

java {
    // Current LTS version of Java
    sourceCompatibility = JavaVersion.VERSION_16
    targetCompatibility = JavaVersion.VERSION_16
}