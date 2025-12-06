package com.eventstore.infrastructure.external

import com.eventstore.domain.Schema
import com.eventstore.domain.exceptions.SchemaNotFoundException
import com.eventstore.domain.exceptions.SchemaValidationException
import com.eventstore.domain.ports.outbound.SchemaValidator
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion

class JsonSchemaValidator(private val objectMapper: ObjectMapper = ObjectMapper()) : SchemaValidator {
    private val validators = mutableMapOf<String, com.networknt.schema.JsonSchema>()
    private val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)

    override fun registerSchemas(topic: String, schemas: List<Schema>) {
        schemas.forEach { schema ->
            val key = "${topic}:${schema.eventType}"

            // Convert Schema to JSON Schema format
            val jsonSchema = buildJsonSchema(schema)
            val schemaJson = objectMapper.writeValueAsString(jsonSchema)

            val jsonSchemaObj = factory.getSchema(schemaJson)
            validators[key] = jsonSchemaObj
        }
    }

    override fun validateEvent(topic: String, eventType: String, payload: Map<String, Any>) {
        val key = "${topic}:${eventType}"
        val validator = validators[key]
            ?: throw SchemaNotFoundException(topic, eventType)

        try {
            val payloadJson = objectMapper.writeValueAsString(payload)
            val errors = validator.validate(objectMapper.readTree(payloadJson))

            if (errors.isNotEmpty()) {
                val errorMessages = errors.joinToString(", ") { it.message }
                throw SchemaValidationException(errorMessages, null)
            }
        } catch (e: SchemaValidationException) {
            throw e
        } catch (e: Exception) {
            throw SchemaValidationException("Validation error: ${e.message}", e)
        }
    }

    override fun hasSchema(topic: String, eventType: String): Boolean {
        val key = "${topic}:${eventType}"
        return validators.containsKey(key)
    }

    private fun buildJsonSchema(schema: Schema): Map<String, Any> {
        val jsonSchema = mutableMapOf<String, Any>()
        jsonSchema["type"] = schema.type
        jsonSchema["\$schema"] = schema.schema

        if (schema.properties.isNotEmpty()) {
            jsonSchema["properties"] = schema.properties
        }

        if (schema.required.isNotEmpty()) {
            jsonSchema["required"] = schema.required
        }

        // Add any additional properties
        schema.additionalProperties.forEach { (key, value) ->
            if (key !in jsonSchema) {
                jsonSchema[key] = value
            }
        }

        return jsonSchema
    }
}
