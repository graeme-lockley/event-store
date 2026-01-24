package com.eventstore.integration

import com.eventstore.Config
import com.eventstore.configureApplication
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

/**
 * Helper class for starting and stopping an event-store instance for integration tests.
 *
 * Each instance uses its own data and config directories to ensure test isolation.
 */
class EventStoreTestHelper(
    private val dataDir: Path,
    private val configDir: Path,
    private val port: Int = findAvailablePort(),
) {
    private var server: ApplicationEngine? = null

    companion object {
        private val portCounter = AtomicInteger(9000)

        /**
         * Finds an available port starting from 9000, incrementing for each call.
         */
        private fun findAvailablePort(): Int {
            return portCounter.getAndIncrement()
        }
    }

    /**
     * Starts the event-store instance with the configured data and config directories.
     *
     * @throws IllegalStateException if the server is already running
     */
    fun start() {
        if (server != null) {
            throw IllegalStateException("Event-store instance is already running")
        }

        // Ensure directories exist
        Files.createDirectories(dataDir)
        Files.createDirectories(configDir)

        val config =
            Config(
                port = port,
                dataDir = dataDir.toString(),
                configDir = configDir.toString(),
                maxBodyBytes = 1048576L,
                rateLimitPerMinute = 600,
                authEnabled = false,
                silent = true,
            )

        server =
            embeddedServer(Netty, port = config.port) {
                configureApplication(config)
            }.start(wait = false)
    }

    /**
     * Stops the event-store instance gracefully.
     *
     * @throws IllegalStateException if the server is not running
     */
    fun stop() {
        val serverInstance = server ?: throw IllegalStateException("Event-store instance is not running")

        runBlocking {
            serverInstance.stop(1000, 2000)
        }

        server = null
    }

    /**
     * Gets the base URL of the running event-store instance.
     */
    fun getBaseUrl(): String {
        if (server == null) {
            throw IllegalStateException("Event-store instance is not running")
        }
        return "http://localhost:$port"
    }

    /**
     * Gets the port the event-store instance is running on.
     */
    fun getPort(): Int = port

    /**
     * Checks if the event-store instance is currently running.
     */
    fun isRunning(): Boolean = server != null

    /**
     * Gets the email for the bootstrapped admin user.
     * Defaults to "admin@system" unless SYSTEM_ADMIN_EMAIL environment variable is set.
     */
    fun getAdminEmail(): String = System.getenv("SYSTEM_ADMIN_EMAIL") ?: "admin@system"

    /**
     * Gets the password for the bootstrapped admin user.
     * Defaults to "admin123" unless SYSTEM_ADMIN_PASSWORD environment variable is set.
     */
    fun getAdminPassword(): String = System.getenv("SYSTEM_ADMIN_PASSWORD") ?: "admin123"
}
