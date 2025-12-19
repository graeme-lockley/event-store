package com.eventstore.domain.services.user

import com.eventstore.infrastructure.projections.UserProjectionService

class GetUserService(
    private val userProjectionService: UserProjectionService
) {
    suspend fun getById(id: String) = userProjectionService.getUser(id)
    suspend fun getByEmail(email: String) = userProjectionService.getUserByEmail(email)
    suspend fun list() = userProjectionService.getAllUsers()
}

