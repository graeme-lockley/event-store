package com.eventstore.infrastructure.external

import com.eventstore.domain.Schema
import com.eventstore.domain.exceptions.SchemaNotFoundException
import com.eventstore.domain.exceptions.SchemaValidationException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JsonSchemaValidatorTest {

    private val objectMapper = jacksonObjectMapper()
    private lateinit var validator: JsonSchemaValidator

    @BeforeEach
    fun setUp() {
        validator = JsonSchemaValidator(objectMapper)
    }

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
    fun `should throw exception for missing required field`() = runTest {
        val schema = Schema(
            eventType = "user.created",
            properties = mapOf(
                "id" to mapOf("type" to "string"),
                "name" to mapOf("type" to "string")
            ),
            required = listOf("id", "name")
        )

        validator.registerSchemas("user-events", listOf(schema))

        // Missing required field "name"
        assertThrows<SchemaValidationException> {
            validator.validateEvent("user-events", "user.created", mapOf("id" to "123"))
        }
    }

    @Test
    fun `should throw exception for wrong field type`() = runTest {
        val schema = Schema(
            eventType = "user.created",
            properties = mapOf(
                "id" to mapOf("type" to "string"),
                "age" to mapOf("type" to "integer")
            ),
            required = listOf("id")
        )

        validator.registerSchemas("user-events", listOf(schema))

        // age should be integer, not string
        assertThrows<SchemaValidationException> {
            validator.validateEvent("user-events", "user.created", mapOf("id" to "123", "age" to "25"))
        }
    }

    @Test
    fun `should reject extra fields not defined in schema`() = runTest {
        val schema = Schema(
            eventType = "user.created",
            properties = mapOf(
                "id" to mapOf("type" to "string"),
                "name" to mapOf("type" to "string")
            ),
            required = listOf("id", "name")
        )

        validator.registerSchemas("user-events", listOf(schema))

        // Payload contains extra field "email" not in schema
        assertThrows<SchemaValidationException> {
            validator.validateEvent(
                "user-events",
                "user.created",
                mapOf("id" to "123", "name" to "Alice", "email" to "alice@example.com")
            )
        }
    }

    @Test
    fun `should accept payload with only defined fields`() = runTest {
        val schema = Schema(
            eventType = "user.created",
            properties = mapOf(
                "id" to mapOf("type" to "string"),
                "name" to mapOf("type" to "string"),
                "email" to mapOf("type" to "string")
            ),
            required = listOf("id", "name")
        )

        validator.registerSchemas("user-events", listOf(schema))

        // Payload with only defined fields (email is optional)
        validator.validateEvent("user-events", "user.created", mapOf("id" to "123", "name" to "Alice"))
        
        // Payload with all fields
        validator.validateEvent(
            "user-events",
            "user.created",
            mapOf("id" to "123", "name" to "Alice", "email" to "alice@example.com")
        )
    }

    @Test
    fun `should allow additional properties when explicitly enabled`() = runTest {
        val schema = Schema(
            eventType = "user.created",
            properties = mapOf(
                "id" to mapOf("type" to "string"),
                "name" to mapOf("type" to "string")
            ),
            required = listOf("id", "name"),
            additionalProperties = mapOf("additionalProperties" to true)
        )

        validator.registerSchemas("user-events", listOf(schema))

        // Should accept extra fields when additionalProperties is true
        validator.validateEvent(
            "user-events",
            "user.created",
            mapOf("id" to "123", "name" to "Alice", "email" to "alice@example.com")
        )
    }

    @Test
    fun `should validate nested objects`() = runTest {
        val schema = Schema(
            eventType = "user.created",
            properties = mapOf(
                "id" to mapOf("type" to "string"),
                "address" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "street" to mapOf("type" to "string"),
                        "city" to mapOf("type" to "string"),
                        "zipCode" to mapOf("type" to "string")
                    ),
                    "required" to listOf("street", "city"),
                    "additionalProperties" to false
                )
            ),
            required = listOf("id")
        )

        validator.registerSchemas("user-events", listOf(schema))

        // Valid nested object
        val validPayload = mapOf(
            "id" to "123",
            "address" to mapOf(
                "street" to "123 Main St",
                "city" to "New York",
                "zipCode" to "10001"
            )
        )
        validator.validateEvent("user-events", "user.created", validPayload)

        // Nested object missing required field
        val invalidPayload = mapOf(
            "id" to "123",
            "address" to mapOf(
                "street" to "123 Main St"
                // Missing required "city"
            )
        )
        assertThrows<SchemaValidationException> {
            validator.validateEvent("user-events", "user.created", invalidPayload)
        }

        // Nested object with extra field
        val extraFieldPayload = mapOf(
            "id" to "123",
            "address" to mapOf(
                "street" to "123 Main St",
                "city" to "New York",
                "country" to "USA" // Extra field not in schema
            )
        )
        assertThrows<SchemaValidationException> {
            validator.validateEvent("user-events", "user.created", extraFieldPayload)
        }
    }

    @Test
    fun `should validate arrays`() = runTest {
        val schema = Schema(
            eventType = "user.created",
            properties = mapOf(
                "id" to mapOf("type" to "string"),
                "tags" to mapOf(
                    "type" to "array",
                    "items" to mapOf("type" to "string")
                )
            ),
            required = listOf("id")
        )

        validator.registerSchemas("user-events", listOf(schema))

        // Valid array
        validator.validateEvent("user-events", "user.created", mapOf("id" to "123", "tags" to listOf("admin", "user")))

        // Array with wrong item type
        assertThrows<SchemaValidationException> {
            validator.validateEvent("user-events", "user.created", mapOf("id" to "123", "tags" to listOf(1, 2, 3)))
        }
    }

    @Test
    fun `should validate number types`() = runTest {
        val schema = Schema(
            eventType = "user.created",
            properties = mapOf(
                "id" to mapOf("type" to "string"),
                "age" to mapOf("type" to "integer"),
                "score" to mapOf("type" to "number")
            ),
            required = listOf("id")
        )

        validator.registerSchemas("user-events", listOf(schema))

        // Valid numbers
        validator.validateEvent("user-events", "user.created", mapOf("id" to "123", "age" to 25, "score" to 95.5))

        // Wrong type for integer
        assertThrows<SchemaValidationException> {
            validator.validateEvent("user-events", "user.created", mapOf("id" to "123", "age" to "25"))
        }
    }

    @Test
    fun `should validate boolean types`() = runTest {
        val schema = Schema(
            eventType = "user.created",
            properties = mapOf(
                "id" to mapOf("type" to "string"),
                "active" to mapOf("type" to "boolean")
            ),
            required = listOf("id")
        )

        validator.registerSchemas("user-events", listOf(schema))

        // Valid boolean
        validator.validateEvent("user-events", "user.created", mapOf("id" to "123", "active" to true))
        validator.validateEvent("user-events", "user.created", mapOf("id" to "123", "active" to false))

        // Wrong type
        assertThrows<SchemaValidationException> {
            validator.validateEvent("user-events", "user.created", mapOf("id" to "123", "active" to "true"))
        }
    }

    @Test
    fun `should validate string constraints`() = runTest {
        val schema = Schema(
            eventType = "user.created",
            properties = mapOf(
                "id" to mapOf("type" to "string", "minLength" to 3, "maxLength" to 10),
                "email" to mapOf("type" to "string", "format" to "email")
            ),
            required = listOf("id")
        )

        validator.registerSchemas("user-events", listOf(schema))

        // Valid string length
        validator.validateEvent("user-events", "user.created", mapOf("id" to "12345"))

        // Too short
        assertThrows<SchemaValidationException> {
            validator.validateEvent("user-events", "user.created", mapOf("id" to "12"))
        }

        // Too long
        assertThrows<SchemaValidationException> {
            validator.validateEvent("user-events", "user.created", mapOf("id" to "12345678901"))
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
    fun `should handle empty payload when no required fields`() = runTest {
        val schema = Schema(
            eventType = "user.created",
            properties = mapOf(
                "id" to mapOf("type" to "string"),
                "name" to mapOf("type" to "string")
            ),
            required = emptyList()
        )

        validator.registerSchemas("user-events", listOf(schema))

        // Empty payload should be valid if no fields are required
        validator.validateEvent("user-events", "user.created", emptyMap())
    }

    @Test
    fun `should handle null values for optional fields`() = runTest {
        val schema = Schema(
            eventType = "user.created",
            properties = mapOf(
                "id" to mapOf("type" to "string"),
                "name" to mapOf("type" to "string", "nullable" to true)
            ),
            required = listOf("id")
        )

        validator.registerSchemas("user-events", listOf(schema))

        // Null for optional nullable field should be valid
        @Suppress("UNCHECKED_CAST")
        val payload = mapOf("id" to "123", "name" to null) as Map<String, Any>
        validator.validateEvent("user-events", "user.created", payload)
    }

    @Test
    fun `should validate enum constraints`() = runTest {
        val schema = Schema(
            eventType = "user.created",
            properties = mapOf(
                "id" to mapOf("type" to "string"),
                "status" to mapOf("type" to "string", "enum" to listOf("active", "inactive", "pending"))
            ),
            required = listOf("id")
        )

        validator.registerSchemas("user-events", listOf(schema))

        // Valid enum value
        validator.validateEvent("user-events", "user.created", mapOf("id" to "123", "status" to "active"))

        // Invalid enum value
        assertThrows<SchemaValidationException> {
            validator.validateEvent("user-events", "user.created", mapOf("id" to "123", "status" to "unknown"))
        }
    }

    @Test
    fun `should validate number constraints`() = runTest {
        val schema = Schema(
            eventType = "user.created",
            properties = mapOf(
                "id" to mapOf("type" to "string"),
                "age" to mapOf("type" to "integer", "minimum" to 0, "maximum" to 120)
            ),
            required = listOf("id")
        )

        validator.registerSchemas("user-events", listOf(schema))

        // Valid age
        validator.validateEvent("user-events", "user.created", mapOf("id" to "123", "age" to 25))

        // Too low
        assertThrows<SchemaValidationException> {
            validator.validateEvent("user-events", "user.created", mapOf("id" to "123", "age" to -1))
        }

        // Too high
        assertThrows<SchemaValidationException> {
            validator.validateEvent("user-events", "user.created", mapOf("id" to "123", "age" to 150))
        }
    }

    @Test
    fun `should handle complex nested structures`() = runTest {
        val schema = Schema(
            eventType = "order.created",
            properties = mapOf(
                "orderId" to mapOf("type" to "string"),
                "items" to mapOf(
                    "type" to "array",
                    "items" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "productId" to mapOf("type" to "string"),
                            "quantity" to mapOf("type" to "integer", "minimum" to 1),
                            "price" to mapOf("type" to "number", "minimum" to 0.0)
                        ),
                        "required" to listOf("productId", "quantity"),
                        "additionalProperties" to false
                    )
                )
            ),
            required = listOf("orderId")
        )

        validator.registerSchemas("order-events", listOf(schema))

        // Valid complex structure
        val validPayload = mapOf(
            "orderId" to "order-123",
            "items" to listOf(
                mapOf("productId" to "prod-1", "quantity" to 2, "price" to 19.99),
                mapOf("productId" to "prod-2", "quantity" to 1, "price" to 29.99)
            )
        )
        validator.validateEvent("order-events", "order.created", validPayload)

        // Invalid: missing required field in nested object
        val invalidPayload = mapOf(
            "orderId" to "order-123",
            "items" to listOf(
                mapOf("productId" to "prod-1") // Missing quantity
            )
        )
        assertThrows<SchemaValidationException> {
            validator.validateEvent("order-events", "order.created", invalidPayload)
        }

        // Invalid: extra field in nested object
        val extraFieldPayload = mapOf(
            "orderId" to "order-123",
            "items" to listOf(
                mapOf("productId" to "prod-1", "quantity" to 2, "discount" to 0.1) // Extra field
            )
        )
        assertThrows<SchemaValidationException> {
            validator.validateEvent("order-events", "order.created", extraFieldPayload)
        }
    }

    @Test
    fun `should overwrite schema when registering same topic and eventType`() = runTest {
        val schema1 = Schema(
            eventType = "user.created",
            properties = mapOf("id" to mapOf("type" to "string")),
            required = listOf("id")
        )

        val schema2 = Schema(
            eventType = "user.created",
            properties = mapOf(
                "id" to mapOf("type" to "string"),
                "name" to mapOf("type" to "string")
            ),
            required = listOf("id", "name")
        )

        validator.registerSchemas("user-events", listOf(schema1))
        validator.registerSchemas("user-events", listOf(schema2))

        // New schema requires name
        assertThrows<SchemaValidationException> {
            validator.validateEvent("user-events", "user.created", mapOf("id" to "123"))
        }

        // Should validate with new schema
        validator.validateEvent("user-events", "user.created", mapOf("id" to "123", "name" to "Alice"))
    }
}
