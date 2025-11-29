import { addFormats, addKeywords, Ajv } from "../deps.ts";
import type { ValidateFunction } from "https://esm.sh/ajv@8.17.1";
import type { JSONObject, Schema } from "../types.ts";

// Initialize Ajv with keywords and formats
const ajv = new Ajv({ allErrors: true });
addKeywords(ajv);
addFormats(ajv);

export class SchemaValidator {
  private validators = new Map<string, ValidateFunction<unknown>>();

  /**
   * Register schemas for a topic
   */
  registerSchemas(topic: string, schemas: Schema[]): void {
    schemas.forEach((schema) => {
      const key = `${topic}:${schema.eventType}`;
      // Remove $schema field and ensure type is set to object
      const { $schema, eventType, type: _ignored, ...schemaWithoutMeta } =
        schema;
      const validSchema = {
        type: "object" as const,
        ...schemaWithoutMeta,
      };
      this.validators.set(key, ajv.compile(validSchema));
    });
  }

  /**
   * Validate an event against its topic's schemas
   */
  validateEvent(
    topic: string,
    eventType: string,
    payload: JSONObject,
  ): boolean {
    const key = `${topic}:${eventType}`;
    const validator = this.validators.get(key);

    if (!validator) {
      throw new Error(
        `No schema found for topic '${topic}' and type '${eventType}'`,
      );
    }

    const isValid = validator(payload);

    if (!isValid) {
      const errors = validator.errors?.map((e) =>
        `${e.instancePath} ${e.message}`
      ).join(", ");
      throw new Error(`Validation failed: ${errors}`);
    }

    return true;
  }

  /**
   * Check if a schema exists for a topic and type
   */
  hasSchema(topic: string, eventType: string): boolean {
    const key = `${topic}:${eventType}`;
    return this.validators.has(key);
  }
}
