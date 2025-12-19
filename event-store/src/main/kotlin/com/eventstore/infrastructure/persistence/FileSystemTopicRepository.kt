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
import java.util.UUID

data class TopicConfig(
    val resourceId: String? = null,  // UUID as string, nullable for backward compatibility
    val tenantResourceId: String? = null,  // UUID as string, nullable for backward compatibility
    val namespaceResourceId: String? = null,  // UUID as string, nullable for backward compatibility
    val name: String,
    val sequence: Long,
    val schemas: List<Schema>,
    val tenantId: String = "default",  // Kept for backward compatibility
    val namespaceId: String = "default"  // Kept for backward compatibility
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

    private fun getMutex(topicName: String, tenantName: String, namespaceName: String): Mutex {
        return mutexes.getOrPut("$tenantName/$namespaceName/$topicName") { Mutex() }
    }

    private fun getConfigPath(topicName: String, tenantName: String, namespaceName: String): Path {
        return configDir.resolve(tenantName).resolve(namespaceName).resolve("$topicName.json")
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
        resourceId: UUID,
        tenantResourceId: UUID,
        namespaceResourceId: UUID,
        name: String,
        schemas: List<Schema>,
        tenantName: String,
        namespaceName: String
    ): Topic {
        return withContext(Dispatchers.IO) {
            try {
                val configPath = getConfigPath(name, tenantName, namespaceName)
                val legacyPath = getLegacyConfigPath(name)
                ensureParentDirectories(configPath)

                if (Files.exists(configPath) || Files.exists(legacyPath)) {
                    throw com.eventstore.domain.exceptions.TopicAlreadyExistsException(name)
                }

                val config = TopicConfig(
                    resourceId = resourceId.toString(),
                    tenantResourceId = tenantResourceId.toString(),
                    namespaceResourceId = namespaceResourceId.toString(),
                    name = name,
                    sequence = 0,
                    schemas = schemas,
                    tenantId = tenantName,
                    namespaceId = namespaceName
                )
                val json = objectMapper.writeValueAsString(config)
                Files.writeString(configPath, json)

                Topic(
                    resourceId = resourceId,
                    tenantResourceId = tenantResourceId,
                    namespaceResourceId = namespaceResourceId,
                    name = name,
                    sequence = 0,
                    schemas = schemas,
                    tenantName = tenantName,
                    namespaceName = namespaceName
                )
            } catch (e: com.eventstore.domain.exceptions.TopicAlreadyExistsException) {
                throw e
            } catch (e: Exception) {
                throw TopicConfigException("Failed to create topic $name", e)
            }
        }
    }

    override suspend fun getTopic(name: String, tenantName: String, namespaceName: String): Topic? {
        return withContext(Dispatchers.IO) {
            try {
                val scopedPath = getConfigPath(name, tenantName, namespaceName)
                val pathToRead = when {
                    Files.exists(scopedPath) -> scopedPath
                    Files.exists(getLegacyConfigPath(name)) -> getLegacyConfigPath(name)
                    else -> return@withContext null
                }

                val json = Files.readString(pathToRead)
                val config: TopicConfig = objectMapper.readValue(json)
                // Generate resourceIds if not present (backward compatibility)
                val resourceId = config.resourceId?.let { UUID.fromString(it) } ?: UUID.randomUUID()
                val tenantResourceId = config.tenantResourceId?.let { UUID.fromString(it) } ?: UUID.randomUUID()
                val namespaceResourceId = config.namespaceResourceId?.let { UUID.fromString(it) } ?: UUID.randomUUID()
                Topic(
                    resourceId = resourceId,
                    tenantResourceId = tenantResourceId,
                    namespaceResourceId = namespaceResourceId,
                    name = config.name,
                    sequence = config.sequence,
                    schemas = config.schemas,
                    tenantName = config.tenantId,
                    namespaceName = config.namespaceId
                )
            } catch (e: Exception) {
                throw TopicConfigException("Failed to read topic configuration for $name", e)
            }
        }
    }

    override suspend fun topicExists(name: String, tenantName: String, namespaceName: String): Boolean {
        return withContext(Dispatchers.IO) {
            Files.exists(getConfigPath(name, tenantName, namespaceName)) || Files.exists(getLegacyConfigPath(name))
        }
    }

    override suspend fun updateSequence(name: String, sequence: Long, tenantName: String, namespaceName: String) {
        val mutex = getMutex(name, tenantName, namespaceName)
        mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val topic = getTopic(name, tenantName, namespaceName)
                        ?: throw com.eventstore.domain.exceptions.TopicNotFoundException(name)

                    val updatedConfig = TopicConfig(
                        resourceId = topic.resourceId.toString(),
                        tenantResourceId = topic.tenantResourceId.toString(),
                        namespaceResourceId = topic.namespaceResourceId.toString(),
                        name = topic.name,
                        sequence = sequence,
                        schemas = topic.schemas,
                        tenantId = topic.tenantName,
                        namespaceId = topic.namespaceName
                    )
                    val json = objectMapper.writeValueAsString(updatedConfig)
                    val configPath = getConfigPath(topic.name, topic.tenantName, topic.namespaceName)
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

    override suspend fun getAndIncrementSequence(topicName: String, tenantName: String, namespaceName: String): Long {
        val mutex = getMutex(topicName, tenantName, namespaceName)
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val topic = getTopic(topicName, tenantName, namespaceName)
                        ?: throw com.eventstore.domain.exceptions.TopicNotFoundException(topicName)

                    val nextSequence = topic.sequence + 1
                    val updatedConfig = TopicConfig(
                        resourceId = topic.resourceId.toString(),
                        tenantResourceId = topic.tenantResourceId.toString(),
                        namespaceResourceId = topic.namespaceResourceId.toString(),
                        name = topic.name,
                        sequence = nextSequence,
                        schemas = topic.schemas,
                        tenantId = topic.tenantName,
                        namespaceId = topic.namespaceName
                    )
                    val json = objectMapper.writeValueAsString(updatedConfig)
                    val configPath = getConfigPath(topic.name, topic.tenantName, topic.namespaceName)
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
        tenantName: String,
        namespaceName: String
    ): Topic {
        val mutex = getMutex(name, tenantName, namespaceName)
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val current = getTopic(name, tenantName, namespaceName)
                        ?: throw com.eventstore.domain.exceptions.TopicNotFoundException(name)

                    val updatedConfig = TopicConfig(
                        resourceId = current.resourceId.toString(),
                        tenantResourceId = current.tenantResourceId.toString(),
                        namespaceResourceId = current.namespaceResourceId.toString(),
                        name = current.name,
                        sequence = current.sequence,
                        schemas = schemas,
                        tenantId = current.tenantName,
                        namespaceId = current.namespaceName
                    )
                    val json = objectMapper.writeValueAsString(updatedConfig)
                    val configPath = getConfigPath(current.name, current.tenantName, current.namespaceName)
                    ensureParentDirectories(configPath)
                    Files.writeString(configPath, json)

                    current.copy(schemas = schemas)
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
                            // Generate resourceIds if not present (backward compatibility)
                            val resourceId = config.resourceId?.let { UUID.fromString(it) } ?: UUID.randomUUID()
                            val tenantResourceId = config.tenantResourceId?.let { UUID.fromString(it) } ?: UUID.randomUUID()
                            val namespaceResourceId = config.namespaceResourceId?.let { UUID.fromString(it) } ?: UUID.randomUUID()
                            Topic(
                                resourceId = resourceId,
                                tenantResourceId = tenantResourceId,
                                namespaceResourceId = namespaceResourceId,
                                name = config.name,
                                sequence = config.sequence,
                                schemas = config.schemas,
                                tenantName = config.tenantId,
                                namespaceName = config.namespaceId
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

