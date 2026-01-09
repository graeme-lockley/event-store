package com.eventstore.domain.services.user

import com.eventstore.domain.Application
import com.eventstore.domain.UserStatus
import com.eventstore.domain.events.UserEventType
import com.eventstore.domain.services.createApplication
import com.eventstore.domain.tenants.SystemTopics
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeleteUserServiceTest {
    private lateinit var application: Application

    @BeforeEach
    fun setup() = runTest {
        application = createApplication()
    }

    @Test
    fun `deletes user and emits event`() = runTest {
        val created = application.createUser(
            email = "alice@example.com",
            name = "Alice",
            password = "secret"
        )

        val deleted = application.deleteUser(created.id)

        assertEquals(created.id, deleted.id)
        assertEquals(UserStatus.DELETED, deleted.status)
        assertNotNull(deleted.updatedAt)

        val storedEvents = application.getEvents(
            topic = SystemTopics.USERS_TOPIC,
            tenantName = SystemTopics.SYSTEM_TENANT_ID,
            namespaceName = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )
        val statusChangedEvents = storedEvents.filter { it.type == UserEventType.STATUS_CHANGED }
        assertTrue(statusChangedEvents.isNotEmpty())
    }

    @Test
    fun `deletes user with reason`() = runTest {
        val created = application.createUser(
            email = "alice@example.com",
            name = "Alice",
            password = "secret"
        )

        val deleted = application.deleteUser(
            userId = created.id,
            reason = "User requested deletion"
        )

        assertEquals(UserStatus.DELETED, deleted.status)
    }

    @Test
    fun `deletes user with custom deletedBy`() = runTest {
        val created = application.createUser(
            email = "alice@example.com",
            name = "Alice",
            password = "secret"
        )

        val deleted = application.deleteUser(
            userId = created.id,
            deletedBy = "admin-user"
        )

        assertEquals(UserStatus.DELETED, deleted.status)
    }

    @Test
    fun `throws exception when user not found`() = runTest {
        assertThrows<com.eventstore.domain.exceptions.UserNotFoundException> {
            application.deleteUser("nonexistent-id")
        }
    }

}

