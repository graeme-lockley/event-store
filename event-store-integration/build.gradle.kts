plugins {
    application
}

dependencies {
    // Dependency on the event-store project
    implementation(project(":event-store"))

    // Ktor Client for making HTTP requests to the event-store
    implementation("io.ktor:ktor-client-core:2.3.8")
    implementation("io.ktor:ktor-server-netty:2.3.8")
    implementation("io.ktor:ktor-client-cio:2.3.8")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.8")

    // Testing
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:1.9.22")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("io.ktor:ktor-server-test-host:2.3.8")
    testImplementation("io.ktor:ktor-client-content-negotiation:2.3.8")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}

application {
    mainClass.set("com.eventstore.integration.MainKt")
}
