package com.eventstore.infrastructure.auth

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class Session(
    val id: String,
    val userId: String,
    val createdAt: Instant = Instant.now()
)

class SessionManager {
    private val sessions = ConcurrentHashMap<String, Session>()

    fun createSession(userId: String): Session {
        val session = Session(UUID.randomUUID().toString(), userId)
        sessions[session.id] = session
        return session
    }

    fun getSession(sessionId: String): Session? = sessions[sessionId]

    fun invalidate(sessionId: String) {
        sessions.remove(sessionId)
    }
}

