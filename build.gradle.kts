// Root build file for shared configuration across all subprojects
// Individual projects can override these settings in their own build.gradle.kts files

plugins {
    kotlin("jvm") version "1.9.22" apply false
}

allprojects {
    repositories {
        mavenCentral()
    }
    
    group = "com.eventstore"
    version = "1.0.0"
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "java")
    
    // Shared Kotlin compiler settings
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "17"
            freeCompilerArgs = listOf("-Xjsr305=strict")
        }
    }
    
    // Shared Java compiler settings
    tasks.withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    
    // Shared test configuration
    tasks.named<Test>("test") {
        useJUnitPlatform()
    }
}
