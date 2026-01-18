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
import java.util.*

data class TopicConfig(
    val topicId: String,          // UUID as string
    val namespaceId: String,      // UUID as string
    val name: String,
    val sequence: Long,
    val schemas: List<Schema>
)

class FileSystemTopicRepository(
    private val configDir: Path,
    private val objectMapper: ObjectMapper
) : TopicRepository {
    private val logger = LoggerFactory.getLogger(FileSystemTopicRepository::class.java)
    private val mutexes = mutableMapOf<UUID, Mutex>()

    init {
        try {
            Files.createDirectories(configDir)
        } catch (e: Exception) {
            throw TopicConfigException("Failed to create config directory: ${configDir}", e)
        }
    }

    private fun getMutex(topicId: UUID): Mutex {
        return mutexes.getOrPut(topicId) { Mutex() }
    }

    private fun getConfigPath(topicId: UUID): Path {
        return configDir.resolve("$topicId.json")
    }

    private fun ensureParentDirectories(path: Path) {
        path.parent?.let {
            if (!Files.exists(it)) {
                Files.createDirectories(it)
            }
        }
    }

    override suspend fun createTopic(
        topicId: UUID,
        namespaceId: UUID,
        name: String,
        schemas: List<Schema>
    ): Topic {
        return withContext(Dispatchers.IO) {
            try {
                val configPath = getConfigPath(topicId)

                if (Files.exists(configPath)) {
                    throw com.eventstore.domain.exceptions.TopicAlreadyExistsException(name)
                }

                val config = TopicConfig(
                    topicId = topicId.toString(),
                    namespaceId = namespaceId.toString(),
                    name = name,
                    sequence = 0,
                    schemas = schemas
                )
                val json = objectMapper.writeValueAsString(config)
                Files.writeString(configPath, json)

                Topic(
                    topicId = topicId,
                    namespaceId = namespaceId,
                    name = name,
                    sequence = 0,
                    schemas = schemas
                )
            } catch (e: com.eventstore.domain.exceptions.TopicAlreadyExistsException) {
                throw e
            } catch (e: Exception) {
                throw TopicConfigException("Failed to create topic $name", e)
            }
        }
    }

    override suspend fun getTopic(topicId: UUID): Topic? {
        return withContext(Dispatchers.IO) {
            try {
                val configPath = getConfigPath(topicId)
                if (!Files.exists(configPath)) {
                    return@withContext null
                }

                val json = Files.readString(configPath)
                val config: TopicConfig = objectMapper.readValue(json)
                Topic(
                    topicId = UUID.fromString(config.topicId),
                    namespaceId = UUID.fromString(config.namespaceId),
                    name = config.name,
                    sequence = config.sequence,
                    schemas = config.schemas
                )
            } catch (e: Exception) {
                throw TopicConfigException("Failed to read topic configuration for $topicId", e)
            }
        }
    }

    override suspend fun topicExists(topicId: UUID): Boolean {
        return withContext(Dispatchers.IO) {
            Files.exists(getConfigPath(topicId))
        }
    }

    override suspend fun updateSequence(topicId: UUID, sequence: Long) {
        val mutex = getMutex(topicId)
        mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val topic = getTopic(topicId)
                        ?: throw com.eventstore.domain.exceptions.TopicNotFoundException(topicId.toString())

                    val updatedConfig = TopicConfig(
                        topicId = topic.topicId.toString(),
                        namespaceId = topic.namespaceId.toString(),
                        name = topic.name,
                        sequence = sequence,
                        schemas = topic.schemas
                    )
                    val json = objectMapper.writeValueAsString(updatedConfig)
                    val configPath = getConfigPath(topicId)
                    Files.writeString(configPath, json)
                } catch (e: com.eventstore.domain.exceptions.TopicNotFoundException) {
                    throw e
                } catch (e: Exception) {
                    throw TopicConfigException("Failed to update sequence for topic $topicId", e)
                }
            }
        }
    }

    override suspend fun getAndIncrementSequence(topicId: UUID): Long {
        val mutex = getMutex(topicId)
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val topic = getTopic(topicId)
                        ?: throw com.eventstore.domain.exceptions.TopicNotFoundException(topicId.toString())

                    val nextSequence = topic.sequence + 1
                    val updatedConfig = TopicConfig(
                        topicId = topic.topicId.toString(),
                        namespaceId = topic.namespaceId.toString(),
                        name = topic.name,
                        sequence = nextSequence,
                        schemas = topic.schemas
                    )
                    val json = objectMapper.writeValueAsString(updatedConfig)
                    val configPath = getConfigPath(topicId)
                    Files.writeString(configPath, json)

                    nextSequence
                } catch (e: com.eventstore.domain.exceptions.TopicNotFoundException) {
                    throw e
                } catch (e: Exception) {
                    throw TopicConfigException("Failed to get and increment sequence for topic $topicId", e)
                }
            }
        }
    }

    override suspend fun updateSchemas(
        topicId: UUID,
        schemas: List<Schema>
    ): Topic {
        val mutex = getMutex(topicId)
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val current = getTopic(topicId)
                        ?: throw com.eventstore.domain.exceptions.TopicNotFoundException(topicId.toString())

                    val updatedConfig = TopicConfig(
                        topicId = current.topicId.toString(),
                        namespaceId = current.namespaceId.toString(),
                        name = current.name,
                        sequence = current.sequence,
                        schemas = schemas
                    )
                    val json = objectMapper.writeValueAsString(updatedConfig)
                    val configPath = getConfigPath(topicId)
                    Files.writeString(configPath, json)

                    current.copy(schemas = schemas)
                } catch (e: com.eventstore.domain.exceptions.TopicNotFoundException) {
                    throw e
                } catch (e: Exception) {
                    throw TopicConfigException("Failed to update schemas for topic $topicId", e)
                }
            }
        }
    }

    override suspend fun getAllTopics(): List<Topic> {
        return withContext(Dispatchers.IO) {
            if (!Files.exists(configDir)) {
                return@withContext emptyList()
            }

            Files.walk(configDir).use { stream ->
                stream
                    .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".json") }
                    .map { path: Path ->
                        try {
                            val json = Files.readString(path)
                            val config: TopicConfig = objectMapper.readValue(json)
                            Topic(
                                topicId = UUID.fromString(config.topicId),
                                namespaceId = UUID.fromString(config.namespaceId),
                                name = config.name,
                                sequence = config.sequence,
                                schemas = config.schemas
                            )
                        } catch (e: Exception) {
                            logger.warn("Failed to read topic configuration from ${path}: ${e.message}", e)
                            null
                        }
                    }
                    .filter { it != null }
                    .map { it!! }
                    .toList()
            }
        }
    }
}

