package com.eventstore.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class SchemaTest {
    
    @Test
    fun `should create valid schema with defaults`() {
        val schema = Schema(eventType = "user.created")
        
        assertEquals("user.created", schema.eventType)
        assertEquals("object", schema.type)
        assertEquals("https://json-schema.org/draft/2020-12/schema", schema.schema)
        assertEquals(emptyMap<String, Any>(), schema.properties)
        assertEquals(emptyList<String>(), schema.required)
    }
    
    @Test
    fun `should create schema with all properties`() {
        val properties = mapOf(
            "id" to mapOf("type" to "string"),
            "name" to mapOf("type" to "string")
        )
        val required = listOf("id", "name")
        val additional = mapOf("description" to "User creation event")
        
        val schema = Schema(
            eventType = "user.created",
            type = "object",
            schema = "https://json-schema.org/draft/2020-12/schema",
            properties = properties,
            required = required,
            additionalProperties = additional
        )
        
        assertEquals("user.created", schema.eventType)
        assertEquals(properties, schema.properties)
        assertEquals(required, schema.required)
        assertEquals(additional, schema.additionalProperties)
    }
    
    @Test
    fun `should throw exception for blank eventType`() {
        assertThrows<IllegalArgumentException> {
            Schema(eventType = "")
        }
    }
    
    @Test
    fun `should throw exception for blank schema`() {
        assertThrows<IllegalArgumentException> {
            Schema(eventType = "user.created", schema = "")
        }
    }
    
    @Test
    fun `should accept custom schema URL`() {
        val schema = Schema(
            eventType = "user.created",
            schema = "https://json-schema.org/draft/2019-09/schema"
        )
        
        assertEquals("https://json-schema.org/draft/2019-09/schema", schema.schema)
    }
}

