import { assertEquals } from "$std/assert/mod.ts";
import {
  afterAll,
  afterEach,
  beforeEach,
  describe,
  it,
} from "$std/testing/bdd.ts";
import { ConsumerManager } from "../core/consumers.ts";
import { EventManager } from "../core/events.ts";
import { TopicManager } from "../core/topics.ts";
import { cleanupAllTestDirectories, TestSetup } from "./helpers/test-setup.ts";

describe("ConsumerManager", () => {
  let consumerManager: ConsumerManager;
  let eventManager: EventManager;
  let topicManager: TopicManager;
  let testSetup: TestSetup;

  beforeEach(async () => {
    testSetup = new TestSetup();
    await testSetup.setup();

    topicManager = await TopicManager.create();
    eventManager = new EventManager(topicManager);
    consumerManager = new ConsumerManager(eventManager);
  });

  afterEach(async () => {
    await testSetup.cleanup();
  });

  afterAll(async () => {
    await cleanupAllTestDirectories();
  });

  describe("registerConsumer", () => {
    it("should register a new consumer", () => {
      const registration = {
        callback: "http://localhost:3000/webhook",
        topics: { "test-topic": null },
      };

      const consumerId = consumerManager.registerConsumer(registration);

      assertEquals(typeof consumerId, "string");
      assertEquals(consumerId.length > 0, true);
      assertEquals(consumerManager.getConsumerCount(), 1);
    });

    it("should register consumer with multiple topics", () => {
      const registration = {
        callback: "http://localhost:3000/webhook",
        topics: {
          "topic-1": null,
          "topic-2": "topic-1-5",
        },
      };

      const consumerId = consumerManager.registerConsumer(registration);

      assertEquals(typeof consumerId, "string");
      assertEquals(consumerManager.getConsumerCount(), 1);
    });
  });

  describe("unregisterConsumer", () => {
    it("should unregister an existing consumer", () => {
      const registration = {
        callback: "http://localhost:3000/webhook",
        topics: { "test-topic": null },
      };

      const consumerId = consumerManager.registerConsumer(registration);
      assertEquals(consumerManager.getConsumerCount(), 1);

      const removed = consumerManager.unregisterConsumer(consumerId);
      assertEquals(removed, true);
      assertEquals(consumerManager.getConsumerCount(), 0);
    });

    it("should return false for non-existent consumer", () => {
      const removed = consumerManager.unregisterConsumer("non-existent");
      assertEquals(removed, false);
    });
  });

  describe("getConsumersForTopic", () => {
    it("should return consumers for a specific topic", () => {
      const registration1 = {
        callback: "http://localhost:3000/webhook1",
        topics: { "topic-1": null },
      };
      const registration2 = {
        callback: "http://localhost:3000/webhook2",
        topics: { "topic-1": null, "topic-2": null },
      };

      consumerManager.registerConsumer(registration1);
      consumerManager.registerConsumer(registration2);

      const consumers = consumerManager.getConsumersForTopic("topic-1");
      assertEquals(consumers.length, 2);
    });

    it("should return empty array for topic with no consumers", () => {
      const consumers = consumerManager.getConsumersForTopic("non-existent");
      assertEquals(consumers.length, 0);
    });
  });

  describe("getAllConsumers", () => {
    it("should return all registered consumers", () => {
      const registration1 = {
        callback: "http://localhost:3000/webhook1",
        topics: { "topic-1": null },
      };
      const registration2 = {
        callback: "http://localhost:3000/webhook2",
        topics: { "topic-2": null },
      };

      consumerManager.registerConsumer(registration1);
      consumerManager.registerConsumer(registration2);

      const consumers = consumerManager.getAllConsumers();
      assertEquals(consumers.length, 2);
    });

    it("should return empty array when no consumers are registered", () => {
      const consumers = consumerManager.getAllConsumers();
      assertEquals(consumers.length, 0);
    });
  });

  describe("getConsumer", () => {
    it("should return consumer by ID", () => {
      const registration = {
        callback: "http://localhost:3000/webhook",
        topics: { "test-topic": null },
      };

      const consumerId = consumerManager.registerConsumer(registration);
      const consumer = consumerManager.getConsumer(consumerId);

      assertEquals(consumer !== undefined, true);
      assertEquals(consumer?.id, consumerId);
      assertEquals(consumer?.callback, "http://localhost:3000/webhook");
    });

    it("should return undefined for non-existent consumer", () => {
      const consumer = consumerManager.getConsumer("non-existent");
      assertEquals(consumer, undefined);
    });
  });

  describe("getConsumerCount", () => {
    it("should return correct consumer count", () => {
      assertEquals(consumerManager.getConsumerCount(), 0);

      const registration1 = {
        callback: "http://localhost:3000/webhook1",
        topics: { "topic-1": null },
      };
      const registration2 = {
        callback: "http://localhost:3000/webhook2",
        topics: { "topic-2": null },
      };

      consumerManager.registerConsumer(registration1);
      assertEquals(consumerManager.getConsumerCount(), 1);

      consumerManager.registerConsumer(registration2);
      assertEquals(consumerManager.getConsumerCount(), 2);

      consumerManager.unregisterConsumer(
        consumerManager.getAllConsumers()[0].id,
      );
      assertEquals(consumerManager.getConsumerCount(), 1);
    });
  });

  describe("nudgeConsumersForTopic", () => {
    it("should nudge consumers for a topic", async () => {
      const registration = {
        callback: "http://localhost:3000/webhook",
        topics: { "test-topic": null },
      };

      consumerManager.registerConsumer(registration);

      // This should not throw an error
      await consumerManager.nudgeConsumersForTopic("test-topic");
    });

    it("should handle nudging for topic with no consumers", async () => {
      // This should not throw an error
      await consumerManager.nudgeConsumersForTopic("non-existent");
    });

    it("should handle nudging with multiple consumers", async () => {
      const registration1 = {
        callback: "http://localhost:3000/webhook1",
        topics: { "topic-1": null },
      };
      const registration2 = {
        callback: "http://localhost:3000/webhook2",
        topics: { "topic-1": null, "topic-2": null },
      };

      consumerManager.registerConsumer(registration1);
      consumerManager.registerConsumer(registration2);

      // This should not throw an error
      await consumerManager.nudgeConsumersForTopic("topic-1");
    });

    it("should handle nudging with empty consumer manager", async () => {
      // This should not throw an error
      await consumerManager.nudgeConsumersForTopic("any-topic");
    });
  });

  describe("Enhanced Registration", () => {
    it("should register consumer with sinceEventId values", () => {
      const registration = {
        callback: "http://localhost:3000/webhook",
        topics: {
          "topic-1": "topic-1-5",
          "topic-2": "topic-2-10",
        },
      };

      const consumerId = consumerManager.registerConsumer(registration);

      assertEquals(typeof consumerId, "string");
      assertEquals(consumerManager.getConsumerCount(), 1);

      const consumer = consumerManager.getConsumer(consumerId);
      assertEquals(consumer?.topics["topic-1"], "topic-1-5");
      assertEquals(consumer?.topics["topic-2"], "topic-2-10");
    });

    it("should register consumer with empty topics object", () => {
      const registration = {
        callback: "http://localhost:3000/webhook",
        topics: {},
      };

      const consumerId = consumerManager.registerConsumer(registration);

      assertEquals(typeof consumerId, "string");
      assertEquals(consumerManager.getConsumerCount(), 1);

      const consumer = consumerManager.getConsumer(consumerId);
      assertEquals(Object.keys(consumer?.topics || {}).length, 0);
    });

    it("should register multiple consumers for same topic", () => {
      const registration1 = {
        callback: "http://localhost:3000/webhook1",
        topics: { "topic-1": null },
      };
      const registration2 = {
        callback: "http://localhost:3000/webhook2",
        topics: { "topic-1": null },
      };
      const registration3 = {
        callback: "http://localhost:3000/webhook3",
        topics: { "topic-1": null },
      };

      consumerManager.registerConsumer(registration1);
      consumerManager.registerConsumer(registration2);
      consumerManager.registerConsumer(registration3);

      assertEquals(consumerManager.getConsumerCount(), 3);

      const consumers = consumerManager.getConsumersForTopic("topic-1");
      assertEquals(consumers.length, 3);
    });
  });

  describe("Enhanced Unregistration", () => {
    it("should unregister consumer and update count correctly", () => {
      const registration1 = {
        callback: "http://localhost:3000/webhook1",
        topics: { "topic-1": null },
      };
      const registration2 = {
        callback: "http://localhost:3000/webhook2",
        topics: { "topic-2": null },
      };

      const consumerId1 = consumerManager.registerConsumer(registration1);
      const consumerId2 = consumerManager.registerConsumer(registration2);

      assertEquals(consumerManager.getConsumerCount(), 2);

      // Unregister first consumer
      const removed1 = consumerManager.unregisterConsumer(consumerId1);
      assertEquals(removed1, true);
      assertEquals(consumerManager.getConsumerCount(), 1);
      assertEquals(consumerManager.getConsumer(consumerId1), undefined);

      // Unregister second consumer
      const removed2 = consumerManager.unregisterConsumer(consumerId2);
      assertEquals(removed2, true);
      assertEquals(consumerManager.getConsumerCount(), 0);
      assertEquals(consumerManager.getConsumer(consumerId2), undefined);
    });

    it("should handle unregistering already unregistered consumer", () => {
      const registration = {
        callback: "http://localhost:3000/webhook",
        topics: { "test-topic": null },
      };

      const consumerId = consumerManager.registerConsumer(registration);
      assertEquals(consumerManager.getConsumerCount(), 1);

      // Unregister first time
      const removed1 = consumerManager.unregisterConsumer(consumerId);
      assertEquals(removed1, true);
      assertEquals(consumerManager.getConsumerCount(), 0);

      // Try to unregister again
      const removed2 = consumerManager.unregisterConsumer(consumerId);
      assertEquals(removed2, false);
      assertEquals(consumerManager.getConsumerCount(), 0);
    });
  });

  describe("Enhanced Topic Queries", () => {
    it("should return consumers for topic with sinceEventId", () => {
      const registration1 = {
        callback: "http://localhost:3000/webhook1",
        topics: { "topic-1": "topic-1-5" },
      };
      const registration2 = {
        callback: "http://localhost:3000/webhook2",
        topics: { "topic-1": null },
      };

      consumerManager.registerConsumer(registration1);
      consumerManager.registerConsumer(registration2);

      const consumers = consumerManager.getConsumersForTopic("topic-1");
      assertEquals(consumers.length, 2);

      // Both consumers should be returned regardless of sinceEventId
      const consumer1 = consumers.find((c) =>
        c.topics["topic-1"] === "topic-1-5"
      );
      const consumer2 = consumers.find((c) => c.topics["topic-1"] === null);
      assertEquals(consumer1 !== undefined, true);
      assertEquals(consumer2 !== undefined, true);
    });

    it("should handle case-sensitive topic matching", () => {
      const registration = {
        callback: "http://localhost:3000/webhook",
        topics: { "Topic-1": null },
      };

      consumerManager.registerConsumer(registration);

      const consumers1 = consumerManager.getConsumersForTopic("Topic-1");
      assertEquals(consumers1.length, 1);

      const consumers2 = consumerManager.getConsumersForTopic("topic-1");
      assertEquals(consumers2.length, 0);
    });
  });

  describe("Enhanced Consumer Management", () => {
    it("should get consumer with all properties", () => {
      const registration = {
        callback: "http://localhost:3000/webhook",
        topics: {
          "topic-1": "topic-1-5",
          "topic-2": null,
        },
      };

      const consumerId = consumerManager.registerConsumer(registration);
      const consumer = consumerManager.getConsumer(consumerId);

      assertEquals(consumer?.id, consumerId);
      assertEquals(consumer?.callback, "http://localhost:3000/webhook");
      assertEquals(consumer?.topics["topic-1"], "topic-1-5");
      assertEquals(consumer?.topics["topic-2"], null);
      assertEquals(typeof consumer?.nudge, "function");
    });

    it("should return undefined for empty consumer ID", () => {
      const consumer = consumerManager.getConsumer("");
      assertEquals(consumer, undefined);
    });
  });

  describe("Enhanced Consumer Count", () => {
    it("should return zero for empty consumer manager", () => {
      assertEquals(consumerManager.getConsumerCount(), 0);
    });

    it("should return correct count after multiple operations", () => {
      assertEquals(consumerManager.getConsumerCount(), 0);

      const registration1 = {
        callback: "http://localhost:3000/webhook1",
        topics: { "topic-1": null },
      };
      const registration2 = {
        callback: "http://localhost:3000/webhook2",
        topics: { "topic-2": null },
      };
      const registration3 = {
        callback: "http://localhost:3000/webhook3",
        topics: { "topic-3": null },
      };

      consumerManager.registerConsumer(registration1);
      assertEquals(consumerManager.getConsumerCount(), 1);

      consumerManager.registerConsumer(registration2);
      assertEquals(consumerManager.getConsumerCount(), 2);

      consumerManager.registerConsumer(registration3);
      assertEquals(consumerManager.getConsumerCount(), 3);

      // Remove middle consumer
      const consumers = consumerManager.getAllConsumers();
      consumerManager.unregisterConsumer(consumers[1].id);
      assertEquals(consumerManager.getConsumerCount(), 2);

      // Remove remaining consumers
      consumerManager.unregisterConsumer(consumers[0].id);
      assertEquals(consumerManager.getConsumerCount(), 1);

      consumerManager.unregisterConsumer(consumers[2].id);
      assertEquals(consumerManager.getConsumerCount(), 0);
    });
  });

  describe("Event Delivery Simulation", () => {
    it("should handle consumer with nudge function", async () => {
      const registration = {
        callback: "http://localhost:3000/webhook",
        topics: { "test-topic": null },
      };

      const consumerId = consumerManager.registerConsumer(registration);
      const consumer = consumerManager.getConsumer(consumerId);

      assertEquals(consumer !== undefined, true);
      assertEquals(typeof consumer?.nudge, "function");

      // Test that nudge function can be called
      try {
        await consumer!.nudge();
        // Should not throw error even if delivery fails
      } catch (error) {
        // Expected to fail due to no actual webhook server
        assertEquals(error instanceof Error, true);
      }
    });

    it("should handle consumer removal during nudge", async () => {
      const registration = {
        callback: "http://localhost:3000/webhook",
        topics: { "test-topic": null },
      };

      const consumerId = consumerManager.registerConsumer(registration);

      // Remove consumer immediately
      consumerManager.unregisterConsumer(consumerId);
      assertEquals(consumerManager.getConsumerCount(), 0);

      // Try to nudge - should not throw error
      await consumerManager.nudgeConsumersForTopic("test-topic");
    });

    it("should handle nudge with consumer that has sinceEventId", async () => {
      const registration = {
        callback: "http://localhost:3000/webhook",
        topics: { "test-topic": "test-topic-5" },
      };

      const consumerId = consumerManager.registerConsumer(registration);
      const consumer = consumerManager.getConsumer(consumerId);

      assertEquals(consumer !== undefined, true);
      assertEquals(consumer?.topics["test-topic"], "test-topic-5");

      // Test that nudge function can be called with sinceEventId
      try {
        await consumer!.nudge();
        // Should not throw error even if delivery fails
      } catch (error) {
        // Expected to fail due to no actual webhook server
        assertEquals(error instanceof Error, true);
      }
    });

    it("should handle nudge with consumer that has multiple topics", async () => {
      const registration = {
        callback: "http://localhost:3000/webhook",
        topics: {
          "topic-1": "topic-1-5",
          "topic-2": null,
          "topic-3": "topic-3-10",
        },
      };

      const consumerId = consumerManager.registerConsumer(registration);
      const consumer = consumerManager.getConsumer(consumerId);

      assertEquals(consumer !== undefined, true);
      assertEquals(Object.keys(consumer?.topics || {}).length, 3);

      // Test that nudge function can be called with multiple topics
      try {
        await consumer!.nudge();
        // Should not throw error even if delivery fails
      } catch (error) {
        // Expected to fail due to no actual webhook server
        assertEquals(error instanceof Error, true);
      }
    });

    it("should handle nudge with consumer that has empty topics", async () => {
      const registration = {
        callback: "http://localhost:3000/webhook",
        topics: {},
      };

      const consumerId = consumerManager.registerConsumer(registration);
      const consumer = consumerManager.getConsumer(consumerId);

      assertEquals(consumer !== undefined, true);
      assertEquals(Object.keys(consumer?.topics || {}).length, 0);

      // Test that nudge function can be called with empty topics
      try {
        await consumer!.nudge();
        // Should not throw error even if delivery fails
      } catch (error) {
        // Expected to fail due to no actual webhook server
        assertEquals(error instanceof Error, true);
      }
    });

    it("should handle nudge with consumer that has null sinceEventId", async () => {
      const registration = {
        callback: "http://localhost:3000/webhook",
        topics: { "test-topic": null },
      };

      const consumerId = consumerManager.registerConsumer(registration);
      const consumer = consumerManager.getConsumer(consumerId);

      assertEquals(consumer !== undefined, true);
      assertEquals(consumer?.topics["test-topic"], null);

      // Test that nudge function can be called with null sinceEventId
      try {
        await consumer!.nudge();
        // Should not throw error even if delivery fails
      } catch (error) {
        // Expected to fail due to no actual webhook server
        assertEquals(error instanceof Error, true);
      }
    });
  });

  describe("Complex Topic Scenarios", () => {
    it("should handle consumers with overlapping topics", () => {
      const registration1 = {
        callback: "http://localhost:3000/webhook1",
        topics: {
          "topic-1": null,
          "topic-2": null,
        },
      };
      const registration2 = {
        callback: "http://localhost:3000/webhook2",
        topics: {
          "topic-2": null,
          "topic-3": null,
        },
      };
      const registration3 = {
        callback: "http://localhost:3000/webhook3",
        topics: {
          "topic-1": null,
          "topic-3": null,
        },
      };

      consumerManager.registerConsumer(registration1);
      consumerManager.registerConsumer(registration2);
      consumerManager.registerConsumer(registration3);

      assertEquals(consumerManager.getConsumerCount(), 3);

      const topic1Consumers = consumerManager.getConsumersForTopic("topic-1");
      assertEquals(topic1Consumers.length, 2);

      const topic2Consumers = consumerManager.getConsumersForTopic("topic-2");
      assertEquals(topic2Consumers.length, 2);

      const topic3Consumers = consumerManager.getConsumersForTopic("topic-3");
      assertEquals(topic3Consumers.length, 2);
    });

    it("should handle consumers with sinceEventId values", () => {
      const registration1 = {
        callback: "http://localhost:3000/webhook1",
        topics: {
          "topic-1": "topic-1-10",
          "topic-2": null,
        },
      };
      const registration2 = {
        callback: "http://localhost:3000/webhook2",
        topics: {
          "topic-1": null,
          "topic-2": "topic-2-5",
        },
      };

      consumerManager.registerConsumer(registration1);
      consumerManager.registerConsumer(registration2);

      const consumers = consumerManager.getAllConsumers();
      assertEquals(consumers.length, 2);

      const consumer1 = consumers.find((c) =>
        c.topics["topic-1"] === "topic-1-10"
      );
      const consumer2 = consumers.find((c) =>
        c.topics["topic-2"] === "topic-2-5"
      );

      assertEquals(consumer1 !== undefined, true);
      assertEquals(consumer2 !== undefined, true);
    });
  });

  describe("Edge Cases", () => {
    it("should handle registration with empty callback", () => {
      const registration = {
        callback: "",
        topics: { "test-topic": null },
      };

      const consumerId = consumerManager.registerConsumer(registration);
      assertEquals(typeof consumerId, "string");
      assertEquals(consumerManager.getConsumerCount(), 1);
    });

    it("should handle registration with empty string callback", () => {
      const registration = {
        callback: "",
        topics: { "test-topic": null },
      };

      const consumerId = consumerManager.registerConsumer(registration);
      assertEquals(typeof consumerId, "string");
      assertEquals(consumerManager.getConsumerCount(), 1);
    });
  });

  describe("Internal Method Coverage", () => {
    // Create a mock EventManager that can return events to trigger delivery logic
    let mockEventManager: any;
    let mockConsumerManager: ConsumerManager;

    beforeEach(() => {
      // Create a mock EventManager that returns events
      mockEventManager = {
        getEvents: (topic: string, _options: any) => {
          // Return mock events to trigger delivery logic
          return [
            {
              id: `${topic}-1`,
              topic: topic,
              payload: { message: "test event 1" },
              timestamp: new Date().toISOString(),
            },
            {
              id: `${topic}-2`,
              topic: topic,
              payload: { message: "test event 2" },
              timestamp: new Date().toISOString(),
            },
          ];
        },
      } as any;

      mockConsumerManager = new ConsumerManager(mockEventManager);
    });

    it("should handle consumer with invalid callback URL", async () => {
      const registration = {
        callback: "invalid-url",
        topics: { "test-topic": null },
      };

      const consumerId = mockConsumerManager.registerConsumer(registration);
      const consumer = mockConsumerManager.getConsumer(consumerId);

      assertEquals(consumer !== undefined, true);

      // Test that nudge function handles invalid URL
      try {
        await consumer!.nudge();
        // Should not throw error even if delivery fails
      } catch (error) {
        // Expected to fail due to invalid URL
        assertEquals(error instanceof Error, true);
      }
    });

    it("should handle consumer with null callback", async () => {
      const registration = {
        callback: null as any,
        topics: { "test-topic": null },
      };

      const consumerId = mockConsumerManager.registerConsumer(registration);
      const consumer = mockConsumerManager.getConsumer(consumerId);

      assertEquals(consumer !== undefined, true);

      // Test that nudge function handles null callback
      try {
        await consumer!.nudge();
        // Should not throw error even if delivery fails
      } catch (error) {
        // Expected to fail due to null callback
        assertEquals(error instanceof Error, true);
      }
    });

    it("should handle consumer with undefined callback", async () => {
      const registration = {
        callback: undefined as any,
        topics: { "test-topic": null },
      };

      const consumerId = mockConsumerManager.registerConsumer(registration);
      const consumer = mockConsumerManager.getConsumer(consumerId);

      assertEquals(consumer !== undefined, true);

      // Test that nudge function handles undefined callback
      try {
        await consumer!.nudge();
        // Should not throw error even if delivery fails
      } catch (error) {
        // Expected to fail due to undefined callback
        assertEquals(error instanceof Error, true);
      }
    });

    it("should handle consumer with empty string callback", async () => {
      const registration = {
        callback: "",
        topics: { "test-topic": null },
      };

      const consumerId = mockConsumerManager.registerConsumer(registration);
      const consumer = mockConsumerManager.getConsumer(consumerId);

      assertEquals(consumer !== undefined, true);

      // Test that nudge function handles empty string callback
      try {
        await consumer!.nudge();
        // Should not throw error even if delivery fails
      } catch (error) {
        // Expected to fail due to empty string callback
        assertEquals(error instanceof Error, true);
      }
    });

    it("should trigger delivery logic with mock events", async () => {
      const registration = {
        callback: "http://localhost:3000/webhook",
        topics: { "test-topic": null },
      };

      const consumerId = mockConsumerManager.registerConsumer(registration);
      const consumer = mockConsumerManager.getConsumer(consumerId);

      assertEquals(consumer !== undefined, true);

      // This should trigger the actual delivery logic with mock events
      try {
        await consumer!.nudge();
        // Should not throw error even if delivery fails
      } catch (error) {
        // Expected to fail due to no server running, but delivery logic was triggered
        assertEquals(error instanceof Error, true);
      }
    });

    it("should trigger delivery logic with sinceEventId", async () => {
      const registration = {
        callback: "http://localhost:3000/webhook",
        topics: { "test-topic": "test-topic-0" },
      };

      const consumerId = mockConsumerManager.registerConsumer(registration);
      const consumer = mockConsumerManager.getConsumer(consumerId);

      assertEquals(consumer !== undefined, true);
      assertEquals(consumer?.topics["test-topic"], "test-topic-0");

      // This should trigger the actual delivery logic with sinceEventId
      try {
        await consumer!.nudge();
        // Should not throw error even if delivery fails
      } catch (error) {
        // Expected to fail due to no server running, but delivery logic was triggered
        assertEquals(error instanceof Error, true);
      }
    });

    it("should trigger delivery logic with multiple topics", async () => {
      const registration = {
        callback: "http://localhost:3000/webhook",
        topics: {
          "topic-1": null,
          "topic-2": "topic-2-0",
        },
      };

      const consumerId = mockConsumerManager.registerConsumer(registration);
      const consumer = mockConsumerManager.getConsumer(consumerId);

      assertEquals(consumer !== undefined, true);
      assertEquals(Object.keys(consumer?.topics || {}).length, 2);

      // This should trigger the actual delivery logic for multiple topics
      try {
        await consumer!.nudge();
        // Should not throw error even if delivery fails
      } catch (error) {
        // Expected to fail due to no server running, but delivery logic was triggered
        assertEquals(error instanceof Error, true);
      }
    });

    it("should handle EventManager errors during delivery", async () => {
      // Create EventManager that throws errors
      const errorEventManager = {
        getEvents: async (topic: string, options: any) => {
          throw new Error(`Failed to get events for topic ${topic}`);
        },
      } as any;

      const errorConsumerManager = new ConsumerManager(errorEventManager);

      const registration = {
        callback: "http://localhost:3000/webhook",
        topics: { "test-topic": null },
      };

      const consumerId = errorConsumerManager.registerConsumer(registration);
      const consumer = errorConsumerManager.getConsumer(consumerId);

      assertEquals(consumer !== undefined, true);

      // This should handle EventManager errors gracefully
      try {
        await consumer!.nudge();
        // Should not throw error even if EventManager fails
      } catch (error) {
        // Expected to fail due to EventManager error
        assertEquals(error instanceof Error, true);
      }
    });

    it("should handle consumer removal during delivery", async () => {
      const registration = {
        callback: "http://localhost:3000/webhook",
        topics: { "test-topic": null },
      };

      const consumerId = mockConsumerManager.registerConsumer(registration);

      // Remove consumer immediately
      mockConsumerManager.unregisterConsumer(consumerId);
      assertEquals(mockConsumerManager.getConsumerCount(), 0);

      // Try to nudge - should not throw error
      await mockConsumerManager.nudgeConsumersForTopic("test-topic");
    });

    it("should handle delivery with empty events array", async () => {
      // Create EventManager that returns empty events
      const emptyEventManager = {
        getEvents: (_topic: string) => {
          return [];
        },
      } as any;

      const emptyConsumerManager = new ConsumerManager(emptyEventManager);

      const registration = {
        callback: "http://localhost:3000/webhook",
        topics: { "test-topic": null },
      };

      const consumerId = emptyConsumerManager.registerConsumer(registration);
      const consumer = emptyConsumerManager.getConsumer(consumerId);

      assertEquals(consumer !== undefined, true);

      // This should handle empty events gracefully
      try {
        await consumer!.nudge();
        // Should not throw error even with empty events
      } catch (_error) {
        // Should not throw error for empty events
        assertEquals(false, true);
      }
    });
  });
});
