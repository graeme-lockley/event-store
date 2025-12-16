package com.eventstore.infrastructure.persistence

import com.eventstore.domain.Schema
import com.eventstore.domain.Topic
import com.eventstore.domain.exceptions.TopicConfigException
import com.eventstore.domain.ports.outbound.TopicRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

data class TopicConfig(
    val name: String,
    val sequence: Long,
    val schemas: List<Schema>
)

class FileSystemTopicRepository(
    private val configDir: Path,
    private val objectMapper: ObjectMapper
) : TopicRepository {
    private val logger = LoggerFactory.getLogger(FileSystemTopicRepository::class.java)
    private val mutexes = mutableMapOf<String, Mutex>()

    init {
        // Ensure config directory exists
        try {
            Files.createDirectories(configDir)
        } catch (e: Exception) {
            throw TopicConfigException("Failed to create config directory: ${configDir}", e)
        }
    }

    private fun getMutex(topicName: String): Mutex {
        return mutexes.getOrPut(topicName) { Mutex() }
    }

    private fun getConfigPath(topicName: String): Path {
        return configDir.resolve("$topicName.json")
    }

    override suspend fun createTopic(name: String, schemas: List<Schema>): Topic {
        return withContext(Dispatchers.IO) {
            try {
                val configPath = getConfigPath(name)

                // Check if topic already exists
                if (Files.exists(configPath)) {
                    throw com.eventstore.domain.exceptions.TopicAlreadyExistsException(name)
                }

                val config = TopicConfig(name, 0, schemas)
                val json = objectMapper.writeValueAsString(config)
                Files.writeString(configPath, json)

                Topic(name, 0, schemas)
            } catch (e: com.eventstore.domain.exceptions.TopicAlreadyExistsException) {
                throw e
            } catch (e: Exception) {
                throw TopicConfigException("Failed to create topic $name", e)
            }
        }
    }

    override suspend fun getTopic(name: String): Topic? {
        return withContext(Dispatchers.IO) {
            try {
                val configPath = getConfigPath(name)
                if (!Files.exists(configPath)) {
                    return@withContext null
                }

                val json = Files.readString(configPath)
                val config: TopicConfig = objectMapper.readValue(json)
                Topic(config.name, config.sequence, config.schemas)
            } catch (e: Exception) {
                throw TopicConfigException("Failed to read topic configuration for $name", e)
            }
        }
    }

    override suspend fun topicExists(name: String): Boolean {
        return withContext(Dispatchers.IO) {
            Files.exists(getConfigPath(name))
        }
    }

    override suspend fun updateSequence(name: String, sequence: Long) {
        val mutex = getMutex(name)
        mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val config = getTopic(name)
                        ?: throw com.eventstore.domain.exceptions.TopicNotFoundException(name)

                    val updatedConfig = TopicConfig(config.name, sequence, config.schemas)
                    val json = objectMapper.writeValueAsString(updatedConfig)
                    Files.writeString(getConfigPath(name), json)
                } catch (e: com.eventstore.domain.exceptions.TopicNotFoundException) {
                    throw e
                } catch (e: Exception) {
                    throw TopicConfigException("Failed to update sequence for topic $name", e)
                }
            }
        }
    }

    override suspend fun getAndIncrementSequence(topicName: String): Long {
        val mutex = getMutex(topicName)
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val config = getTopic(topicName)
                        ?: throw com.eventstore.domain.exceptions.TopicNotFoundException(topicName)

                    val nextSequence = config.sequence + 1
                    val updatedConfig = TopicConfig(config.name, nextSequence, config.schemas)
                    val json = objectMapper.writeValueAsString(updatedConfig)
                    Files.writeString(getConfigPath(topicName), json)

                    nextSequence
                } catch (e: com.eventstore.domain.exceptions.TopicNotFoundException) {
                    throw e
                } catch (e: Exception) {
                    throw TopicConfigException("Failed to get and increment sequence for topic $topicName", e)
                }
            }
        }
    }

    override suspend fun updateSchemas(name: String, schemas: List<Schema>): Topic {
        val mutex = getMutex(name)
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val current = getTopic(name)
                        ?: throw com.eventstore.domain.exceptions.TopicNotFoundException(name)

                    val updatedConfig = TopicConfig(current.name, current.sequence, schemas)
                    val json = objectMapper.writeValueAsString(updatedConfig)
                    Files.writeString(getConfigPath(name), json)

                    Topic(updatedConfig.name, updatedConfig.sequence, updatedConfig.schemas)
                } catch (e: com.eventstore.domain.exceptions.TopicNotFoundException) {
                    throw e
                } catch (e: Exception) {
                    throw TopicConfigException("Failed to update schemas for topic $name", e)
                }
            }
        }
    }

    override suspend fun getAllTopics(): List<Topic> {
        return withContext(Dispatchers.IO) {
            if (!Files.exists(configDir)) {
                return@withContext emptyList()
            }

            Files.list(configDir).use { stream ->
                stream
                    .filter { it.fileName.toString().endsWith(".json") }
                    .toList()
                    .mapNotNull { path: Path ->
                        try {
                            val json = Files.readString(path)
                            val config: TopicConfig = objectMapper.readValue(json)
                            Topic(config.name, config.sequence, config.schemas)
                        } catch (e: Exception) {
                            logger.warn("Failed to read topic configuration from ${path}: ${e.message}", e)
                            null
                        }
                    }
            }
        }
    }
}

