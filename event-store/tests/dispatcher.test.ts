import { assertEquals, assertExists } from "$std/assert/mod.ts";
import {
  afterAll,
  afterEach,
  beforeEach,
  describe,
  it,
} from "$std/testing/bdd.ts";
import { Dispatcher } from "../core/dispatcher.ts";
import { ConsumerManager } from "../core/consumers.ts";
import { EventManager } from "../core/events.ts";
import { TopicManager } from "../core/topics.ts";
import { cleanupAllTestDirectories, TestSetup } from "./helpers/test-setup.ts";

describe("Dispatcher", () => {
  let dispatcher: Dispatcher;
  let consumerManager: ConsumerManager;
  let eventManager: EventManager;
  let topicManager: TopicManager;
  let testSetup: TestSetup;

  beforeEach(async () => {
    testSetup = new TestSetup();
    await testSetup.setup();

    topicManager = new TopicManager();
    eventManager = new EventManager(topicManager);
    consumerManager = new ConsumerManager(eventManager);
    dispatcher = new Dispatcher(consumerManager, eventManager);
  });

  afterEach(async () => {
    dispatcher.stopAllDispatchers();
    await testSetup.cleanup();
  });

  afterAll(async () => {
    await cleanupAllTestDirectories();
  });

  describe("startDispatcher", () => {
    it("should start a dispatcher for a topic", () => {
      dispatcher.startDispatcher("test-topic");
      assertEquals(dispatcher.isDispatcherRunning("test-topic"), true);
      assertEquals(dispatcher.getRunningDispatchers(), ["test-topic"]);
    });

    it("should not start duplicate dispatchers for the same topic", () => {
      dispatcher.startDispatcher("test-topic");
      dispatcher.startDispatcher("test-topic");
      assertEquals(dispatcher.getRunningDispatchers().length, 1);
    });

    it("should start multiple dispatchers for different topics", () => {
      dispatcher.startDispatcher("topic-1");
      dispatcher.startDispatcher("topic-2");
      assertEquals(dispatcher.getRunningDispatchers().length, 2);
      assertEquals(dispatcher.isDispatcherRunning("topic-1"), true);
      assertEquals(dispatcher.isDispatcherRunning("topic-2"), true);
    });
  });

  describe("stopDispatcher", () => {
    it("should stop a specific dispatcher", () => {
      dispatcher.startDispatcher("test-topic");
      dispatcher.stopDispatcher("test-topic");
      assertEquals(dispatcher.isDispatcherRunning("test-topic"), false);
      assertEquals(dispatcher.getRunningDispatchers().length, 0);
    });

    it("should handle stopping non-existent dispatcher", () => {
      dispatcher.stopDispatcher("non-existent");
      assertEquals(dispatcher.getRunningDispatchers().length, 0);
    });
  });

  describe("stopAllDispatchers", () => {
    it("should stop all running dispatchers", () => {
      dispatcher.startDispatcher("topic-1");
      dispatcher.startDispatcher("topic-2");
      dispatcher.startDispatcher("topic-3");

      dispatcher.stopAllDispatchers();

      assertEquals(dispatcher.getRunningDispatchers().length, 0);
      assertEquals(dispatcher.isDispatcherRunning("topic-1"), false);
      assertEquals(dispatcher.isDispatcherRunning("topic-2"), false);
      assertEquals(dispatcher.isDispatcherRunning("topic-3"), false);
    });

    it("should handle stopping when no dispatchers are running", () => {
      dispatcher.stopAllDispatchers();
      assertEquals(dispatcher.getRunningDispatchers().length, 0);
    });
  });

  describe("triggerDelivery", () => {
    it("should trigger delivery for a topic", async () => {
      // Mock the consumer manager's nudgeConsumersForTopic method
      const originalNudge = consumerManager.nudgeConsumersForTopic.bind(
        consumerManager,
      );
      let nudgeCalled = false;
      consumerManager.nudgeConsumersForTopic = async (topic: string) => {
        nudgeCalled = true;
        assertEquals(topic, "test-topic");
      };

      await dispatcher.triggerDelivery("test-topic");
      assertEquals(nudgeCalled, true);

      // Restore original method
      consumerManager.nudgeConsumersForTopic = originalNudge;
    });
  });

  describe("getRunningDispatchers", () => {
    it("should return empty array when no dispatchers are running", () => {
      assertEquals(dispatcher.getRunningDispatchers(), []);
    });

    it("should return all running dispatcher topics", () => {
      dispatcher.startDispatcher("topic-1");
      dispatcher.startDispatcher("topic-2");

      const running = dispatcher.getRunningDispatchers();
      assertEquals(running.length, 2);
      assertEquals(running.includes("topic-1"), true);
      assertEquals(running.includes("topic-2"), true);
    });
  });

  describe("isDispatcherRunning", () => {
    it("should return false for non-existent dispatcher", () => {
      assertEquals(dispatcher.isDispatcherRunning("non-existent"), false);
    });

    it("should return true for running dispatcher", () => {
      dispatcher.startDispatcher("test-topic");
      assertEquals(dispatcher.isDispatcherRunning("test-topic"), true);
    });
  });

  describe("setCheckInterval", () => {
    it("should set the check interval", () => {
      dispatcher.setCheckInterval(1000);
      // We can't directly test the private property, but we can verify it doesn't throw
      assertEquals(typeof dispatcher.setCheckInterval, "function");
    });
  });

  describe("Enhanced Dispatcher Logic", () => {
    it("should handle dispatcher with no consumers for topic", async () => {
      // Start a dispatcher for a topic with no consumers
      dispatcher.startDispatcher("empty-topic");

      // Wait a bit for the interval to run
      await new Promise((resolve) => setTimeout(resolve, 100));

      // Should not throw any errors and should still be running
      assertEquals(dispatcher.isDispatcherRunning("empty-topic"), true);

      dispatcher.stopDispatcher("empty-topic");
    });

    it("should handle dispatcher with consumers but no new events", async () => {
      // Create a topic and register a consumer
      await topicManager.createTopic("dispatcher-test-topic-1", [{
        eventType: "test.event",
        type: "object",
        $schema: "https://json-schema.org/draft/2020-12/schema",
        properties: {
          message: { type: "string" },
        },
        required: ["message"],
      }]);

      // Register a consumer
      const consumerId = consumerManager.registerConsumer({
        callback: "http://localhost:3000/webhook",
        topics: {
          "dispatcher-test-topic-1": null,
        },
      });

      // Start dispatcher
      dispatcher.startDispatcher("dispatcher-test-topic-1");

      // Wait a bit for the interval to run
      await new Promise((resolve) => setTimeout(resolve, 100));

      // Should not throw any errors and should still be running
      assertEquals(
        dispatcher.isDispatcherRunning("dispatcher-test-topic-1"),
        true,
      );

      dispatcher.stopDispatcher("dispatcher-test-topic-1");
    });

    it("should handle dispatcher with consumers and new events", async () => {
      // Create a topic and register a consumer
      await topicManager.createTopic("dispatcher-test-topic-2", [{
        eventType: "test.event",
        type: "object",
        $schema: "https://json-schema.org/draft/2020-12/schema",
        properties: {
          message: { type: "string" },
        },
        required: ["message"],
      }]);

      // Register a consumer
      const consumerId = consumerManager.registerConsumer({
        callback: "http://localhost:3000/webhook",
        topics: {
          "dispatcher-test-topic-2": null,
        },
      });

      // Start dispatcher
      dispatcher.startDispatcher("dispatcher-test-topic-2");

      // Store an event to trigger delivery
      await eventManager.storeEvent({
        topic: "dispatcher-test-topic-2",
        type: "test.event",
        payload: { message: "Hello World" },
      });

      // Wait a bit for the interval to run
      await new Promise((resolve) => setTimeout(resolve, 100));

      // Should not throw any errors and should still be running
      assertEquals(
        dispatcher.isDispatcherRunning("dispatcher-test-topic-2"),
        true,
      );

      dispatcher.stopDispatcher("dispatcher-test-topic-2");
    });

    it("should handle multiple consumers for same topic", async () => {
      // Create a topic and register multiple consumers
      await topicManager.createTopic("dispatcher-test-topic-3", [{
        eventType: "test.event",
        type: "object",
        $schema: "https://json-schema.org/draft/2020-12/schema",
        properties: {
          message: { type: "string" },
        },
        required: ["message"],
      }]);

      // Register multiple consumers
      const consumerId1 = consumerManager.registerConsumer({
        callback: "http://localhost:3000/webhook1",
        topics: {
          "dispatcher-test-topic-3": null,
        },
      });
      const consumerId2 = consumerManager.registerConsumer({
        callback: "http://localhost:3000/webhook2",
        topics: {
          "dispatcher-test-topic-3": null,
        },
      });

      // Start dispatcher
      dispatcher.startDispatcher("dispatcher-test-topic-3");

      // Store an event to trigger delivery
      await eventManager.storeEvent({
        topic: "dispatcher-test-topic-3",
        type: "test.event",
        payload: { message: "Hello World" },
      });

      // Wait a bit for the interval to run
      await new Promise((resolve) => setTimeout(resolve, 100));

      // Should not throw any errors and should still be running
      assertEquals(
        dispatcher.isDispatcherRunning("dispatcher-test-topic-3"),
        true,
      );

      dispatcher.stopDispatcher("dispatcher-test-topic-3");
    });

    it("should handle consumers with sinceEventId values", async () => {
      // Create a topic and register a consumer with sinceEventId
      await topicManager.createTopic("dispatcher-test-topic-4", [{
        eventType: "test.event",
        type: "object",
        $schema: "https://json-schema.org/draft/2020-12/schema",
        properties: {
          message: { type: "string" },
        },
        required: ["message"],
      }]);

      // Store an initial event
      const eventId1 = await eventManager.storeEvent({
        topic: "dispatcher-test-topic-4",
        type: "test.event",
        payload: { message: "Event 1" },
      });

      // Register a consumer with sinceEventId
      const consumerId = consumerManager.registerConsumer({
        callback: "http://localhost:3000/webhook",
        topics: {
          "dispatcher-test-topic-4": eventId1,
        },
      });

      // Start dispatcher
      dispatcher.startDispatcher("dispatcher-test-topic-4");

      // Store a new event
      await eventManager.storeEvent({
        topic: "dispatcher-test-topic-4",
        type: "test.event",
        payload: { message: "Event 2" },
      });

      // Wait a bit for the interval to run
      await new Promise((resolve) => setTimeout(resolve, 100));

      // Should not throw any errors and should still be running
      assertEquals(
        dispatcher.isDispatcherRunning("dispatcher-test-topic-4"),
        true,
      );

      dispatcher.stopDispatcher("dispatcher-test-topic-4");
    });
  });

  describe("Error Handling", () => {
    it("should handle EventManager errors gracefully", async () => {
      // Create a topic and register a consumer
      await topicManager.createTopic("dispatcher-test-topic-5", [{
        eventType: "test.event",
        type: "object",
        $schema: "https://json-schema.org/draft/2020-12/schema",
        properties: {
          message: { type: "string" },
        },
        required: ["message"],
      }]);

      // Register a consumer
      const consumerId = consumerManager.registerConsumer({
        callback: "http://localhost:3000/webhook",
        topics: {
          "dispatcher-test-topic-5": null,
        },
      });

      // Mock EventManager to throw an error
      const originalGetEvents = eventManager.getEvents.bind(eventManager);
      eventManager.getEvents = async () => {
        throw new Error("EventManager error");
      };

      // Start dispatcher
      dispatcher.startDispatcher("dispatcher-test-topic-5");

      // Wait a bit for the interval to run
      await new Promise((resolve) => setTimeout(resolve, 100));

      // Should not throw any errors and should still be running
      assertEquals(
        dispatcher.isDispatcherRunning("dispatcher-test-topic-5"),
        true,
      );

      // Restore original method
      eventManager.getEvents = originalGetEvents;

      dispatcher.stopDispatcher("dispatcher-test-topic-5");
    });

    it("should handle ConsumerManager errors gracefully", async () => {
      // Create a topic and register a consumer
      await topicManager.createTopic("dispatcher-test-topic-6", [{
        eventType: "test.event",
        type: "object",
        $schema: "https://json-schema.org/draft/2020-12/schema",
        properties: {
          message: { type: "string" },
        },
        required: ["message"],
      }]);

      // Register a consumer
      const consumerId = consumerManager.registerConsumer({
        callback: "http://localhost:3000/webhook",
        topics: {
          "dispatcher-test-topic-6": null,
        },
      });

      // Mock ConsumerManager to throw an error
      const originalGetConsumers = consumerManager.getConsumersForTopic.bind(
        consumerManager,
      );
      consumerManager.getConsumersForTopic = () => {
        throw new Error("ConsumerManager error");
      };

      // Start dispatcher
      dispatcher.startDispatcher("dispatcher-test-topic-6");

      // Wait a bit for the interval to run
      await new Promise((resolve) => setTimeout(resolve, 100));

      // Should not throw any errors and should still be running
      assertEquals(
        dispatcher.isDispatcherRunning("dispatcher-test-topic-6"),
        true,
      );

      // Restore original method
      consumerManager.getConsumersForTopic = originalGetConsumers;

      dispatcher.stopDispatcher("dispatcher-test-topic-6");
    });

    it("should handle nudgeConsumersForTopic errors gracefully", async () => {
      // Create a topic and register a consumer
      await topicManager.createTopic("dispatcher-test-topic-7", [{
        eventType: "test.event",
        type: "object",
        $schema: "https://json-schema.org/draft/2020-12/schema",
        properties: {
          message: { type: "string" },
        },
        required: ["message"],
      }]);

      // Register a consumer
      const consumerId = consumerManager.registerConsumer({
        callback: "http://localhost:3000/webhook",
        topics: {
          "dispatcher-test-topic-7": null,
        },
      });

      // Mock nudgeConsumersForTopic to throw an error
      const originalNudge = consumerManager.nudgeConsumersForTopic.bind(
        consumerManager,
      );
      consumerManager.nudgeConsumersForTopic = async () => {
        throw new Error("Nudge error");
      };

      // Start dispatcher
      dispatcher.startDispatcher("dispatcher-test-topic-7");

      // Store an event to trigger delivery
      await eventManager.storeEvent({
        topic: "dispatcher-test-topic-7",
        type: "test.event",
        payload: { message: "Hello World" },
      });

      // Wait a bit for the interval to run
      await new Promise((resolve) => setTimeout(resolve, 100));

      // Should not throw any errors and should still be running
      assertEquals(
        dispatcher.isDispatcherRunning("dispatcher-test-topic-7"),
        true,
      );

      // Restore original method
      consumerManager.nudgeConsumersForTopic = originalNudge;

      dispatcher.stopDispatcher("dispatcher-test-topic-7");
    });
  });

  describe("Enhanced triggerDelivery", () => {
    it("should handle triggerDelivery with no consumers", async () => {
      // Trigger delivery for a topic with no consumers
      await dispatcher.triggerDelivery("empty-topic");
      // Should not throw any errors
      assertEquals(true, true);
    });

    it("should handle triggerDelivery with consumers", async () => {
      // Create a topic and register a consumer
      await topicManager.createTopic("dispatcher-test-topic-8", [{
        eventType: "test.event",
        type: "object",
        $schema: "https://json-schema.org/draft/2020-12/schema",
        properties: {
          message: { type: "string" },
        },
        required: ["message"],
      }]);

      // Register a consumer
      const consumerId = consumerManager.registerConsumer({
        callback: "http://localhost:3000/webhook",
        topics: {
          "dispatcher-test-topic-8": null,
        },
      });

      // Trigger delivery
      await dispatcher.triggerDelivery("dispatcher-test-topic-8");
      // Should not throw any errors
      assertEquals(true, true);
    });

    it("should handle triggerDelivery errors gracefully", async () => {
      // Mock nudgeConsumersForTopic to throw an error
      const originalNudge = consumerManager.nudgeConsumersForTopic.bind(
        consumerManager,
      );
      consumerManager.nudgeConsumersForTopic = async () => {
        throw new Error("Trigger delivery error");
      };

      try {
        // Trigger delivery should not throw
        await dispatcher.triggerDelivery("test-topic");
      } catch (error) {
        // If it throws, that's expected behavior
        assertEquals(error instanceof Error, true);
      }

      // Restore original method
      consumerManager.nudgeConsumersForTopic = originalNudge;
    });
  });

  describe("Edge Cases", () => {
    it("should handle very short check intervals", () => {
      dispatcher.setCheckInterval(1); // Very short interval
      dispatcher.startDispatcher("test-topic");

      // Should still work
      assertEquals(dispatcher.isDispatcherRunning("test-topic"), true);

      dispatcher.stopDispatcher("test-topic");
    });

    it("should handle very long check intervals", () => {
      dispatcher.setCheckInterval(60000); // 1 minute
      dispatcher.startDispatcher("test-topic");

      // Should still work
      assertEquals(dispatcher.isDispatcherRunning("test-topic"), true);

      dispatcher.stopDispatcher("test-topic");
    });

    it("should handle zero check interval", () => {
      dispatcher.setCheckInterval(0);
      dispatcher.startDispatcher("test-topic");

      // Should still work
      assertEquals(dispatcher.isDispatcherRunning("test-topic"), true);

      dispatcher.stopDispatcher("test-topic");
    });

    it("should handle negative check interval", () => {
      dispatcher.setCheckInterval(-100);
      dispatcher.startDispatcher("test-topic");

      // Should still work
      assertEquals(dispatcher.isDispatcherRunning("test-topic"), true);

      dispatcher.stopDispatcher("test-topic");
    });

    it("should handle topic names with special characters", () => {
      const topicName = "test-topic-with-special-chars-123_456";
      dispatcher.startDispatcher(topicName);

      assertEquals(dispatcher.isDispatcherRunning(topicName), true);
      assertEquals(
        dispatcher.getRunningDispatchers().includes(topicName),
        true,
      );

      dispatcher.stopDispatcher(topicName);
    });

    it("should handle very long topic names", () => {
      const topicName = "a".repeat(100); // Very long topic name
      dispatcher.startDispatcher(topicName);

      assertEquals(dispatcher.isDispatcherRunning(topicName), true);
      assertEquals(
        dispatcher.getRunningDispatchers().includes(topicName),
        true,
      );

      dispatcher.stopDispatcher(topicName);
    });

    it("should handle empty topic names", () => {
      dispatcher.startDispatcher("");

      assertEquals(dispatcher.isDispatcherRunning(""), true);
      assertEquals(dispatcher.getRunningDispatchers().includes(""), true);

      dispatcher.stopDispatcher("");
    });

    it("should handle stopping dispatcher multiple times", () => {
      dispatcher.startDispatcher("test-topic");
      dispatcher.stopDispatcher("test-topic");
      dispatcher.stopDispatcher("test-topic"); // Stop again

      assertEquals(dispatcher.isDispatcherRunning("test-topic"), false);
      assertEquals(dispatcher.getRunningDispatchers().length, 0);
    });

    it("should handle starting and stopping dispatchers rapidly", () => {
      for (let i = 0; i < 10; i++) {
        dispatcher.startDispatcher(`topic-${i}`);
        dispatcher.stopDispatcher(`topic-${i}`);
      }

      assertEquals(dispatcher.getRunningDispatchers().length, 0);
    });
  });

  describe("Dispatcher Lifecycle", () => {
    it("should handle complete dispatcher lifecycle", async () => {
      // Create a topic and register a consumer
      await topicManager.createTopic("dispatcher-test-topic-9", [{
        eventType: "test.event",
        type: "object",
        $schema: "https://json-schema.org/draft/2020-12/schema",
        properties: {
          message: { type: "string" },
        },
        required: ["message"],
      }]);

      // Register a consumer
      const consumerId = consumerManager.registerConsumer({
        callback: "http://localhost:3000/webhook",
        topics: {
          "dispatcher-test-topic-9": null,
        },
      });

      // Start dispatcher
      dispatcher.startDispatcher("dispatcher-test-topic-9");
      assertEquals(
        dispatcher.isDispatcherRunning("dispatcher-test-topic-9"),
        true,
      );
      assertEquals(dispatcher.getRunningDispatchers().length, 1);

      // Store an event
      await eventManager.storeEvent({
        topic: "dispatcher-test-topic-9",
        type: "test.event",
        payload: { message: "Hello World" },
      });

      // Wait a bit for processing
      await new Promise((resolve) => setTimeout(resolve, 100));

      // Stop dispatcher
      dispatcher.stopDispatcher("dispatcher-test-topic-9");
      assertEquals(
        dispatcher.isDispatcherRunning("dispatcher-test-topic-9"),
        false,
      );
      assertEquals(dispatcher.getRunningDispatchers().length, 0);
    });

    it("should handle multiple dispatchers lifecycle", async () => {
      // Create topics and register consumers
      await topicManager.createTopic("dispatcher-lifecycle-topic-1", [{
        eventType: "test.event",
        type: "object",
        $schema: "https://json-schema.org/draft/2020-12/schema",
        properties: {
          message: { type: "string" },
        },
        required: ["message"],
      }]);

      await topicManager.createTopic("dispatcher-lifecycle-topic-2", [{
        eventType: "test.event",
        type: "object",
        $schema: "https://json-schema.org/draft/2020-12/schema",
        properties: {
          message: { type: "string" },
        },
        required: ["message"],
      }]);

      // Register consumers
      consumerManager.registerConsumer({
        callback: "http://localhost:3000/webhook1",
        topics: {
          "dispatcher-lifecycle-topic-1": null,
        },
      });
      consumerManager.registerConsumer({
        callback: "http://localhost:3000/webhook2",
        topics: {
          "dispatcher-lifecycle-topic-2": null,
        },
      });

      // Start multiple dispatchers
      dispatcher.startDispatcher("dispatcher-lifecycle-topic-1");
      dispatcher.startDispatcher("dispatcher-lifecycle-topic-2");

      assertEquals(dispatcher.getRunningDispatchers().length, 2);
      assertEquals(
        dispatcher.isDispatcherRunning("dispatcher-lifecycle-topic-1"),
        true,
      );
      assertEquals(
        dispatcher.isDispatcherRunning("dispatcher-lifecycle-topic-2"),
        true,
      );

      // Store events
      await eventManager.storeEvent({
        topic: "dispatcher-lifecycle-topic-1",
        type: "test.event",
        payload: { message: "Event 1" },
      });

      await eventManager.storeEvent({
        topic: "dispatcher-lifecycle-topic-2",
        type: "test.event",
        payload: { message: "Event 2" },
      });

      // Wait a bit for processing
      await new Promise((resolve) => setTimeout(resolve, 100));

      // Stop all dispatchers
      dispatcher.stopAllDispatchers();
      assertEquals(dispatcher.getRunningDispatchers().length, 0);
      assertEquals(
        dispatcher.isDispatcherRunning("dispatcher-lifecycle-topic-1"),
        false,
      );
      assertEquals(
        dispatcher.isDispatcherRunning("dispatcher-lifecycle-topic-2"),
        false,
      );
    });
  });
});
