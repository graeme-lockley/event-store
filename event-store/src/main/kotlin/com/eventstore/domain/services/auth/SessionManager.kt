package com.eventstore.domain.services.auth

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class Session(
    val sessionId: String,
    val userId: String,
    val tenantIds: List<String>
)

class SessionManager {
    private val sessions = ConcurrentHashMap<String, Session>()

    fun createSession(userId: String, tenantIds: List<String>): Session {
        val sessionId = UUID.randomUUID().toString()
        val session = Session(sessionId, userId, tenantIds)
        sessions[sessionId] = session
        return session
    }

    fun getSession(sessionId: String?): Session? = sessionId?.let { sessions[it] }

    fun deleteSession(sessionId: String?) {
        if (sessionId != null) {
            sessions.remove(sessionId)
        }
    }
}

