import { Application } from "./deps.ts";
import { TopicManager } from "./core/topics.ts";
import { EventManager } from "./core/events.ts";
import { ConsumerManager } from "./core/consumers.ts";
import { Dispatcher } from "./core/dispatcher.ts";
import { createRouter } from "./api/routes.ts";

// Get configuration from environment variables
const port = parseInt(Deno.env.get("PORT") || "8000");
const dataDir = Deno.env.get("DATA_DIR") || "./data";
const configDir = Deno.env.get("CONFIG_DIR") || "./config";

// Initialize core components
const topicManager = await TopicManager.create();
const eventManager = new EventManager(topicManager);
const consumerManager = new ConsumerManager(eventManager);
const dispatcher = new Dispatcher(consumerManager, eventManager);

// Create and configure the application
const app = new Application();
const router = createRouter(
  topicManager,
  eventManager,
  consumerManager,
  dispatcher,
);

// Add middleware
app.use(async (ctx, next) => {
  const start = Date.now();
  await next();
  const ms = Date.now() - start;
  console.log(`${ctx.request.method} ${ctx.request.url.pathname} - ${ms}ms`);
});

// Basic body size limit for POST endpoints
app.use(async (ctx, next) => {
  if (ctx.request.hasBody && ctx.request.method === "POST") {
    const contentLength = Number(
      ctx.request.headers.get("content-length") || "0",
    );
    const maxBytes = Number(Deno.env.get("MAX_BODY_BYTES") || "1048576"); // 1MB default
    if (contentLength > maxBytes) {
      ctx.response.status = 413;
      ctx.response.body = {
        error: "Payload too large",
        code: "PAYLOAD_TOO_LARGE",
      };
      return;
    }
  }
  await next();
});

// Simple in-memory rate limiter (per IP per route)
const rateBuckets = new Map<string, { count: number; resetAt: number }>();
const RATE_LIMIT = Number(Deno.env.get("RATE_LIMIT_PER_MINUTE") || "600");
app.use(async (ctx, next) => {
  const ip = ctx.request.ip ?? "unknown";
  const key = `${ip}:${ctx.request.url.pathname}`;
  const now = Date.now();
  const minute = 60_000;
  const bucket = rateBuckets.get(key) || { count: 0, resetAt: now + minute };
  if (now > bucket.resetAt) {
    bucket.count = 0;
    bucket.resetAt = now + minute;
  }
  bucket.count += 1;
  rateBuckets.set(key, bucket);
  if (bucket.count > RATE_LIMIT) {
    ctx.response.status = 429;
    ctx.response.headers.set(
      "Retry-After",
      Math.ceil((bucket.resetAt - now) / 1000).toString(),
    );
    ctx.response.body = { error: "Too many requests", code: "RATE_LIMITED" };
    return;
  }
  await next();
});

// Use router
app.use(router.routes());
app.use(router.allowedMethods());

// Graceful shutdown handler
const shutdown = () => {
  console.log("\nShutting down gracefully...");
  dispatcher.stopAllDispatchers();
  Deno.exit(0);
};

// Handle shutdown signals
Deno.addSignalListener("SIGINT", shutdown);
Deno.addSignalListener("SIGTERM", shutdown);

// Start the server
console.log(`🚀 Event Store starting on port ${port}`);
console.log(`📁 Data directory: ${dataDir}`);
console.log(`📁 Config directory: ${configDir}`);
console.log(`📖 API Endpoints:`);
console.log(`   POST /topics - Create a topic with schemas`);
console.log(`   GET  /topics - List all topics`);
console.log(`   GET  /topics/:topic - Get topic details`);
console.log(`   POST /events - Publish events`);
console.log(`   POST /consumers/register - Register a consumer`);
console.log(`   GET  /topics/:topic/events - Retrieve events`);
console.log(`   GET  /consumers - List consumers`);
console.log(`   DELETE /consumers/:id - Unregister a consumer`);
console.log(`   GET  /health - Health check`);
console.log(`\n💡 Example usage:`);
console.log(`   curl -X POST http://localhost:${port}/topics \\`);
console.log(`     -H "Content-Type: application/json" \\`);
console.log(
  `     -d '{"name":"user-events","schemas":[{"type":"user.created","$schema":"https://json-schema.org/draft/2020-12/schema","properties":{"id":{"type":"string"},"name":{"type":"string"}},"required":["id","name"]}]}'`,
);

await app.listen({ port });
