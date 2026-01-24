package com.eventstore.integration

/**
 * Main entry point for the event-store-integration project.
 *
 * This project is primarily focused on integration tests.
 * This main class exists to satisfy tooling requirements for a main source directory.
 */
object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        println("Event Store Integration Test Project")
        println("Run tests with: ./gradlew :event-store-integration:test")
    }
}
