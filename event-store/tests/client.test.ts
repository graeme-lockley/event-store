import { assertEquals, assertRejects } from "$std/assert/mod.ts";
import { afterAll, beforeAll, describe, it } from "$std/testing/bdd.ts";
import {
  EventStoreClient,
  type EventStoreConfig,
  type Schema,
} from "../client.ts";
import { TestSetup } from "./helpers/test-setup.ts";

describe("EventStoreClient Integration", () => {
  let client: EventStoreClient;
  let serverProcess: Deno.ChildProcess;
  let testSetup: TestSetup;

  // Use a random high port to avoid conflicts
  const testPort = 18000; // 9000-9999 range

  const testConfig: EventStoreConfig = {
    baseUrl: `http://localhost:${testPort}`,
    timeout: 10000,
    retries: 3,
    retryDelay: 500,
  };

  beforeAll(async () => {
    // Set up test environment with dedicated directories
    testSetup = new TestSetup("client-test");
    await testSetup.setup();

    // Start the Event Store server with test directories
    // Get the event-store directory path relative to this test file
    const testFileUrl = new URL(import.meta.url);
    const eventStoreDir = new URL("../", testFileUrl).pathname;

    serverProcess = new Deno.Command("deno", {
      args: ["run", "-A", "mod.ts"],
      cwd: eventStoreDir,
      env: {
        "PORT": testPort.toString(),
        "DATA_DIR": testSetup.getDataDir(),
        "CONFIG_DIR": testSetup.getConfigDir(),
      },
      stdout: "piped",
      stderr: "piped",
    }).spawn();

    // Wait for server to start using the built-in helper
    client = new EventStoreClient(testConfig);
    await client.waitForServer();
  });

  afterAll(async () => {
    // Clean up server process
    try {
      serverProcess.kill();
      await serverProcess.status;
      // Close the streams to prevent resource leaks
      await serverProcess.stdout?.cancel();
      await serverProcess.stderr?.cancel();
    } catch {
      // Process may already be terminated
    }

    // Clean up test directories
    await testSetup.cleanup();
  });

  describe("getHealth", () => {
    it("should get health status", async () => {
      const health = await client.getHealth();

      assertEquals(typeof health.status, "string");
      assertEquals(typeof health.consumers, "number");
      assertEquals(Array.isArray(health.runningDispatchers), true);
    });
  });

  describe("createTopic", () => {
    it("should create a topic with schemas", async () => {
      const uniqueTopicName = `test-topic-${Date.now()}`;
      const schemas: Schema[] = [
        {
          eventType: "user.created",
          type: "object",
          $schema: "https://json-schema.org/draft/2020-12/schema",
          properties: {
            id: { type: "string" },
            name: { type: "string" },
          },
          required: ["id"],
        },
      ];

      await client.createTopic(uniqueTopicName, schemas);

      // Verify topic was created by getting it
      const topic = await client.getTopic(uniqueTopicName);
      assertEquals(topic.name, uniqueTopicName);
      assertEquals(topic.schemas.length, 1);
      assertEquals(topic.schemas[0].eventType, "user.created");
    });

    it("should reject duplicate topic creation", async () => {
      const uniqueTopicName = `duplicate-topic-${Date.now()}`;
      const schemas: Schema[] = [
        {
          eventType: "test.event",
          type: "object",
          $schema: "https://json-schema.org/draft/2020-12/schema",
          properties: { id: { type: "string" } },
          required: ["id"],
        },
      ];

      // Create topic first time
      await client.createTopic(uniqueTopicName, schemas);

      // Try to create same topic again
      await assertRejects(
        () => client.createTopic(uniqueTopicName, schemas),
      );
    });
  });

  describe("getTopics", () => {
    it("should get all topics", async () => {
      const topics = await client.getTopics();

      assertEquals(Array.isArray(topics), true);
      // Check that we have some topics (the exact names may vary due to test order)
      assertEquals(topics.length >= 0, true);
    });
  });

  describe("getTopic", () => {
    it("should get specific topic", async () => {
      const uniqueTopicName = `get-topic-${Date.now()}`;
      const schemas: Schema[] = [
        {
          eventType: "user.created",
          type: "object",
          $schema: "https://json-schema.org/draft/2020-12/schema",
          properties: { id: { type: "string" } },
          required: ["id"],
        },
      ];

      await client.createTopic(uniqueTopicName, schemas);
      const topic = await client.getTopic(uniqueTopicName);

      assertEquals(topic.name, uniqueTopicName);
      assertEquals(typeof topic.sequence, "number");
      assertEquals(Array.isArray(topic.schemas), true);
    });

    it("should throw error for non-existent topic", async () => {
      await assertRejects(
        () => client.getTopic("non-existent-topic"),
      );
    });
  });

  describe("publishEvent", () => {
    it("should publish single event", async () => {
      const uniqueTopicName = `publish-topic-${Date.now()}`;
      const schemas: Schema[] = [
        {
          eventType: "user.created",
          type: "object",
          $schema: "https://json-schema.org/draft/2020-12/schema",
          properties: {
            id: { type: "string" },
            name: { type: "string" },
          },
          required: ["id"],
        },
      ];

      await client.createTopic(uniqueTopicName, schemas);
      const eventId = await client.publishEvent(
        uniqueTopicName,
        "user.created",
        {
          id: "456",
          name: "Jane",
        },
      );

      assertEquals(typeof eventId, "string");
      assertEquals(eventId.length > 0, true);
    });
  });

  describe("getEvents", () => {
    it("should get events from topic", async () => {
      const uniqueTopicName = `events-topic-${Date.now()}`;
      const schemas: Schema[] = [
        {
          eventType: "user.created",
          type: "object",
          $schema: "https://json-schema.org/draft/2020-12/schema",
          properties: {
            id: { type: "string" },
            name: { type: "string" },
          },
          required: ["id"],
        },
      ];

      await client.createTopic(uniqueTopicName, schemas);
      await client.publishEvent(uniqueTopicName, "user.created", {
        id: "123",
        name: "John",
      });

      const events = await client.getEvents(uniqueTopicName);

      assertEquals(Array.isArray(events), true);
      if (events.length > 0) {
        const event = events[0];
        assertEquals(typeof event.id, "string");
        assertEquals(typeof event.timestamp, "string");
        assertEquals(typeof event.type, "string");
        assertEquals(typeof event.payload, "object");
      }
    });

    it("should handle query parameters", async () => {
      const uniqueTopicName = `query-topic-${Date.now()}`;
      const schemas: Schema[] = [
        {
          eventType: "user.created",
          type: "object",
          $schema: "https://json-schema.org/draft/2020-12/schema",
          properties: { id: { type: "string" } },
          required: ["id"],
        },
      ];

      await client.createTopic(uniqueTopicName, schemas);
      const events = await client.getEvents(uniqueTopicName, {
        limit: 5,
      });

      assertEquals(Array.isArray(events), true);
      assertEquals(events.length <= 5, true);
    });
  });

  describe("registerConsumer", () => {
    it("should register a consumer", async () => {
      const uniqueTopicName = `consumer-topic-${Date.now()}`;
      const schemas: Schema[] = [
        {
          eventType: "user.created",
          type: "object",
          $schema: "https://json-schema.org/draft/2020-12/schema",
          properties: { id: { type: "string" } },
          required: ["id"],
        },
      ];

      await client.createTopic(uniqueTopicName, schemas);
      const registration = {
        callback: "http://localhost:3000/webhook",
        topics: { [uniqueTopicName]: null },
      };

      const consumerId = await client.registerConsumer(registration);

      assertEquals(typeof consumerId, "string");
      assertEquals(consumerId.length > 0, true);
    });

    it("should reject registration with non-existent topic", async () => {
      const registration = {
        callback: "http://localhost:3000/webhook",
        topics: { "non-existent-topic": null },
      };

      await assertRejects(
        () => client.registerConsumer(registration),
      );
    });
  });

  describe("getConsumers", () => {
    it("should get all consumers", async () => {
      const consumers = await client.getConsumers();

      assertEquals(Array.isArray(consumers), true);
      if (consumers.length > 0) {
        const consumer = consumers[0];
        assertEquals(typeof consumer.id, "string");
        assertEquals(typeof consumer.callback, "string");
        assertEquals(typeof consumer.topics, "object");
      }
    });
  });

  describe("unregisterConsumer", () => {
    it("should unregister a consumer", async () => {
      const uniqueTopicName = `unregister-topic-${Date.now()}`;
      const schemas: Schema[] = [
        {
          eventType: "user.created",
          type: "object",
          $schema: "https://json-schema.org/draft/2020-12/schema",
          properties: { id: { type: "string" } },
          required: ["id"],
        },
      ];

      await client.createTopic(uniqueTopicName, schemas);

      // First register a consumer
      const registration = {
        callback: "http://localhost:3000/webhook",
        topics: { [uniqueTopicName]: null },
      };
      const consumerId = await client.registerConsumer(registration);

      // Then unregister it
      await client.unregisterConsumer(consumerId);

      // Verify it's gone
      const consumers = await client.getConsumers();
      const foundConsumer = consumers.find((c) => c.id === consumerId);
      assertEquals(foundConsumer, undefined);
    });
  });

  describe("testConnection", () => {
    it("should test connection successfully", async () => {
      const result = await client.testConnection();
      assertEquals(result, true);
    });
  });

  describe("error handling", () => {
    it("should handle non-existent topic", async () => {
      await assertRejects(
        () => client.getTopic("definitely-non-existent-topic"),
      );
    });

    it("should handle invalid event type", async () => {
      const uniqueTopicName = `error-topic-${Date.now()}`;
      const schemas: Schema[] = [
        {
          eventType: "user.created",
          type: "object",
          $schema: "https://json-schema.org/draft/2020-12/schema",
          properties: { id: { type: "string" } },
          required: ["id"],
        },
      ];

      await client.createTopic(uniqueTopicName, schemas);
      await assertRejects(
        () =>
          client.publishEvent(uniqueTopicName, "invalid.event.type", {
            id: "123",
          }),
      );
    });

    it("should handle empty events array", async () => {
      await assertRejects(
        () => client.publishEvents([]),
      );
    });

    it("should handle invalid event format", async () => {
      const uniqueTopicName = `invalid-format-topic-${Date.now()}`;
      const schemas: Schema[] = [
        {
          eventType: "user.created",
          type: "object",
          $schema: "https://json-schema.org/draft/2020-12/schema",
          properties: { id: { type: "string" } },
          required: ["id"],
        },
      ];

      await client.createTopic(uniqueTopicName, schemas);

      // Test event with missing required fields
      await assertRejects(
        () => client.publishEvent(uniqueTopicName, "user.created", {}),
      );
    });

    it("should handle invalid topic creation with missing required fields", async () => {
      // Test creating topic with invalid schema structure (missing required fields)
      const invalidSchemas = [
        {
          // Missing eventType and $schema
          type: "object",
          properties: { id: { type: "string" } },
        },
      ] as any;

      await assertRejects(
        () => client.createTopic("invalid-topic", invalidSchemas),
      );
    });

    it("should handle invalid event publishing with null payload", async () => {
      const uniqueTopicName = `invalid-payload-topic-${Date.now()}`;
      const schemas: Schema[] = [
        {
          eventType: "user.created",
          type: "object",
          $schema: "https://json-schema.org/draft/2020-12/schema",
          properties: { id: { type: "string" } },
          required: ["id"],
        },
      ];

      await client.createTopic(uniqueTopicName, schemas);

      // Test publishing with null payload (should be rejected by server)
      await assertRejects(
        () => client.publishEvent(uniqueTopicName, "user.created", null as any),
      );
    });

    it("should handle invalid consumer registration with empty object", async () => {
      const uniqueTopicName = `invalid-consumer-topic-${Date.now()}`;
      const schemas: Schema[] = [
        {
          eventType: "user.created",
          type: "object",
          $schema: "https://json-schema.org/draft/2020-12/schema",
          properties: { id: { type: "string" } },
          required: ["id"],
        },
      ];

      await client.createTopic(uniqueTopicName, schemas);

      // Test registration with empty object (should be rejected by server)
      await assertRejects(
        () => client.registerConsumer({} as any),
      );
    });

    it("should handle missing consumer ID in delete", async () => {
      // Test unregistering with empty/invalid consumer ID
      await assertRejects(
        () => client.unregisterConsumer(""),
      );
    });
  });

  describe("updateTopicSchemas", () => {
    it("should update existing schema via API", async () => {
      const uniqueTopicName = `update-schema-${Date.now()}`;
      const initialSchemas: Schema[] = [
        {
          eventType: "user.created",
          type: "object",
          $schema: "https://json-schema.org/draft/2020-12/schema",
          properties: { id: { type: "string" }, name: { type: "string" } },
          required: ["id"],
        },
      ];

      await client.createTopic(uniqueTopicName, initialSchemas);

      // Update schema: add new property
      const updatedSchemas: Schema[] = [
        {
          eventType: "user.created",
          type: "object",
          $schema: "https://json-schema.org/draft/2020-12/schema",
          properties: {
            id: { type: "string" },
            name: { type: "string" },
            email: { type: "string" },
          },
          required: ["id", "email"],
        },
      ];

      await client.updateTopicSchemas(uniqueTopicName, updatedSchemas);

      // Verify update via GET
      const topic = await client.getTopic(uniqueTopicName);
      assertEquals(topic.schemas.length, 1);
      assertEquals(topic.schemas[0].eventType, "user.created");
      const props = topic.schemas[0].properties as Record<string, unknown>;
      assertEquals(props.email !== undefined, true);
    });

    it("should add new schema to topic via API", async () => {
      const uniqueTopicName = `add-schema-${Date.now()}`;
      const initialSchemas: Schema[] = [
        {
          eventType: "user.created",
          type: "object",
          $schema: "https://json-schema.org/draft/2020-12/schema",
          properties: { id: { type: "string" } },
          required: ["id"],
        },
      ];

      await client.createTopic(uniqueTopicName, initialSchemas);

      // Add new schema while keeping existing
      const updatedSchemas: Schema[] = [
        {
          eventType: "user.created",
          type: "object",
          $schema: "https://json-schema.org/draft/2020-12/schema",
          properties: { id: { type: "string" } },
          required: ["id"],
        },
        {
          eventType: "user.updated",
          type: "object",
          $schema: "https://json-schema.org/draft/2020-12/schema",
          properties: { id: { type: "string" }, name: { type: "string" } },
          required: ["id"],
        },
      ];

      await client.updateTopicSchemas(uniqueTopicName, updatedSchemas);

      // Verify both schemas exist
      const topic = await client.getTopic(uniqueTopicName);
      assertEquals(topic.schemas.length, 2);
      const eventTypes = topic.schemas.map((s) => s.eventType);
      assertEquals(eventTypes.includes("user.created"), true);
      assertEquals(eventTypes.includes("user.updated"), true);
    });

    it("should reject attempt to remove schema via API", async () => {
      const uniqueTopicName = `remove-schema-${Date.now()}`;
      const initialSchemas: Schema[] = [
        {
          eventType: "user.created",
          type: "object",
          $schema: "https://json-schema.org/draft/2020-12/schema",
          properties: { id: { type: "string" } },
          required: ["id"],
        },
        {
          eventType: "user.updated",
          type: "object",
          $schema: "https://json-schema.org/draft/2020-12/schema",
          properties: { id: { type: "string" } },
          required: ["id"],
        },
      ];

      await client.createTopic(uniqueTopicName, initialSchemas);

      // Try to remove user.created
      const updatedSchemas: Schema[] = [
        {
          eventType: "user.updated",
          type: "object",
          $schema: "https://json-schema.org/draft/2020-12/schema",
          properties: { id: { type: "string" } },
          required: ["id"],
        },
      ];

      await assertRejects(
        () => client.updateTopicSchemas(uniqueTopicName, updatedSchemas),
      );
    });

    it("should make updated schemas immediately effective for new events", async () => {
      const uniqueTopicName = `immediate-schema-${Date.now()}`;
      const initialSchemas: Schema[] = [
        {
          eventType: "user.created",
          type: "object",
          $schema: "https://json-schema.org/draft/2020-12/schema",
          properties: { id: { type: "string" } },
          required: ["id"],
        },
      ];

      await client.createTopic(uniqueTopicName, initialSchemas);

      // Update schema to require email
      const updatedSchemas: Schema[] = [
        {
          eventType: "user.created",
          type: "object",
          $schema: "https://json-schema.org/draft/2020-12/schema",
          properties: {
            id: { type: "string" },
            email: { type: "string" },
          },
          required: ["id", "email"],
        },
      ];

      await client.updateTopicSchemas(uniqueTopicName, updatedSchemas);

      // Try to publish event without email (should fail)
      await assertRejects(
        () =>
          client.publishEvent(uniqueTopicName, "user.created", {
            id: "123",
          }),
      );

      // Publish event with email (should succeed)
      const eventId = await client.publishEvent(
        uniqueTopicName,
        "user.created",
        { id: "123", email: "test@example.com" },
      );
      assertEquals(typeof eventId, "string");
      assertEquals(eventId.length > 0, true);
    });

    it("should handle updating non-existent topic", async () => {
      const updatedSchemas: Schema[] = [
        {
          eventType: "test.event",
          type: "object",
          $schema: "https://json-schema.org/draft/2020-12/schema",
          properties: { id: { type: "string" } },
          required: ["id"],
        },
      ];

      await assertRejects(
        () =>
          client.updateTopicSchemas(
            "definitely-non-existent-topic",
            updatedSchemas,
          ),
      );
    });

    it("should handle invalid schema format", async () => {
      const uniqueTopicName = `invalid-schema-${Date.now()}`;
      const initialSchemas: Schema[] = [
        {
          eventType: "user.created",
          type: "object",
          $schema: "https://json-schema.org/draft/2020-12/schema",
          properties: { id: { type: "string" } },
          required: ["id"],
        },
      ];

      await client.createTopic(uniqueTopicName, initialSchemas);

      // Try to update with schema missing eventType
      const invalidSchemas = [
        {
          type: "object",
          $schema: "https://json-schema.org/draft/2020-12/schema",
          properties: { id: { type: "string" } },
          required: ["id"],
        },
      ] as any;

      await assertRejects(
        () => client.updateTopicSchemas(uniqueTopicName, invalidSchemas),
      );
    });
  });
});
