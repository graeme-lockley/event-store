package com.eventstore

data class Config(
    val port: Int,
    val dataDir: String,
    val configDir: String,
    val maxBodyBytes: Long,
    val rateLimitPerMinute: Int
) {
    companion object {
        fun fromEnvironment(): Config {
            return Config(
                port = System.getenv("PORT")?.toIntOrNull() ?: 8000,
                dataDir = System.getenv("DATA_DIR") ?: "./data",
                configDir = System.getenv("CONFIG_DIR") ?: "./config",
                maxBodyBytes = System.getenv("MAX_BODY_BYTES")?.toLongOrNull() ?: 1048576L,
                rateLimitPerMinute = System.getenv("RATE_LIMIT_PER_MINUTE")?.toIntOrNull() ?: 600
            )
        }
    }
}

