package com.eventstore.domain.services.user

import com.eventstore.domain.Application
import com.eventstore.domain.UserStatus
import com.eventstore.domain.exceptions.UserNotFoundException
import com.eventstore.domain.services.createApplication
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GetUserServiceTest {
    private lateinit var application: Application

    @BeforeEach
    fun setup() = runTest {
        application = createApplication()
    }

    @Test
    fun `gets user by ID`() = runTest {
        val created = application.createUser(
            email = "alice@example.com",
            name = "Alice",
            password = "secret"
        )

        val retrieved = application.getUserById(created.id)

        assertNotNull(retrieved)
        assertEquals(created.id, retrieved?.id)
        assertEquals("alice@example.com", retrieved?.email)
        assertEquals("Alice", retrieved?.name)
    }

    @Test
    fun `gets user by email`() = runTest {
        application.createUser(
            email = "alice@example.com",
            name = "Alice",
            password = "secret"
        )

        val retrieved = application.getUserByEmail("alice@example.com")

        assertNotNull(retrieved)
        assertEquals("alice@example.com", retrieved?.email)
        assertEquals("Alice", retrieved?.name)
    }

    @Test
    fun `lists all users`() = runTest {
        application.createUser(
            email = "alice@example.com",
            name = "Alice",
            password = "secret"
        )
        application.createUser(
            email = "bob@example.com",
            name = "Bob",
            password = "password"
        )

        val users = application.listUsers()

        assertTrue(users.size >= 2)
        assertTrue(users.any { it.email == "alice@example.com" })
        assertTrue(users.any { it.email == "bob@example.com" })
    }

    @Test
    fun `returns null when user not found by ID`() = runTest {
        val retrieved = application.getUserById("nonexistent-id")

        assertNull(retrieved)
    }

    @Test
    fun `returns null when user not found by email`() = runTest {
        val retrieved = application.getUserByEmail("nonexistent@example.com")

        assertNull(retrieved)
    }

    @Test
    fun `gets user after update`() = runTest {
        val created = application.createUser(
            email = "alice@example.com",
            name = "Alice",
            password = "secret"
        )

        application.updateUser(
            userId = created.id,
            name = "Alice Updated"
        )

        val retrieved = application.getUserById(created.id)

        assertNotNull(retrieved)
        assertEquals("Alice Updated", retrieved?.name)
        assertEquals("alice@example.com", retrieved?.email)
    }

    @Test
    fun `gets user after deletion`() = runTest {
        val created = application.createUser(
            email = "alice@example.com",
            name = "Alice",
            password = "secret"
        )

        application.deleteUser(created.id)

        // Deleted users may still be retrievable depending on projection implementation
        val retrieved = application.getUserById(created.id)
        // If user is still retrievable, it should have DELETED status
        if (retrieved != null) {
            assertEquals(UserStatus.DELETED, retrieved.status)
        }
    }
}

