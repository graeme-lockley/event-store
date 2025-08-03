import { Handlers } from "$fresh/server.ts";
import { getStores } from "../../utils/storeConfig.ts";
import {
  aggregateTopicStats,
  createEventStoreClient,
  type LegacyEventStoreConfig,
} from "../../utils/eventStore.ts";

function getDateKey(date: Date) {
  return date.toISOString().slice(0, 10); // YYYY-MM-DD
}

function getWeekKey(date: Date) {
  const d = new Date(date);
  d.setHours(0, 0, 0, 0);
  // Get Monday of the week
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7));
  return d.toISOString().slice(0, 10);
}

function getMonthKey(date: Date) {
  return date.toISOString().slice(0, 7); // YYYY-MM
}

export const handler: Handlers = {
  async GET(req) {
    const url = new URL(req.url);
    const storeName = url.searchParams.get("store");
    const topic = url.searchParams.get("topic");
    if (!storeName || !topic) {
      return new Response("Missing store or topic param", { status: 400 });
    }
    const stores = await getStores();
    const store = stores.find((s) => s.name === storeName);
    if (!store) {
      return new Response("Store not found", { status: 404 });
    }
    try {
      const client = createEventStoreClient(store as LegacyEventStoreConfig);
      // Get all events for the topic
      const events: any[] = await client.getEvents(topic, { limit: 10000 });
      // Aggregate counts using utility
      const { perDay, perWeek, perMonth, totalSize } = aggregateTopicStats(
        events,
      );
      // Get consumers
      const consumers = await client.getConsumers();
      const topicConsumers = consumers.filter((c: any) =>
        Object.keys(c.topics).includes(topic)
      );
      // Return stats
      return Response.json({
        perDay,
        perWeek,
        perMonth,
        totalEvents: events.length,
        consumerCount: topicConsumers.length,
        storageBytes: totalSize,
      });
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      return Response.json({ error: message }, { status: 500 });
    }
  },
};
