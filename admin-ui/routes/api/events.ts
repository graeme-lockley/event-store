import { Handlers } from "$fresh/server.ts";
import { getStores } from "../../utils/storeConfig.ts";
import {
  createEventStoreClient,
  type LegacyEventStoreConfig,
} from "../../utils/eventStore.ts";

// Factory function to create the handler with dependency injection
export function createEventsHandler(
  clientFactory?: (config: LegacyEventStoreConfig) => any,
) {
  return {
    async GET(req: Request) {
      const url = new URL(req.url);
      const storeName = url.searchParams.get("store");
      const topic = url.searchParams.get("topic");
      const eventType = url.searchParams.get("eventType");
      const dateFrom = url.searchParams.get("dateFrom");
      const dateTo = url.searchParams.get("dateTo");
      const search = url.searchParams.get("search");
      const page = parseInt(url.searchParams.get("page") || "1");
      const pageSize = parseInt(url.searchParams.get("pageSize") || "20");

      if (!storeName) {
        return new Response("Missing store parameter", { status: 400 });
      }

      const stores = await getStores();
      const store = stores.find((s) => s.name === storeName);
      if (!store) {
        return new Response("Store not found", { status: 404 });
      }

      try {
        const client = clientFactory
          ? clientFactory(store)
          : createEventStoreClient(store as LegacyEventStoreConfig);

        // Get all topics if no specific topic is requested
        const topics = topic ? [topic] : await client.getTopics();

        let allEvents: any[] = [];
        const eventTypes = new Set<string>();

        // Collect events from all relevant topics
        for (const topicName of topics) {
          try {
            const events = await client.getEvents(topicName, { limit: 10000 });

            // Add topic information to each event
            const eventsWithTopic = events.map((event: any) => ({
              ...event,
              topic: topicName,
            }));

            allEvents.push(...eventsWithTopic);

            // Collect unique event types
            events.forEach((event: any) => eventTypes.add(event.type));
          } catch (error) {
            console.warn(
              `Failed to load events for topic ${topicName}:`,
              error,
            );
          }
        }

        // Apply filters
        let filteredEvents = allEvents;

        // Filter by event type
        if (eventType) {
          filteredEvents = filteredEvents.filter((event) =>
            event.type === eventType
          );
        }

        // Filter by date range
        if (dateFrom) {
          const fromDate = new Date(dateFrom);
          filteredEvents = filteredEvents.filter((event) =>
            new Date(event.timestamp) >= fromDate
          );
        }

        if (dateTo) {
          const toDate = new Date(dateTo);
          toDate.setHours(23, 59, 59, 999); // End of day
          filteredEvents = filteredEvents.filter((event) =>
            new Date(event.timestamp) <= toDate
          );
        }

        // Filter by search term (search in payload)
        if (search) {
          const searchLower = search.toLowerCase();
          filteredEvents = filteredEvents.filter((event) => {
            const payloadStr = JSON.stringify(event.payload).toLowerCase();
            return payloadStr.includes(searchLower);
          });
        }

        // Sort by timestamp (newest first)
        filteredEvents.sort((a, b) =>
          new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()
        );

        const total = filteredEvents.length;
        const startIndex = (page - 1) * pageSize;
        const endIndex = startIndex + pageSize;
        const paginatedEvents = filteredEvents.slice(startIndex, endIndex);

        return Response.json({
          events: paginatedEvents,
          total,
          page,
          pageSize,
          eventTypes: Array.from(eventTypes).sort(),
        });
      } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        return Response.json({ error: message }, { status: 500 });
      }
    },
  };
}

// Default handler using the real EventStoreClient
export const handler: Handlers = createEventsHandler();
