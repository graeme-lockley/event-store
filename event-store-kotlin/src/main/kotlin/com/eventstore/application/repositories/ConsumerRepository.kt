package com.eventstore.application.repositories

import com.eventstore.domain.Consumer

/**
 * Repository interface for consumer management operations.
 */
interface ConsumerRepository {
    suspend fun save(consumer: Consumer)
    suspend fun findById(id: String): Consumer?
    suspend fun findAll(): List<Consumer>
    suspend fun findByTopic(topic: String): List<Consumer>
    suspend fun delete(id: String): Boolean
    suspend fun count(): Int
}

