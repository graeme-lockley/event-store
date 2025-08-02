import { addKeywords, Ajv } from "../deps.ts";

// Initialize Ajv with keywords
const ajv = new Ajv({ allErrors: true });
addKeywords(ajv);

export class SchemaValidator {
  private validators = new Map<string, any>();

  /**
   * Register schemas for a topic
   */
  registerSchemas(topic: string, schemas: any[]): void {
    schemas.forEach((schema) => {
      const key = `${topic}:${schema.eventType}`;
      // Remove $schema field and ensure type is set to object
      const { $schema, eventType, ...schemaWithoutMeta } = schema;
      const validSchema = {
        type: "object",
        ...schemaWithoutMeta,
      };
      this.validators.set(key, ajv.compile(validSchema));
    });
  }

  /**
   * Validate an event against its topic's schemas
   */
  validateEvent(topic: string, eventType: string, payload: any): boolean {
    const key = `${topic}:${eventType}`;
    const validator = this.validators.get(key);

    if (!validator) {
      throw new Error(
        `No schema found for topic '${topic}' and type '${eventType}'`,
      );
    }

    const isValid = validator(payload);

    if (!isValid) {
      const errors = validator.errors?.map((e: any) =>
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
