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
