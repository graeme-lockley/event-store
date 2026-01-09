package com.eventstore.domain.services.user

import com.eventstore.domain.Application
import com.eventstore.domain.events.UserEventType
import com.eventstore.domain.services.createApplication
import com.eventstore.domain.tenants.SystemTopics
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UpdateUserServiceTest {
    private lateinit var application: Application

    @BeforeEach
    fun setup() = runTest {
        application = createApplication()
    }

    @Test
    fun `updates user and emits event`() = runTest {
        val created = application.createUser(
            email = "alice@example.com",
            name = "Alice",
            password = "secret"
        )

        val updated = application.updateUser(
            userId = created.id,
            name = "Alice Updated",
            email = "alice.updated@example.com"
        )

        assertEquals(created.id, updated.id)
        assertEquals("Alice Updated", updated.name)
        assertEquals("alice.updated@example.com", updated.email)

        val storedEvents = application.getEvents(
            topic = SystemTopics.USERS_TOPIC,
            tenantName = SystemTopics.SYSTEM_TENANT_ID,
            namespaceName = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )
        val updatedEvents = storedEvents.filter { it.type == UserEventType.UPDATED }
        assertTrue(updatedEvents.isNotEmpty())
    }

    @Test
    fun `updates only name`() = runTest {
        val created = application.createUser(
            email = "alice@example.com",
            name = "Alice",
            password = "secret"
        )

        val updated = application.updateUser(
            userId = created.id,
            name = "Alice Updated"
        )

        assertEquals("Alice Updated", updated.name)
        assertEquals("alice@example.com", updated.email)
    }

    @Test
    fun `updates only email`() = runTest {
        val created = application.createUser(
            email = "alice@example.com",
            name = "Alice",
            password = "secret"
        )

        val updated = application.updateUser(
            userId = created.id,
            email = "alice.new@example.com"
        )

        assertEquals("Alice", updated.name)
        assertEquals("alice.new@example.com", updated.email)
    }

    @Test
    fun `updates metadata`() = runTest {
        val created = application.createUser(
            email = "alice@example.com",
            name = "Alice",
            password = "secret",
            metadata = mapOf("department" to "Engineering")
        )

        val newMetadata = mapOf("department" to "Engineering", "role" to "Senior Developer")
        val updated = application.updateUser(
            userId = created.id,
            metadata = newMetadata
        )

        assertEquals(newMetadata, updated.metadata)
    }

    @Test
    fun `throws exception when user not found`() = runTest {
        assertThrows<com.eventstore.domain.exceptions.UserNotFoundException> {
            application.updateUser(
                userId = "nonexistent-id",
                name = "Updated"
            )
        }
    }

}

