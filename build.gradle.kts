// Root build file for shared configuration across all subprojects
// Individual projects can override these settings in their own build.gradle.kts files
//
// Quality Tools Configuration:
// - JaCoCo: Code coverage measurement (version 0.8.11+)
//   - Run coverage: ./gradlew test jacocoTestReport
//   - Reports: build/reports/jacoco/test/html/index.html
// - ktlint: Kotlin code style checking (version 1.1.1+)
//   - Check style: ./gradlew ktlintCheck
//   - Auto-format: ./gradlew ktlintFormat

plugins {
    kotlin("jvm") version "1.9.22" apply false
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0" apply false
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
    apply(plugin = "jacoco")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    
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
        // Enable JaCoCo coverage for tests
        finalizedBy(tasks.named("jacocoTestReport"))
    }
    
    // JaCoCo configuration for code coverage (REQ-2)
    // - Version 0.8.11+ required per REQ-2.2
    // - Generates XML and HTML reports per REQ-2.4, REQ-2.5
    configure<JacocoPluginExtension> {
        toolVersion = "0.8.11"
    }
    
    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn(tasks.named("test"))
        
        reports {
            // XML format for programmatic parsing (REQ-2.4)
            xml.required.set(true)
            xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml"))
            
            // HTML format for developer viewing (REQ-2.5)
            html.required.set(true)
            html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/test/html"))
            
            // CSV not required
            csv.required.set(false)
        }
        
        // Exclude build directories and generated code (REQ-2.9)
        classDirectories.setFrom(
            files(classDirectories.files.map {
                fileTree(it) {
                    exclude(
                        "**/build/**",
                        "**/generated/**"
                    )
                }
            })
        )
    }
    
    // ktlint configuration for code style checking (REQ-3)
    // - Version 1.1.1+ required per REQ-3.2
    // - Verbose output, console output enabled, failures not ignored (REQ-3.7)
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.1.1")
        verbose.set(true)
        outputToConsole.set(true)
        ignoreFailures.set(false)
        enableExperimentalRules.set(false)
        
        // Exclude build directories and generated code (REQ-3.5)
        filter {
            exclude("**/build/**")
            exclude("**/generated/**")
        }
    }
}
