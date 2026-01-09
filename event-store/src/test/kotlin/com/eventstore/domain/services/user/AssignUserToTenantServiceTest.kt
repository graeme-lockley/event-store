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
class AssignUserToTenantServiceTest {
    private lateinit var application: Application

    @BeforeEach
    fun setup() = runTest {
        application = createApplication()
    }

    @Test
    fun `assigns user to tenant and emits event`() = runTest {
        val tenantName = "acme"
        application.createTenant(tenantName)

        val user = application.createUser(
            email = "alice@example.com",
            name = "Alice",
            password = "secret"
        )

        val result = application.assignUserToTenant(
            userId = user.id,
            tenantId = tenantName
        )

        assertEquals(true, result)

        val storedEvents = application.getEvents(
            topic = SystemTopics.USERS_TOPIC,
            tenantName = SystemTopics.SYSTEM_TENANT_ID,
            namespaceName = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )
        val assignedEvents = storedEvents.filter { it.type == UserEventType.TENANT_ASSIGNED }
        assertTrue(assignedEvents.isNotEmpty())
    }

    @Test
    fun `assigns user to tenant with role`() = runTest {
        val tenantName = "acme"
        application.createTenant(tenantName)

        val user = application.createUser(
            email = "alice@example.com",
            name = "Alice",
            password = "secret"
        )

        val result = application.assignUserToTenant(
            userId = user.id,
            tenantId = tenantName,
            role = "admin"
        )

        assertEquals(true, result)
    }

    @Test
    fun `assigns user to tenant as primary`() = runTest {
        val tenantName = "acme"
        application.createTenant(tenantName)

        val user = application.createUser(
            email = "alice@example.com",
            name = "Alice",
            password = "secret"
        )

        val result = application.assignUserToTenant(
            userId = user.id,
            tenantId = tenantName,
            isPrimary = true
        )

        assertEquals(true, result)
    }

    @Test
    fun `throws exception when user not found`() = runTest {
        val tenantName = "acme"
        application.createTenant(tenantName)

        assertThrows<com.eventstore.domain.exceptions.UserNotFoundException> {
            application.assignUserToTenant(
                userId = "nonexistent-id",
                tenantId = tenantName
            )
        }
    }

    @Test
    fun `throws exception when tenant not found`() = runTest {
        val user = application.createUser(
            email = "alice@example.com",
            name = "Alice",
            password = "secret"
        )

        assertThrows<com.eventstore.domain.exceptions.TenantNotFoundException> {
            application.assignUserToTenant(
                userId = user.id,
                tenantId = "nonexistent-tenant"
            )
        }
    }

    @Test
    fun `throws exception when multi-tenant is disabled`() = runTest {
        val appWithoutMultiTenant = Application(
            bootstrap = true, // Need bootstrap to create system topics
            config = com.eventstore.Config(
                port = 0,
                dataDir = "./data",
                configDir = "./config",
                maxBodyBytes = 1024,
                rateLimitPerMinute = 10,
                multiTenantEnabled = false,
                authEnabled = false
            )
        )

        assertThrows<IllegalStateException> {
            appWithoutMultiTenant.assignUserToTenant("some-id", "some-tenant")
        }
    }
}

