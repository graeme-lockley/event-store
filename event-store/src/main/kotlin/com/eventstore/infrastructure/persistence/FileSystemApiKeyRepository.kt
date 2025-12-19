package com.eventstore.infrastructure.persistence

import com.eventstore.domain.ApiKey
import com.eventstore.domain.ports.outbound.ApiKeyRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class FileSystemApiKeyRepository(
    private val configDir: Path,
    private val objectMapper: ObjectMapper
) : ApiKeyRepository {
    private val logger = LoggerFactory.getLogger(FileSystemApiKeyRepository::class.java)
    private val mutex = Mutex()
    private val apiKeysFile: Path = configDir.resolve("api-keys.json")

    init {
        try {
            Files.createDirectories(configDir)
        } catch (e: Exception) {
            throw RuntimeException("Failed to create config directory: ${configDir}", e)
        }
    }

    private suspend fun loadApiKeys(): MutableList<ApiKey> = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!Files.exists(apiKeysFile)) {
                return@withLock mutableListOf()
            }
            try {
                val content = Files.readString(apiKeysFile)
                if (content.isBlank()) {
                    return@withLock mutableListOf()
                }
                val list: List<ApiKey> = objectMapper.readValue(content)
                list.toMutableList()
            } catch (e: Exception) {
                logger.error("Failed to load API keys from file", e)
                mutableListOf()
            }
        }
    }

    private suspend fun saveApiKeys(apiKeys: List<ApiKey>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val tempFile = apiKeysFile.resolveSibling("${apiKeysFile.fileName}.tmp")
                val json = objectMapper.writeValueAsString(apiKeys)
                Files.writeString(tempFile, json)
                Files.move(tempFile, apiKeysFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (e: Exception) {
                logger.error("Failed to save API keys to file", e)
                throw RuntimeException("Failed to save API keys", e)
            }
        }
    }

    override suspend fun save(apiKey: ApiKey) {
        val apiKeys = loadApiKeys()
        val index = apiKeys.indexOfFirst { it.id == apiKey.id }
        if (index >= 0) {
            apiKeys[index] = apiKey
        } else {
            apiKeys.add(apiKey)
        }
        saveApiKeys(apiKeys)
    }

    override suspend fun findById(id: String): ApiKey? {
        val apiKeys = loadApiKeys()
        return apiKeys.find { it.id == id }
    }

    override suspend fun findByKeyHash(keyHash: String): ApiKey? {
        val apiKeys = loadApiKeys()
        return apiKeys.find { it.keyHash == keyHash }
    }

    override suspend fun findByUserId(userId: String): List<ApiKey> {
        val apiKeys = loadApiKeys()
        return apiKeys.filter { it.userId == userId }
    }

    override suspend fun delete(id: String) {
        val apiKeys = loadApiKeys()
        apiKeys.removeAll { it.id == id }
        saveApiKeys(apiKeys)
    }
}

