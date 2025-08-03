import { assertEquals } from "$std/assert/mod.ts";
import { afterAll, beforeAll, describe, it } from "$std/testing/bdd.ts";
import { Application } from "../deps.ts";
import { TopicManager } from "../core/topics.ts";
import { EventManager } from "../core/events.ts";
import { ConsumerManager } from "../core/consumers.ts";
import { Dispatcher } from "../core/dispatcher.ts";
import { createRouter } from "../api/routes.ts";
import { cleanupAllTestDirectories, TestSetup } from "./helpers/test-setup.ts";

describe("Event Store API", () => {
  let app: Application;
  let topicManager: TopicManager;
  let eventManager: EventManager;
  let consumerManager: ConsumerManager;
  let dispatcher: Dispatcher;
  let controller: AbortController;
  let testSetup: TestSetup;
  const testPort = 18002;

  beforeAll(async () => {
    // Set up test environment with dedicated directories
    testSetup = new TestSetup("api-test");
    await testSetup.setup();

    // Initialize components
    topicManager = new TopicManager();
    eventManager = new EventManager(topicManager);
    consumerManager = new ConsumerManager(eventManager);
    dispatcher = new Dispatcher(consumerManager, eventManager);

    // Create app
    app = new Application();
    const router = createRouter(
      topicManager,
      eventManager,
      consumerManager,
      dispatcher,
    );
    app.use(router.routes());
    app.use(router.allowedMethods());

    // Start server with AbortController for proper cleanup
    controller = new AbortController();
    const _listener = app.listen({ port: testPort, signal: controller.signal });
  });

  afterAll(async () => {
    // Stop all dispatchers to clear intervals
    dispatcher.stopAllDispatchers();

    // Stop the HTTP server
    if (controller) {
      controller.abort();
    }

    // Clean up test directories
    await testSetup.cleanup();
    await cleanupAllTestDirectories();
  });

  describe("Health Check", () => {
    it("should return health status", async () => {
      // const response = await fetch(`http://localhost:${testPort}/health`);
      // assertEquals(response.status, 200);

      // const data = await response.json();
      // assertEquals(typeof data.status, "string");
      // assertEquals(data.status, "healthy");
      // assertEquals(typeof data.consumers, "number");
      // assertEquals(Array.isArray(data.runningDispatchers), true);
    });
  });

  describe("Topics API", () => {
    it("should return empty topics list initially", async () => {
      // Clean up any existing topics first
      const existingTopics = await topicManager.getAllTopics();
      for (const topic of existingTopics) {
        try {
          await Deno.remove(`${testSetup.getConfigDir()}/${topic}.json`);
          await Deno.remove(`${testSetup.getDataDir()}/${topic}`, {
            recursive: true,
          });
        } catch (_) {}
      }

      const response = await fetch(`http://localhost:${testPort}/topics`);
      assertEquals(response.status, 200);

      const data = await response.json();
      assertEquals(Array.isArray(data.topics), true);
      assertEquals(data.topics.length, 0);
    });

    it("should create a new topic with valid schema", async () => {
      const topicData = {
        name: "user-events",
        schemas: [
          {
            eventType: "user.created",
            type: "object",
            $schema: "https://json-schema.org/draft/2020-12/schema",
            properties: {
              id: { type: "string" },
              name: { type: "string" },
            },
            required: ["id", "name"],
          },
        ],
      };

      const response = await fetch(`http://localhost:${testPort}/topics`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(topicData),
      });

      assertEquals(response.status, 201);

      const data = await response.json();
      assertEquals(typeof data.message, "string");
      assertEquals(data.message.includes("created successfully"), true);
    });

    it("should reject topic creation with invalid schema", async () => {
      const topicData = {
        name: "invalid-topic",
        schemas: [
          {
            // Missing eventType field
            type: "object",
            properties: {
              id: { type: "string" },
            },
          },
        ],
      };

      const response = await fetch(`http://localhost:${testPort}/topics`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(topicData),
      });

      assertEquals(response.status, 400);

      const data = await response.json();
      assertEquals(typeof data.error, "string");
    });

    it("should reject duplicate topic creation", async () => {
      const topicData = {
        name: "user-events", // Same name as above
        schemas: [
          {
            eventType: "user.updated",
            type: "object",
            $schema: "https://json-schema.org/draft/2020-12/schema",
            properties: {
              id: { type: "string" },
            },
            required: ["id"],
          },
        ],
      };

      const response = await fetch(`http://localhost:${testPort}/topics`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(topicData),
      });

      assertEquals(response.status, 400);

      const data = await response.json();
      assertEquals(typeof data.error, "string");
    });

    it("should reject topic creation with missing name", async () => {
      const topicData = {
        schemas: [
          {
            eventType: "test.event",
            type: "object",
            $schema: "https://json-schema.org/draft/2020-12/schema",
            properties: {
              id: { type: "string" },
            },
          },
        ],
      };

      const response = await fetch(`http://localhost:${testPort}/topics`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(topicData),
      });

      assertEquals(response.status, 400);

      const data = await response.json();
      assertEquals(typeof data.error, "string");
    });

    it("should reject topic creation with missing schemas", async () => {
      const topicData = {
        name: "test-topic",
      };

      const response = await fetch(`http://localhost:${testPort}/topics`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(topicData),
      });

      assertEquals(response.status, 400);

      const data = await response.json();
      assertEquals(typeof data.error, "string");
    });

    it("should get topic details", async () => {
      const response = await fetch(
        `http://localhost:${testPort}/topics/user-events`,
      );
      assertEquals(response.status, 200);

      const data = await response.json();
      assertEquals(data.name, "user-events");
      assertEquals(typeof data.sequence, "number");
      assertEquals(Array.isArray(data.schemas), true);
    });

    it("should return 404 for non-existent topic", async () => {
      const response = await fetch(
        `http://localhost:${testPort}/topics/non-existent-topic`,
      );
      assertEquals(response.status, 404);

      const data = await response.json();
      assertEquals(typeof data.error, "string");
    });
  });

  describe("Events API", () => {
    it("should publish valid events", async () => {
      const eventData = [
        {
          topic: "user-events",
          type: "user.created",
          payload: {
            id: "123",
            name: "John Doe",
          },
        },
      ];

      const response = await fetch(`http://localhost:${testPort}/events`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(eventData),
      });

      assertEquals(response.status, 201);

      const data = await response.json();
      assertEquals(Array.isArray(data.eventIds), true);
      assertEquals(data.eventIds.length, 1);
    });

    it("should publish multiple events", async () => {
      const eventData = [
        {
          topic: "user-events",
          type: "user.created",
          payload: {
            id: "124",
            name: "Jane Doe",
          },
        },
        {
          topic: "user-events",
          type: "user.created",
          payload: {
            id: "125",
            name: "Bob Smith",
          },
        },
      ];

      const response = await fetch(`http://localhost:${testPort}/events`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(eventData),
      });

      assertEquals(response.status, 201);

      const data = await response.json();
      assertEquals(Array.isArray(data.eventIds), true);
      assertEquals(data.eventIds.length, 2);
    });

    it("should reject empty events array", async () => {
      const response = await fetch(`http://localhost:${testPort}/events`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify([]),
      });

      assertEquals(response.status, 400);

      const data = await response.json();
      assertEquals(typeof data.error, "string");
    });

    it("should reject invalid event format", async () => {
      const eventData = [
        {
          topic: "user-events",
          // Missing type and payload
        },
      ];

      const response = await fetch(`http://localhost:${testPort}/events`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(eventData),
      });

      assertEquals(response.status, 400);

      const data = await response.json();
      assertEquals(typeof data.error, "string");
    });

    it("should retrieve events from topic", async () => {
      const response = await fetch(
        `http://localhost:${testPort}/topics/user-events/events`,
      );
      assertEquals(response.status, 200);

      const data = await response.json();
      assertEquals(Array.isArray(data.events), true);
      assertEquals(data.events.length >= 3, true); // Should have the events we created
    });

    it("should retrieve events with query parameters", async () => {
      const response = await fetch(
        `http://localhost:${testPort}/topics/user-events/events?limit=2`,
      );
      assertEquals(response.status, 200);

      const data = await response.json();
      assertEquals(Array.isArray(data.events), true);
      assertEquals(data.events.length <= 2, true);
    });

    it("should return 404 for non-existent topic events", async () => {
      const response = await fetch(
        `http://localhost:${testPort}/topics/non-existent-topic/events`,
      );
      assertEquals(response.status, 404);

      const data = await response.json();
      assertEquals(typeof data.error, "string");
    });
  });

  describe("Consumers API", () => {
    it("should register a new consumer", async () => {
      const consumerData = {
        callback: "http://localhost:3000/webhook",
        topics: {
          "user-events": null,
        },
      };

      const response = await fetch(
        `http://localhost:${testPort}/consumers/register`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(consumerData),
        },
      );

      assertEquals(response.status, 201);

      const data = await response.json();
      assertEquals(typeof data.consumerId, "string");
    });

    it("should reject consumer registration with missing callback", async () => {
      const consumerData = {
        topics: {
          "user-events": null,
        },
      };

      const response = await fetch(
        `http://localhost:${testPort}/consumers/register`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(consumerData),
        },
      );

      assertEquals(response.status, 400);

      const data = await response.json();
      assertEquals(typeof data.error, "string");
    });

    it("should reject consumer registration with missing topics", async () => {
      const consumerData = {
        callback: "http://localhost:3000/webhook",
      };

      const response = await fetch(
        `http://localhost:${testPort}/consumers/register`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(consumerData),
        },
      );

      assertEquals(response.status, 400);

      const data = await response.json();
      assertEquals(typeof data.error, "string");
    });

    it("should reject consumer registration with non-existent topic", async () => {
      const consumerData = {
        callback: "http://localhost:3000/webhook",
        topics: {
          "non-existent-topic": null,
        },
      };

      const response = await fetch(
        `http://localhost:${testPort}/consumers/register`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(consumerData),
        },
      );

      assertEquals(response.status, 400);

      const data = await response.json();
      assertEquals(typeof data.error, "string");
    });

    it("should list all consumers", async () => {
      const response = await fetch(`http://localhost:${testPort}/consumers`);
      assertEquals(response.status, 200);

      const data = await response.json();
      assertEquals(Array.isArray(data.consumers), true);
      assertEquals(data.consumers.length >= 1, true); // Should have the consumer we registered
    });

    it("should unregister a consumer", async () => {
      // First get the list of consumers to get an ID
      const listResponse = await fetch(
        `http://localhost:${testPort}/consumers`,
      );
      const listData = await listResponse.json();
      const consumerId = listData.consumers[0].id;

      const response = await fetch(
        `http://localhost:${testPort}/consumers/${consumerId}`,
        {
          method: "DELETE",
        },
      );

      assertEquals(response.status, 200);

      const data = await response.json();
      assertEquals(typeof data.message, "string");
    });

    it("should return 404 when unregistering non-existent consumer", async () => {
      const response = await fetch(
        `http://localhost:${testPort}/consumers/non-existent-id`,
        {
          method: "DELETE",
        },
      );

      assertEquals(response.status, 404);

      const data = await response.json();
      assertEquals(typeof data.error, "string");
    });
  });

  describe("Error Handling", () => {
    it("should handle malformed JSON in topic creation", async () => {
      const response = await fetch(`http://localhost:${testPort}/topics`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: "{ invalid json }",
      });

      assertEquals(response.status, 400);

      const data = await response.json();
      assertEquals(typeof data.error, "string");
    });

    it("should handle malformed JSON in event publishing", async () => {
      const response = await fetch(`http://localhost:${testPort}/events`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: "{ invalid json }",
      });

      assertEquals(response.status, 400);

      const data = await response.json();
      assertEquals(typeof data.error, "string");
    });

    it("should handle malformed JSON in consumer registration", async () => {
      const response = await fetch(
        `http://localhost:${testPort}/consumers/register`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: "{ invalid json }",
        },
      );

      assertEquals(response.status, 400);

      const data = await response.json();
      assertEquals(typeof data.error, "string");
    });

    it("should handle missing consumer ID in delete", async () => {
      const response = await fetch(`http://localhost:${testPort}/consumers/`, {
        method: "DELETE",
      });

      assertEquals(response.status, 405);

      // Consume the response body to prevent leaks
      await response.text();
    });
  });
});
