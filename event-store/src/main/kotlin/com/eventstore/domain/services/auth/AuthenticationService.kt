package com.eventstore.domain.services.auth

import com.eventstore.domain.UserStatus
import com.eventstore.domain.exceptions.InvalidCredentialsException
import com.eventstore.infrastructure.auth.Session
import com.eventstore.infrastructure.auth.SessionManager
import com.eventstore.infrastructure.projections.UserProjectionService
import org.mindrot.jbcrypt.BCrypt

class AuthenticationService(
    private val userProjectionService: UserProjectionService,
    private val sessionManager: SessionManager
) {
    suspend fun login(email: String, password: String): Pair<Session, List<String>> {
        val user = userProjectionService.getUserByEmail(email)
            ?: throw InvalidCredentialsException()
        if (user.status != UserStatus.ACTIVE) {
            throw InvalidCredentialsException()
        }
        if (!BCrypt.checkpw(password, user.passwordHash)) {
            throw InvalidCredentialsException()
        }
        val session = sessionManager.createSession(user.id)
        val tenants = userProjectionService.getAssociations(user.id).map { it.tenantId }
        return session to tenants
    }

    fun logout(sessionId: String) {
        sessionManager.invalidate(sessionId)
    }

    fun getSession(sessionId: String): Session? = sessionManager.getSession(sessionId)
}
