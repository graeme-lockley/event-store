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
class CreateUserServiceTest {
    private lateinit var application: Application

    @BeforeEach
    fun setup() = runTest {
        application = createApplication()
    }

    @Test
    fun `creates user and emits events`() = runTest {
        val tenantName = "t-1"
        application.createTenant(tenantName)

        val created = application.createUser(
            email = "alice@example.com",
            name = "Alice",
            password = "secret",
            primaryTenantId = tenantName
        )

        assertNotNull(created.id)
        assertEquals("alice@example.com", created.email)
        assertEquals("Alice", created.name)
        assertEquals(UserStatus.ACTIVE, created.status)
        assertEquals(tenantName, created.primaryTenantId)

        val storedEvents = application.getEvents(
            topic = SystemTopics.USERS_TOPIC,
            tenantName = SystemTopics.SYSTEM_TENANT_ID,
            namespaceName = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )
        val userCreatedEvents = storedEvents.filter { it.type == UserEventType.CREATED }
        assertTrue(userCreatedEvents.isNotEmpty())
        assertEquals(UserEventType.CREATED, userCreatedEvents.last().type)
    }

    @Test
    fun `creates user without primary tenant`() = runTest {
        val created = application.createUser(
            email = "bob@example.com",
            name = "Bob",
            password = "password"
        )

        assertNotNull(created.id)
        assertEquals("bob@example.com", created.email)
        assertEquals(null, created.primaryTenantId)
    }

    @Test
    fun `creates user with metadata`() = runTest {
        val metadata = mapOf("department" to "Engineering", "role" to "Developer")

        val created = application.createUser(
            email = "charlie@example.com",
            name = "Charlie",
            password = "password",
            metadata = metadata
        )

        assertEquals(metadata, created.metadata)
    }

    @Test
    fun `creates user with suspended status`() = runTest {
        val created = application.createUser(
            email = "dave@example.com",
            name = "Dave",
            password = "password",
            status = UserStatus.SUSPENDED
        )

        assertEquals(UserStatus.SUSPENDED, created.status)
    }

    @Test
    fun `creates user with custom createdBy`() = runTest {
        val created = application.createUser(
            email = "eve@example.com",
            name = "Eve",
            password = "password",
            createdBy = "admin-user"
        )

        assertNotNull(created.id)
        assertEquals("eve@example.com", created.email)
    }

    @Test
    fun `throws exception when user already exists`() = runTest {
        application.createUser(
            email = "alice@example.com",
            name = "Alice",
            password = "secret"
        )

        assertThrows<com.eventstore.domain.exceptions.UserAlreadyExistsException> {
            application.createUser(
                email = "alice@example.com",
                name = "Alice",
                password = "secret"
            )
        }
    }

    @Test
    fun `throws exception when primary tenant does not exist`() = runTest {
        assertThrows<com.eventstore.domain.exceptions.TenantNotFoundException> {
            application.createUser(
                email = "alice@example.com",
                name = "Alice",
                password = "secret",
                primaryTenantId = "nonexistent-tenant"
            )
        }
    }

}
