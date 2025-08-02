import { Handlers } from "$fresh/server.ts";
import { getStores } from "../../utils/storeConfig.ts";
import { createEventStoreClient, type LegacyEventStoreConfig } from "../../utils/eventStore.ts";

export const handler: Handlers = {
  async GET(req) {
    const url = new URL(req.url);
    const storeName = url.searchParams.get("store");
    if (!storeName) {
      return new Response("Missing store param", { status: 400 });
    }
    const stores = await getStores();
    const store = stores.find(s => s.name === storeName);
    if (!store) {
      return new Response("Store not found", { status: 404 });
    }
    try {
      const client = createEventStoreClient(store as LegacyEventStoreConfig);
      const health = await client.getHealth();
      const topics = await client.getTopics();
      const consumers = await client.getConsumers();
      
      // Return the structure expected by DashboardStores component
      return Response.json({
        health: {
          status: health.status,
          consumers: health.consumers,
          runningDispatchers: health.runningDispatchers
        },
        topics: topics,
        consumers: consumers,
        error: null
      });
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      return Response.json({ 
        health: { status: "unhealthy", consumers: 0, runningDispatchers: [] },
        topics: [],
        consumers: [],
        error: message 
      }, { status: 500 });
    }
  }
}; 