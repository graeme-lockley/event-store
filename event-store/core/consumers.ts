import { uuidv4 } from "../deps.ts";
import { Consumer, ConsumerRegistration, Event } from "../types.ts";
import { EventManager } from "./events.ts";

export class ConsumerManager {
  private consumers = new Map<string, Consumer>();
  private eventManager: EventManager;

  constructor(eventManager: EventManager) {
    this.eventManager = eventManager;
  }

  /**
   * Register a new consumer
   */
  registerConsumer(registration: ConsumerRegistration): string {
    const consumerId = uuidv4();

    const consumer: Consumer = {
      id: consumerId,
      callback: registration.callback,
      topics: { ...registration.topics },
      nudge: async () => {
        await this.deliverPendingEvents(consumerId);
      },
    };

    this.consumers.set(consumerId, consumer);
    console.log(
      `Registered consumer ${consumerId} for topics: ${
        Object.keys(registration.topics).join(", ")
      }`,
    );

    return consumerId;
  }

  /**
   * Unregister a consumer
   */
  unregisterConsumer(consumerId: string): boolean {
    const removed = this.consumers.delete(consumerId);
    if (removed) {
      console.log(`Unregistered consumer ${consumerId}`);
    }
    return removed;
  }

  /**
   * Get all consumers for a topic
   */
  getConsumersForTopic(topic: string): Consumer[] {
    return Array.from(this.consumers.values()).filter((consumer) =>
      topic in consumer.topics
    );
  }

  /**
   * Nudge all consumers for a topic when a new event is published
   */
  async nudgeConsumersForTopic(topic: string): Promise<void> {
    const consumers = this.getConsumersForTopic(topic);

    // Trigger nudge for each consumer asynchronously
    const nudgePromises = consumers.map((consumer) =>
      consumer.nudge().catch((error) => {
        console.error(`Error nudging consumer ${consumer.id}:`, error);
      })
    );

    await Promise.all(nudgePromises);
  }

  /**
   * Deliver pending events to a consumer
   */
  private async deliverPendingEvents(consumerId: string): Promise<void> {
    const consumer = this.consumers.get(consumerId);
    if (!consumer) {
      return; // Consumer was already removed
    }

    const eventsToDeliver: Event[] = [];

    // Check each topic the consumer is interested in
    for (const [topic, lastEventId] of Object.entries(consumer.topics)) {
      try {
        // Get events since the last consumed event
        const events = await this.eventManager.getEvents(topic, {
          sinceEventId: lastEventId || undefined,
        });

        if (events.length > 0) {
          eventsToDeliver.push(...events);

          // Update the last consumed event ID
          const latestEventId = events[events.length - 1].id;
          consumer.topics[topic] = latestEventId;
        }
      } catch (error) {
        console.error(`Error fetching events for topic ${topic}:`, error);
      }
    }

    if (eventsToDeliver.length === 0) {
      return; // No events to deliver
    }

    // Sort events by ID to ensure proper ordering
    eventsToDeliver.sort((a, b) => this.compareEventIds(a.id, b.id));

    // Deliver events to consumer
    try {
      const response = await fetch(consumer.callback, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          consumerId: consumer.id,
          events: eventsToDeliver,
        }),
        signal: AbortSignal.timeout(30000), // 30 second timeout
      });

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }

      console.log(
        `Delivered ${eventsToDeliver.length} events to consumer ${consumerId}`,
      );
    } catch (error) {
      console.error(
        `Failed to deliver events to consumer ${consumerId}:`,
        error,
      );

      // Remove consumer on delivery failure
      this.unregisterConsumer(consumerId);
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
   * Get all registered consumers
   */
  getAllConsumers(): Consumer[] {
    return Array.from(this.consumers.values());
  }

  /**
   * Get consumer by ID
   */
  getConsumer(consumerId: string): Consumer | undefined {
    return this.consumers.get(consumerId);
  }

  /**
   * Get consumer count
   */
  getConsumerCount(): number {
    return this.consumers.size;
  }
}
