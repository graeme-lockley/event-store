package com.eventstore.domain.services.user

import com.eventstore.domain.Application
import com.eventstore.domain.events.UserEventType
import com.eventstore.domain.exceptions.InvalidCredentialsException
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
class ChangePasswordServiceTest {
    private lateinit var application: Application

    @BeforeEach
    fun setup() =
        runTest {
            application = createApplication()
        }

    @Test
    fun `changes password and emits event`() =
        runTest {
            val user =
                application.createUser(
                    email = "alice@example.com",
                    name = "Alice",
                    password = "old-password",
                )

            val result =
                application.changePassword(
                    userId = user.id,
                    oldPassword = "old-password",
                    newPassword = "new-password",
                )

            assertEquals(true, result)

            val storedEvents =
                application.getEvents(
                    topicId = SystemTopics.USERS_TOPIC_ID,
                )
            val passwordChangedEvents = storedEvents.filter { it.type == UserEventType.PASSWORD_CHANGED }
            assertTrue(passwordChangedEvents.isNotEmpty())
        }

    @Test
    fun `changes password with custom changedBy`() =
        runTest {
            val user =
                application.createUser(
                    email = "alice@example.com",
                    name = "Alice",
                    password = "old-password",
                )

            val result =
                application.changePassword(
                    userId = user.id,
                    oldPassword = "old-password",
                    newPassword = "new-password",
                    changedBy = "admin-user",
                )

            assertEquals(true, result)
        }

    @Test
    fun `throws exception when user not found`() =
        runTest {
            assertThrows<com.eventstore.domain.exceptions.UserNotFoundException> {
                application.changePassword(
                    userId = "nonexistent-id",
                    oldPassword = "old-password",
                    newPassword = "new-password",
                )
            }
        }

    @Test
    fun `throws exception when old password is incorrect`() =
        runTest {
            val user =
                application.createUser(
                    email = "alice@example.com",
                    name = "Alice",
                    password = "correct-password",
                )

            assertThrows<InvalidCredentialsException> {
                application.changePassword(
                    userId = user.id,
                    oldPassword = "wrong-password",
                    newPassword = "new-password",
                )
            }
        }
}
