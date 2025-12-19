package com.eventstore.domain.ports.outbound

import com.eventstore.domain.User
import com.eventstore.domain.UserTenantAssociation

interface UserRepository {
    suspend fun save(user: User)
    suspend fun findById(id: String): User?
    suspend fun findByEmail(email: String): User?
    suspend fun findAll(): List<User>
    suspend fun saveAssociation(association: UserTenantAssociation)
    suspend fun getAssociations(userId: String): List<UserTenantAssociation>
}

