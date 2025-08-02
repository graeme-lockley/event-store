import { Handlers } from "$fresh/server.ts";
import { getStores } from "../../../utils/storeConfig.ts";
import { createEventStoreClient, type LegacyEventStoreConfig } from "../../../utils/eventStore.ts";

// Factory function to create the handler with dependency injection
export function createEventsExportHandler(clientFactory?: (config: LegacyEventStoreConfig) => any) {
  return {
    async GET(req: Request) {
      const url = new URL(req.url);
      const storeName = url.searchParams.get("store");
      const topic = url.searchParams.get("topic");
      const eventType = url.searchParams.get("eventType");
      const dateFrom = url.searchParams.get("dateFrom");
      const dateTo = url.searchParams.get("dateTo");
      const search = url.searchParams.get("search");
      const format = url.searchParams.get("format") || "json";

      if (!storeName) {
        return new Response("Missing store parameter", { status: 400 });
      }

      if (!["json", "csv"].includes(format)) {
        return new Response("Invalid format. Use 'json' or 'csv'", { status: 400 });
      }

      const stores = await getStores();
      const store = stores.find(s => s.name === storeName);
      if (!store) {
        return new Response("Store not found", { status: 404 });
      }

      try {
        const client = clientFactory ? clientFactory(store) : createEventStoreClient(store as LegacyEventStoreConfig);
        
        // Get all topics if no specific topic is requested
        const topics = topic ? [topic] : await client.getTopics();
        
        let allEvents: any[] = [];

        // Collect events from all relevant topics
        for (const topicName of topics) {
          try {
            const events = await client.getEvents(topicName, { limit: 10000 });
            
            // Add topic information to each event
            const eventsWithTopic = events.map((event: any) => ({
              ...event,
              topic: topicName
            }));
            
            allEvents.push(...eventsWithTopic);
          } catch (error) {
            console.warn(`Failed to load events for topic ${topicName}:`, error);
          }
        }

        // Apply filters (same logic as the main events endpoint)
        let filteredEvents = allEvents;

        if (eventType) {
          filteredEvents = filteredEvents.filter(event => event.type === eventType);
        }

        if (dateFrom) {
          const fromDate = new Date(dateFrom);
          filteredEvents = filteredEvents.filter(event => new Date(event.timestamp) >= fromDate);
        }

        if (dateTo) {
          const toDate = new Date(dateTo);
          toDate.setHours(23, 59, 59, 999);
          filteredEvents = filteredEvents.filter(event => new Date(event.timestamp) <= toDate);
        }

        if (search) {
          const searchLower = search.toLowerCase();
          filteredEvents = filteredEvents.filter(event => {
            const payloadStr = JSON.stringify(event.payload).toLowerCase();
            return payloadStr.includes(searchLower);
          });
        }

        // Sort by timestamp (newest first)
        filteredEvents.sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime());

        let content: string;
        let contentType: string;
        let filename: string;

        if (format === "json") {
          content = JSON.stringify(filteredEvents, null, 2);
          contentType = "application/json";
          filename = `events-${new Date().toISOString().split("T")[0]}.json`;
        } else {
          // CSV format
          const headers = ["id", "topic", "type", "timestamp", "payload"];
          const csvRows = [headers.join(",")];
          
          for (const event of filteredEvents) {
            const row = [
              event.id,
              event.topic || "",
              event.type,
              event.timestamp,
              JSON.stringify(event.payload).replace(/"/g, '""') // Escape quotes for CSV
            ];
            csvRows.push(row.join(","));
          }
          
          content = csvRows.join("\n");
          contentType = "text/csv";
          filename = `events-${new Date().toISOString().split("T")[0]}.csv`;
        }

        return new Response(content, {
          headers: {
            "Content-Type": contentType,
            "Content-Disposition": `attachment; filename="${filename}"`,
          },
        });
      } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        return Response.json({ error: message }, { status: 500 });
      }
    }
  };
}

// Default handler using the real EventStoreClient
export const handler: Handlers = createEventsExportHandler(); 