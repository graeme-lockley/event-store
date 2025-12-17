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
    val schemas: List<Schema>,
    val tenantId: String = "default",
    val namespaceId: String = "default"
)

class FileSystemTopicRepository(
    private val configDir: Path,
    private val objectMapper: ObjectMapper
) : TopicRepository {
    private val logger = LoggerFactory.getLogger(FileSystemTopicRepository::class.java)
    private val mutexes = mutableMapOf<String, Mutex>()

    init {
        try {
            Files.createDirectories(configDir)
        } catch (e: Exception) {
            throw TopicConfigException("Failed to create config directory: ${configDir}", e)
        }
    }

    private fun getMutex(topicName: String, tenantId: String, namespaceId: String): Mutex {
        return mutexes.getOrPut("$tenantId/$namespaceId/$topicName") { Mutex() }
    }

    private fun getConfigPath(topicName: String, tenantId: String, namespaceId: String): Path {
        return configDir.resolve(tenantId).resolve(namespaceId).resolve("$topicName.json")
    }

    private fun getLegacyConfigPath(topicName: String): Path {
        return configDir.resolve("$topicName.json")
    }

    private fun ensureParentDirectories(path: Path) {
        path.parent?.let {
            if (!Files.exists(it)) {
                Files.createDirectories(it)
            }
        }
    }

    override suspend fun createTopic(
        name: String,
        schemas: List<Schema>,
        tenantId: String,
        namespaceId: String
    ): Topic {
        return withContext(Dispatchers.IO) {
            try {
                val configPath = getConfigPath(name, tenantId, namespaceId)
                val legacyPath = getLegacyConfigPath(name)
                ensureParentDirectories(configPath)

                if (Files.exists(configPath) || Files.exists(legacyPath)) {
                    throw com.eventstore.domain.exceptions.TopicAlreadyExistsException(name)
                }

                val config = TopicConfig(name, 0, schemas, tenantId, namespaceId)
                val json = objectMapper.writeValueAsString(config)
                Files.writeString(configPath, json)

                Topic(name, 0, schemas, tenantId, namespaceId)
            } catch (e: com.eventstore.domain.exceptions.TopicAlreadyExistsException) {
                throw e
            } catch (e: Exception) {
                throw TopicConfigException("Failed to create topic $name", e)
            }
        }
    }

    override suspend fun getTopic(name: String, tenantId: String, namespaceId: String): Topic? {
        return withContext(Dispatchers.IO) {
            try {
                val scopedPath = getConfigPath(name, tenantId, namespaceId)
                val pathToRead = when {
                    Files.exists(scopedPath) -> scopedPath
                    Files.exists(getLegacyConfigPath(name)) -> getLegacyConfigPath(name)
                    else -> return@withContext null
                }

                val json = Files.readString(pathToRead)
                val config: TopicConfig = objectMapper.readValue(json)
                Topic(config.name, config.sequence, config.schemas, config.tenantId, config.namespaceId)
            } catch (e: Exception) {
                throw TopicConfigException("Failed to read topic configuration for $name", e)
            }
        }
    }

    override suspend fun topicExists(name: String, tenantId: String, namespaceId: String): Boolean {
        return withContext(Dispatchers.IO) {
            Files.exists(getConfigPath(name, tenantId, namespaceId)) || Files.exists(getLegacyConfigPath(name))
        }
    }

    override suspend fun updateSequence(name: String, sequence: Long, tenantId: String, namespaceId: String) {
        val mutex = getMutex(name, tenantId, namespaceId)
        mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val config = getTopic(name, tenantId, namespaceId)
                        ?: throw com.eventstore.domain.exceptions.TopicNotFoundException(name)

                    val updatedConfig = TopicConfig(config.name, sequence, config.schemas, config.tenantId, config.namespaceId)
                    val json = objectMapper.writeValueAsString(updatedConfig)
                    val configPath = getConfigPath(config.name, config.tenantId, config.namespaceId)
                    ensureParentDirectories(configPath)
                    Files.writeString(configPath, json)
                } catch (e: com.eventstore.domain.exceptions.TopicNotFoundException) {
                    throw e
                } catch (e: Exception) {
                    throw TopicConfigException("Failed to update sequence for topic $name", e)
                }
            }
        }
    }

    override suspend fun getAndIncrementSequence(topicName: String, tenantId: String, namespaceId: String): Long {
        val mutex = getMutex(topicName, tenantId, namespaceId)
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val config = getTopic(topicName, tenantId, namespaceId)
                        ?: throw com.eventstore.domain.exceptions.TopicNotFoundException(topicName)

                    val nextSequence = config.sequence + 1
                    val updatedConfig = TopicConfig(
                        config.name,
                        nextSequence,
                        config.schemas,
                        config.tenantId,
                        config.namespaceId
                    )
                    val json = objectMapper.writeValueAsString(updatedConfig)
                    val configPath = getConfigPath(config.name, config.tenantId, config.namespaceId)
                    ensureParentDirectories(configPath)
                    Files.writeString(configPath, json)

                    nextSequence
                } catch (e: com.eventstore.domain.exceptions.TopicNotFoundException) {
                    throw e
                } catch (e: Exception) {
                    throw TopicConfigException("Failed to get and increment sequence for topic $topicName", e)
                }
            }
        }
    }

    override suspend fun updateSchemas(
        name: String,
        schemas: List<Schema>,
        tenantId: String,
        namespaceId: String
    ): Topic {
        val mutex = getMutex(name, tenantId, namespaceId)
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val current = getTopic(name, tenantId, namespaceId)
                        ?: throw com.eventstore.domain.exceptions.TopicNotFoundException(name)

                    val updatedConfig = TopicConfig(
                        current.name,
                        current.sequence,
                        schemas,
                        current.tenantId,
                        current.namespaceId
                    )
                    val json = objectMapper.writeValueAsString(updatedConfig)
                    val configPath = getConfigPath(current.name, current.tenantId, current.namespaceId)
                    ensureParentDirectories(configPath)
                    Files.writeString(configPath, json)

                    Topic(
                        updatedConfig.name,
                        updatedConfig.sequence,
                        updatedConfig.schemas,
                        updatedConfig.tenantId,
                        updatedConfig.namespaceId
                    )
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

            Files.walk(configDir).use { stream ->
                stream
                    .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".json") }
                    .map { path: Path ->
                        try {
                            val json = Files.readString(path)
                            val config: TopicConfig = objectMapper.readValue(json)
                            Topic(config.name, config.sequence, config.schemas, config.tenantId, config.namespaceId)
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

