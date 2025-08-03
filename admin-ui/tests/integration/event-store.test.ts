import { assertEquals } from "$std/assert/mod.ts";
import { afterAll, beforeAll, describe, it } from "$std/testing/bdd.ts";
import { startIntegrationServers } from "../helpers/integration_server.ts";
import { EventStoreClient } from "../../../event-store/client.ts";

const TEST_TOPIC = {
  name: "test-topic",
  schemas: [
    {
      eventType: "test.event",
      type: "object",
      $schema: "https://json-schema.org/draft/2020-12/schema",
      properties: {
        message: { type: "string" },
      },
      required: ["message"],
    },
  ],
};

const TEST_EVENT = {
  topic: TEST_TOPIC.name,
  type: "test.event",
  payload: {
    message: "Hello from integration test!",
  },
};

describe("Event Store API Integration", () => {
  let servers: Awaited<ReturnType<typeof startIntegrationServers>>;
  let eventStoreUrl: string;
  let eventStoreClient: EventStoreClient;

  beforeAll(async () => {
    servers = await startIntegrationServers();
    eventStoreUrl = servers.eventStore.url;

    // Example: Using EventStoreClient directly in integration tests
    eventStoreClient = new EventStoreClient({
      baseUrl: eventStoreUrl,
      timeout: 5000,
      retries: 3,
      retryDelay: 1000,
    });
  });

  afterAll(async () => {
    await servers.stop();
  });

  describe("Health Check", () => {
    it("should return health status", async () => {
      const response = await fetch(`${eventStoreUrl}/health`);

      assertEquals(response.status, 200);

      const data = await response.json();
      assertEquals(typeof data.status, "string");
    });

    it("should work with EventStoreClient health check", async () => {
      // Example: Using EventStoreClient for health checks
      const health = await eventStoreClient.getHealth();

      assertEquals(typeof health.status, "string");
      assertEquals(typeof health.consumers, "number");
      assertEquals(Array.isArray(health.runningDispatchers), true);
    });
  });

  describe("Topics", () => {
    it("should return empty topics list initially", async () => {
      const response = await fetch(`${eventStoreUrl}/topics`);

      assertEquals(response.status, 200);

      const data = await response.json();
      assertEquals(Array.isArray(data.topics), true);
      assertEquals(data.topics.length, 0);
    });

    it("should create a new topic", async () => {
      const response = await fetch(`${eventStoreUrl}/topics`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(TEST_TOPIC),
      });

      assertEquals(response.status, 201);

      const data = await response.json();
      assertEquals(typeof data.message, "string");
    });

    it("should return the created topic", async () => {
      const response = await fetch(`${eventStoreUrl}/topics`);

      assertEquals(response.status, 200);

      const data = await response.json();
      assertEquals(Array.isArray(data.topics), true);
      assertEquals(data.topics.length, 1);
      assertEquals(data.topics[0], TEST_TOPIC.name);
    });

    it("should work with EventStoreClient topic operations", async () => {
      // Example: Using EventStoreClient for topic operations
      const topics = await eventStoreClient.getTopics();
      assertEquals(Array.isArray(topics), true);
      assertEquals(topics.length >= 1, true); // Should have the topic we created

      const topic = await eventStoreClient.getTopic(TEST_TOPIC.name);
      assertEquals(topic.name, TEST_TOPIC.name);
      assertEquals(Array.isArray(topic.schemas), true);
    });
  });

  describe("Events", () => {
    it("should publish an event", async () => {
      const response = await fetch(`${eventStoreUrl}/events`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify([TEST_EVENT]),
      });

      assertEquals(response.status, 201);

      const data = await response.json();
      assertEquals(Array.isArray(data.eventIds), true);
      assertEquals(data.eventIds.length, 1);
    });

    it("should retrieve events from topic", async () => {
      const response = await fetch(`${eventStoreUrl}/topics/test-topic/events`);

      assertEquals(response.status, 200);

      const data = await response.json();
      assertEquals(Array.isArray(data.events), true);
      assertEquals(data.events.length, 1);
      assertEquals(data.events[0].type, TEST_EVENT.type);
      assertEquals(data.events[0].payload.message, TEST_EVENT.payload.message);
    });

    it("should work with EventStoreClient event operations", async () => {
      // Example: Using EventStoreClient for event operations
      const eventId = await eventStoreClient.publishEvent(
        TEST_TOPIC.name,
        "test.event",
        { message: "Hello from EventStoreClient!" },
      );

      assertEquals(typeof eventId, "string");
      assertEquals(eventId.length > 0, true);

      const events = await eventStoreClient.getEvents(TEST_TOPIC.name);
      assertEquals(Array.isArray(events), true);
      assertEquals(events.length >= 1, true);
    });
  });
});
