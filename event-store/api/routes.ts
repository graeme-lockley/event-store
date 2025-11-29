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
  TopicUpdate,
} from "../types.ts";

export function createRouter(
  topicManager: TopicManager,
  eventManager: EventManager,
  consumerManager: ConsumerManager,
  dispatcher: Dispatcher,
): Router {
  const router = new Router();

  const sendError = (
    ctx: Context,
    status: number,
    message: string,
    code?: string,
  ) => {
    ctx.response.status = status;
    ctx.response.body = code ? { error: message, code } : { error: message };
  };

  // POST /topics - Create a new topic and register associated schemas
  router.post("/topics", async (ctx: Context) => {
    try {
      const body = await ctx.request.body().value as TopicCreation;

      if (!body.name || !body.schemas || !Array.isArray(body.schemas)) {
        sendError(
          ctx,
          400,
          "Invalid request body. Required: name, schemas array",
          "INVALID_REQUEST",
        );
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
      sendError(
        ctx,
        400,
        error instanceof Error ? error.message : String(error),
        "TOPIC_CREATION_FAILED",
      );
    }
  });

  // POST /events - Publish one or more events
  router.post("/events", async (ctx: Context) => {
    try {
      const body = await ctx.request.body().value as EventRequest[];

      if (!Array.isArray(body) || body.length === 0) {
        sendError(
          ctx,
          400,
          "Request body must be a non-empty array of events",
          "INVALID_REQUEST",
        );
        return;
      }

      // Validate all events first
      for (const eventRequest of body) {
        if (
          !eventRequest.topic || !eventRequest.type ||
          eventRequest.payload === undefined
        ) {
          sendError(
            ctx,
            400,
            "Each event must have topic, type, and payload",
            "INVALID_EVENT",
          );
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
      sendError(
        ctx,
        400,
        error instanceof Error ? error.message : String(error),
        "EVENT_PUBLISH_FAILED",
      );
    }
  });

  // POST /consumers/register - Register a new consumer
  router.post("/consumers/register", async (ctx: Context) => {
    try {
      const body = await ctx.request.body().value as ConsumerRegistration;

      if (
        !body.callback || !body.topics || Object.keys(body.topics).length === 0
      ) {
        sendError(
          ctx,
          400,
          "Invalid request body. Required: callback URL and topics object",
          "INVALID_REQUEST",
        );
        return;
      }

      // Validate callback is a proper URL
      try {
        // Will throw for invalid URL
        new URL(body.callback);
      } catch {
        sendError(ctx, 400, "Invalid callback URL", "INVALID_CALLBACK");
        return;
      }

      // Validate that all topics exist
      for (const topic of Object.keys(body.topics)) {
        if (!(await topicManager.topicExists(topic))) {
          sendError(ctx, 400, `Topic '${topic}' not found`, "TOPIC_NOT_FOUND");
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
      sendError(
        ctx,
        400,
        error instanceof Error ? error.message : String(error),
        "CONSUMER_REGISTRATION_FAILED",
      );
    }
  });

  // GET /topics/:topic/events - Retrieve events from a topic
  router.get(
    "/topics/:topic/events",
    async (ctx: RouterContext<"/topics/:topic/events">) => {
      try {
        const topic = ctx.params.topic;

        if (!topic || !(await topicManager.topicExists(topic))) {
          sendError(ctx, 404, `Topic '${topic}' not found`, "TOPIC_NOT_FOUND");
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
        sendError(
          ctx,
          500,
          error instanceof Error ? error.message : String(error),
          "EVENTS_FETCH_FAILED",
        );
      }
    },
  );

  // GET /topics - List all topics
  router.get("/topics", async (ctx: Context) => {
    try {
      const topics = await topicManager.getAllTopicsWithDetails();
      ctx.response.status = 200;
      ctx.response.body = { topics };
    } catch (error: unknown) {
      sendError(
        ctx,
        500,
        error instanceof Error ? error.message : String(error),
        "TOPICS_LIST_FAILED",
      );
    }
  });

  // GET /topics/:topic - Get detailed information about a specific topic
  router.get("/topics/:topic", async (ctx: RouterContext<"/topics/:topic">) => {
    try {
      const topic = ctx.params.topic;

      if (!topic || !(await topicManager.topicExists(topic))) {
        sendError(ctx, 404, `Topic '${topic}' not found`, "TOPIC_NOT_FOUND");
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
      sendError(
        ctx,
        500,
        error instanceof Error ? error.message : String(error),
        "TOPIC_FETCH_FAILED",
      );
    }
  });

  // PUT /topics/:topic - Update schemas for an existing topic (additive only)
  router.put("/topics/:topic", async (ctx: RouterContext<"/topics/:topic">) => {
    try {
      const topic = ctx.params.topic;

      if (!topic || !(await topicManager.topicExists(topic))) {
        sendError(ctx, 404, `Topic '${topic}' not found`, "TOPIC_NOT_FOUND");
        return;
      }

      const body = await ctx.request.body().value as TopicUpdate;

      if (!body.schemas || !Array.isArray(body.schemas)) {
        sendError(
          ctx,
          400,
          "Invalid request body. Required: schemas array",
          "INVALID_REQUEST",
        );
        return;
      }

      await topicManager.updateSchemas(topic, body.schemas);

      ctx.response.status = 200;
      ctx.response.body = {
        message: `Topic '${topic}' schemas updated successfully`,
      };
    } catch (error: unknown) {
      const errorMessage = error instanceof Error
        ? error.message
        : String(error);
      // Check if it's a schema removal attempt
      if (errorMessage.includes("Cannot remove schemas")) {
        sendError(
          ctx,
          400,
          errorMessage,
          "SCHEMA_REMOVAL_NOT_ALLOWED",
        );
      } else {
        sendError(
          ctx,
          400,
          errorMessage,
          "TOPIC_UPDATE_FAILED",
        );
      }
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
      sendError(
        ctx,
        500,
        error instanceof Error ? error.message : String(error),
        "CONSUMERS_LIST_FAILED",
      );
    }
  });

  // DELETE /consumers/:id - Unregister a consumer
  router.delete(
    "/consumers/:id",
    async (ctx: RouterContext<"/consumers/:id">) => {
      try {
        const consumerId = ctx.params.id;

        if (!consumerId) {
          sendError(ctx, 400, "Consumer ID is required", "INVALID_REQUEST");
          return;
        }

        const removed = consumerManager.unregisterConsumer(consumerId);

        if (removed) {
          ctx.response.status = 200;
          ctx.response.body = {
            message: `Consumer ${consumerId} unregistered`,
          };
        } else {
          sendError(
            ctx,
            404,
            `Consumer ${consumerId} not found`,
            "CONSUMER_NOT_FOUND",
          );
        }
      } catch (error: unknown) {
        sendError(
          ctx,
          500,
          error instanceof Error ? error.message : String(error),
          "CONSUMER_DELETE_FAILED",
        );
      }
    },
  );

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
