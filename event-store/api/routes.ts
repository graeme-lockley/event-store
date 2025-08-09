import { Context, Router, RouterContext } from "../deps.ts";
import { TopicManager } from "../core/topics.ts";
import { EventManager } from "../core/events.ts";
import { ConsumerManager } from "../core/consumers.ts";
import { Dispatcher } from "../core/dispatcher.ts";
import {
  ConsumerRegistration,
  EventRequest,
  EventsQuery,
  TopicCreation,
} from "../types.ts";

export function createRouter(
  topicManager: TopicManager,
  eventManager: EventManager,
  consumerManager: ConsumerManager,
  dispatcher: Dispatcher,
): Router {
  const router = new Router();

  // POST /topics - Create a new topic and register associated schemas
  router.post("/topics", async (ctx: Context) => {
    try {
      const body = await ctx.request.body().value as TopicCreation;

      if (!body.name || !body.schemas || !Array.isArray(body.schemas)) {
        ctx.response.status = 400;
        ctx.response.body = {
          error: "Invalid request body. Required: name, schemas array",
        };
        return;
      }

      await topicManager.createTopic(body.name, body.schemas);

      // Start dispatcher for the new topic
      dispatcher.startDispatcher(body.name);

      ctx.response.status = 201;
      ctx.response.body = {
        message: `Topic '${body.name}' created successfully`,
      };
    } catch (error: unknown) {
      ctx.response.status = 400;
      ctx.response.body = {
        error: error instanceof Error ? error.message : String(error),
      };
    }
  });

  // POST /events - Publish one or more events
  router.post("/events", async (ctx: Context) => {
    try {
      const body = await ctx.request.body().value as EventRequest[];

      if (!Array.isArray(body) || body.length === 0) {
        ctx.response.status = 400;
        ctx.response.body = {
          error: "Request body must be a non-empty array of events",
        };
        return;
      }

      // Validate all events first
      for (const eventRequest of body) {
        if (
          !eventRequest.topic || !eventRequest.type ||
          eventRequest.payload === undefined
        ) {
          ctx.response.status = 400;
          ctx.response.body = {
            error: "Each event must have topic, type, and payload",
          };
          return;
        }
      }

      // Store all events
      const eventIds = await eventManager.storeEvents(body);

      // Trigger immediate delivery for each topic that received events
      const topicsWithEvents = [...new Set(body.map((e) => e.topic))];
      for (const topic of topicsWithEvents) {
        await dispatcher.triggerDelivery(topic);
      }

      ctx.response.status = 201;
      ctx.response.body = { eventIds };
    } catch (error: unknown) {
      ctx.response.status = 400;
      ctx.response.body = {
        error: error instanceof Error ? error.message : String(error),
      };
    }
  });

  // POST /consumers/register - Register a new consumer
  router.post("/consumers/register", async (ctx: Context) => {
    try {
      const body = await ctx.request.body().value as ConsumerRegistration;

      if (
        !body.callback || !body.topics || Object.keys(body.topics).length === 0
      ) {
        ctx.response.status = 400;
        ctx.response.body = {
          error:
            "Invalid request body. Required: callback URL and topics object",
        };
        return;
      }

      // Validate that all topics exist
      for (const topic of Object.keys(body.topics)) {
        if (!(await topicManager.topicExists(topic))) {
          ctx.response.status = 400;
          ctx.response.body = { error: `Topic '${topic}' not found` };
          return;
        }
      }

      const consumerId = consumerManager.registerConsumer(body);

      // Start dispatchers for topics that don't have one running
      for (const topic of Object.keys(body.topics)) {
        if (!dispatcher.isDispatcherRunning(topic)) {
          dispatcher.startDispatcher(topic);
        }
      }

      ctx.response.status = 201;
      ctx.response.body = { consumerId };
    } catch (error: unknown) {
      ctx.response.status = 400;
      ctx.response.body = {
        error: error instanceof Error ? error.message : String(error),
      };
    }
  });

  // GET /topics/:topic/events - Retrieve events from a topic
  router.get("/topics/:topic/events", async (ctx: RouterContext<"/topics/:topic/events">) => {
    try {
      const topic = ctx.params.topic;

      if (!topic || !(await topicManager.topicExists(topic))) {
        ctx.response.status = 404;
        ctx.response.body = { error: `Topic '${topic}' not found` };
        return;
      }

      // Parse query parameters
      const query: EventsQuery = {};
      const url = new URL(ctx.request.url);

      if (url.searchParams.has("sinceEventId")) {
        query.sinceEventId = url.searchParams.get("sinceEventId")!;
      }

      if (url.searchParams.has("date")) {
        query.date = url.searchParams.get("date")!;
      }

      if (url.searchParams.has("limit")) {
        const limit = parseInt(url.searchParams.get("limit")!);
        if (!isNaN(limit) && limit > 0) {
          query.limit = limit;
        }
      }

      const events = await eventManager.getEvents(topic, query);

      ctx.response.status = 200;
      ctx.response.body = { events };
    } catch (error: unknown) {
      ctx.response.status = 500;
      ctx.response.body = {
        error: error instanceof Error ? error.message : String(error),
      };
    }
  });

  // GET /topics - List all topics
  router.get("/topics", async (ctx: Context) => {
    try {
      const topics = await topicManager.getAllTopics();
      ctx.response.status = 200;
      ctx.response.body = { topics };
    } catch (error: unknown) {
      ctx.response.status = 500;
      ctx.response.body = {
        error: error instanceof Error ? error.message : String(error),
      };
    }
  });

  // GET /topics/:topic - Get detailed information about a specific topic
  router.get("/topics/:topic", async (ctx: RouterContext<"/topics/:topic">) => {
    try {
      const topic = ctx.params.topic;

      if (!topic || !(await topicManager.topicExists(topic))) {
        ctx.response.status = 404;
        ctx.response.body = { error: `Topic '${topic}' not found` };
        return;
      }

      const config = await topicManager.loadTopicConfig(topic);

      ctx.response.status = 200;
      ctx.response.body = {
        name: config.name,
        sequence: config.sequence,
        schemas: config.schemas,
      };
    } catch (error: unknown) {
      ctx.response.status = 500;
      ctx.response.body = {
        error: error instanceof Error ? error.message : String(error),
      };
    }
  });

  // GET /consumers - List all consumers
  router.get("/consumers", async (ctx: Context) => {
    try {
      const consumers = consumerManager.getAllConsumers();
      const consumerInfo = consumers.map((consumer) => ({
        id: consumer.id,
        callback: consumer.callback,
        topics: consumer.topics,
      }));

      ctx.response.status = 200;
      ctx.response.body = { consumers: consumerInfo };
    } catch (error: unknown) {
      ctx.response.status = 500;
      ctx.response.body = {
        error: error instanceof Error ? error.message : String(error),
      };
    }
  });

  // DELETE /consumers/:id - Unregister a consumer
  router.delete("/consumers/:id", async (ctx: RouterContext<"/consumers/:id">) => {
    try {
      const consumerId = ctx.params.id;

      if (!consumerId) {
        ctx.response.status = 400;
        ctx.response.body = { error: "Consumer ID is required" };
        return;
      }

      const removed = consumerManager.unregisterConsumer(consumerId);

      if (removed) {
        ctx.response.status = 200;
        ctx.response.body = { message: `Consumer ${consumerId} unregistered` };
      } else {
        ctx.response.status = 404;
        ctx.response.body = { error: `Consumer ${consumerId} not found` };
      }
    } catch (error: unknown) {
      ctx.response.status = 500;
      ctx.response.body = {
        error: error instanceof Error ? error.message : String(error),
      };
    }
  });

  // Health check endpoint
  router.get("/health", (ctx: Context) => {
    ctx.response.status = 200;
    ctx.response.body = {
      status: "healthy",
      consumers: consumerManager.getConsumerCount(),
      runningDispatchers: dispatcher.getRunningDispatchers(),
    };
  });

  return router;
}
