import { dirname, ensureDir, join, walk } from "../deps.ts";
import { Event, EventRequest, EventsQuery } from "../types.ts";
import { TopicManager } from "./topics.ts";

export class EventManager {
  private dataDir: string;
  private topicManager: TopicManager;

  constructor(topicManager: TopicManager) {
    this.topicManager = topicManager;
    this.dataDir = Deno.env.get("DATA_DIR") || "data";
  }

  /**
   * Store a single event
   */
  async storeEvent(eventRequest: EventRequest): Promise<string> {
    // Validate topic exists
    if (!(await this.topicManager.topicExists(eventRequest.topic))) {
      throw new Error(`Topic '${eventRequest.topic}' not found`);
    }

    // Validate event against schema
    this.topicManager.validateEvent(
      eventRequest.topic,
      eventRequest.type,
      eventRequest.payload,
    );

    // Get next event ID
    const eventId = await this.topicManager.getNextEventId(eventRequest.topic);

    // Create event object
    const event: Event = {
      id: eventId,
      timestamp: new Date().toISOString(),
      type: eventRequest.type,
      payload: eventRequest.payload,
    };

    // Determine file path
    const filePath = this.getEventFilePath(eventRequest.topic, eventId);

    // Ensure directory exists
    await ensureDir(dirname(filePath));

    // Write event to file
    await Deno.writeTextFile(filePath, JSON.stringify(event, null, 2));

    return eventId;
  }

  /**
   * Store multiple events in a batch
   */
  async storeEvents(eventRequests: EventRequest[]): Promise<string[]> {
    if (!Array.isArray(eventRequests) || eventRequests.length === 0) {
      throw new Error("Events must be a non-empty array");
    }

    const eventIds: string[] = [];

    // Validate all events first
    for (const eventRequest of eventRequests) {
      if (!(await this.topicManager.topicExists(eventRequest.topic))) {
        throw new Error(`Topic '${eventRequest.topic}' not found`);
      }

      this.topicManager.validateEvent(
        eventRequest.topic,
        eventRequest.type,
        eventRequest.payload,
      );
    }

    // Store all events (validation already performed)
    for (const eventRequest of eventRequests) {
      const eventId = await this.storeEventWithoutValidation(eventRequest);
      eventIds.push(eventId);
    }

    return eventIds;
  }

  /**
   * Store a single event without re-validating (assumes prior validation)
   */
  private async storeEventWithoutValidation(
    eventRequest: EventRequest,
  ): Promise<string> {
    // Get next event ID
    const eventId = await this.topicManager.getNextEventId(eventRequest.topic);

    // Create event object
    const event: Event = {
      id: eventId,
      timestamp: new Date().toISOString(),
      type: eventRequest.type,
      payload: eventRequest.payload,
    };

    // Determine file path
    const filePath = this.getEventFilePath(eventRequest.topic, eventId);

    // Ensure directory exists
    await ensureDir(dirname(filePath));

    // Write event to file
    await Deno.writeTextFile(filePath, JSON.stringify(event, null, 2));

    return eventId;
  }

  /**
   * Get event file path based on topic, date, and grouping
   */
  private getEventFilePath(topic: string, eventId: string): string {
    const date = new Date().toISOString().split("T")[0]; // YYYY-MM-DD
    const sequence = parseInt(eventId.split("-").pop() || "0");
    const group = Math.floor(sequence / 1000).toString().padStart(4, "0");

    const fileName = `${eventId}.json`;
    return join(this.dataDir, topic, date, group, fileName);
  }

  /**
   * Read a single event from file
   */
  async readEvent(topic: string, eventId: string): Promise<Event | null> {
    try {
      // Try to find the event file by walking the directory structure
      const eventPath = await this.findEventFile(topic, eventId);
      if (!eventPath) {
        return null;
      }

      const content = await Deno.readTextFile(eventPath);
      return JSON.parse(content) as Event;
    } catch {
      return null;
    }
  }

  /**
   * Find event file by walking the directory structure
   */
  private async findEventFile(
    topic: string,
    eventId: string,
  ): Promise<string | null> {
    const topicDir = join(this.dataDir, topic);

    try {
      for await (
        const entry of walk(topicDir, {
          includeDirs: false,
          match: [new RegExp(`${eventId}\\.json$`)],
        })
      ) {
        return entry.path;
      }
    } catch {
      // Directory doesn't exist
    }

    return null;
  }

  /**
   * Get events from a topic with optional filtering
   */
  async getEvents(topic: string, query: EventsQuery = {}): Promise<Event[]> {
    const events: Event[] = [];
    const topicDir = query.date
      ? join(this.dataDir, topic, query.date)
      : join(this.dataDir, topic);

    try {
      // Walk through all event files in the chosen directory (topic or date-specific)
      for await (
        const entry of walk(topicDir, {
          includeDirs: false,
          match: [/\.json$/],
        })
      ) {
        try {
          const content = await Deno.readTextFile(entry.path);
          const event: Event = JSON.parse(content);

          // Apply filters
          if (
            query.sinceEventId &&
            this.compareEventIds(event.id, query.sinceEventId) <= 0
          ) {
            continue;
          }

          if (query.date) {
            const eventDate = event.timestamp.split("T")[0];
            if (eventDate !== query.date) {
              continue;
            }
          }

          events.push(event);
        } catch {
          // Skip invalid event files
          continue;
        }
      }

      // Sort by event ID
      events.sort((a, b) => this.compareEventIds(a.id, b.id));

      // Apply limit
      if (query.limit) {
        return events.slice(0, query.limit);
      }

      return events;
    } catch {
      // Topic directory doesn't exist
      return [];
    }
  }

  /**
   * Compare event IDs for sorting
   */
  private compareEventIds(id1: string, id2: string): number {
    const parts1 = id1.split("-");
    const parts2 = id2.split("-");

    // Compare topic names first
    const topic1 = parts1.slice(0, -1).join("-");
    const topic2 = parts2.slice(0, -1).join("-");

    if (topic1 !== topic2) {
      return topic1.localeCompare(topic2);
    }

    // Compare sequence numbers
    const seq1 = parseInt(parts1[parts1.length - 1]);
    const seq2 = parseInt(parts2[parts2.length - 1]);

    return seq1 - seq2;
  }

  /**
   * Get the latest event ID for a topic
   */
  async getLatestEventId(topic: string): Promise<string | null> {
    const events = await this.getEvents(topic);
    return events.length > 0 ? events[events.length - 1].id : null;
  }
}
