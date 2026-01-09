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
class RemoveUserFromTenantServiceTest {
    private lateinit var application: Application

    @BeforeEach
    fun setup() = runTest {
        application = createApplication()
    }

    @Test
    fun `removes user from tenant and emits event`() = runTest {
        val tenantName = "acme"
        application.createTenant(tenantName)

        val user = application.createUser(
            email = "alice@example.com",
            name = "Alice",
            password = "secret"
        )

        application.assignUserToTenant(user.id, tenantName)

        val result = application.removeUserFromTenant(
            userId = user.id,
            tenantId = tenantName
        )

        assertEquals(true, result)

        val storedEvents = application.getEvents(
            topic = SystemTopics.USERS_TOPIC,
            tenantName = SystemTopics.SYSTEM_TENANT_ID,
            namespaceName = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )
        val removedEvents = storedEvents.filter { it.type == UserEventType.TENANT_REMOVED }
        assertTrue(removedEvents.isNotEmpty())
    }

    @Test
    fun `removes user from tenant with reason`() = runTest {
        val tenantName = "acme"
        application.createTenant(tenantName)

        val user = application.createUser(
            email = "alice@example.com",
            name = "Alice",
            password = "secret"
        )

        application.assignUserToTenant(user.id, tenantName)

        val result = application.removeUserFromTenant(
            userId = user.id,
            tenantId = tenantName,
            reason = "User left organization"
        )

        assertEquals(true, result)
    }

    @Test
    fun `removes user from tenant with custom removedBy`() = runTest {
        val tenantName = "acme"
        application.createTenant(tenantName)

        val user = application.createUser(
            email = "alice@example.com",
            name = "Alice",
            password = "secret"
        )

        application.assignUserToTenant(user.id, tenantName)

        val result = application.removeUserFromTenant(
            userId = user.id,
            tenantId = tenantName,
            removedBy = "admin-user"
        )

        assertEquals(true, result)
    }

    @Test
    fun `throws exception when user not found`() = runTest {
        val tenantName = "acme"
        application.createTenant(tenantName)

        assertThrows<com.eventstore.domain.exceptions.UserNotFoundException> {
            application.removeUserFromTenant(
                userId = "nonexistent-id",
                tenantId = tenantName
            )
        }
    }

}

