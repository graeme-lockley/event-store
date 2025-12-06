package com.eventstore.infrastructure.external

import com.eventstore.domain.Schema
import com.eventstore.domain.exceptions.SchemaNotFoundException
import com.eventstore.domain.exceptions.SchemaValidationException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JsonSchemaValidatorTest {

    private val objectMapper = jacksonObjectMapper()
    private val validator = JsonSchemaValidator(objectMapper)

    @Test
    fun `should register and validate schema successfully`() = runTest {
        val schema = Schema(
            eventType = "user.created",
            properties = mapOf(
                "id" to mapOf("type" to "string"),
                "name" to mapOf("type" to "string")
            ),
            required = listOf("id", "name")
        )

        validator.registerSchemas("user-events", listOf(schema))

        val payload = mapOf("id" to "123", "name" to "Alice")
        validator.validateEvent("user-events", "user.created", payload)

        assertTrue(validator.hasSchema("user-events", "user.created"))
    }

    @Test
    fun `should throw exception when schema not found`() = runTest {
        assertThrows<SchemaNotFoundException> {
            validator.validateEvent("user-events", "user.created", mapOf("id" to "123"))
        }

        assertFalse(validator.hasSchema("user-events", "user.created"))
    }

    @Test
    fun `should throw exception for invalid payload`() = runTest {
        val schema = Schema(
            eventType = "user.created",
            properties = mapOf("id" to mapOf("type" to "string")),
            required = listOf("id")
        )

        validator.registerSchemas("user-events", listOf(schema))

        // Missing required field
        assertThrows<SchemaValidationException> {
            validator.validateEvent("user-events", "user.created", emptyMap())
        }
    }

    @Test
    fun `should register multiple schemas for same topic`() = runTest {
        val schemas = listOf(
            Schema(eventType = "user.created", properties = mapOf("id" to mapOf("type" to "string"))),
            Schema(eventType = "user.updated", properties = mapOf("id" to mapOf("type" to "string")))
        )

        validator.registerSchemas("user-events", schemas)

        assertTrue(validator.hasSchema("user-events", "user.created"))
        assertTrue(validator.hasSchema("user-events", "user.updated"))
    }

    @Test
    fun `should register schemas for different topics`() = runTest {
        val schema1 = Schema(eventType = "user.created", properties = mapOf("id" to mapOf("type" to "string")))
        val schema2 = Schema(eventType = "order.created", properties = mapOf("id" to mapOf("type" to "string")))

        validator.registerSchemas("user-events", listOf(schema1))
        validator.registerSchemas("order-events", listOf(schema2))

        assertTrue(validator.hasSchema("user-events", "user.created"))
        assertTrue(validator.hasSchema("order-events", "order.created"))
        assertFalse(validator.hasSchema("user-events", "order.created"))
    }

    @Test
    fun `should validate payload with nested objects`() = runTest {
        val schema = Schema(
            eventType = "user.created",
            properties = mapOf(
                "id" to mapOf("type" to "string"),
                "address" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "street" to mapOf("type" to "string"),
                        "city" to mapOf("type" to "string")
                    )
                )
            ),
            required = listOf("id")
        )

        validator.registerSchemas("user-events", listOf(schema))

        val payload = mapOf(
            "id" to "123",
            "address" to mapOf(
                "street" to "123 Main St",
                "city" to "New York"
            )
        )

        validator.validateEvent("user-events", "user.created", payload)
    }
}

