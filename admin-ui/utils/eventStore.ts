// Import the Event Store client library
import { EventStoreClient as BaseEventStoreClient, type EventStoreConfig, type Topic, type Schema, type Event, type Consumer, type HealthStatus } from "../../event-store/client.ts";

// Re-export types
export type { EventStoreConfig, Topic, Schema, Event, Consumer, HealthStatus };

// Legacy interface for backward compatibility
export interface LegacyEventStoreConfig {
  name: string;
  url: string;
  port: number;
}

// Adapter to convert legacy config to new client config
export function createEventStoreClient(config: LegacyEventStoreConfig): BaseEventStoreClient {
  return new BaseEventStoreClient({
    baseUrl: `${config.url}:${config.port}`,
    timeout: 30000,
    retries: 30,
    retryDelay: 100,
  });
}

// Test-optimized client creation with shorter timeouts
export function createTestEventStoreClient(config: LegacyEventStoreConfig): BaseEventStoreClient {
  return new BaseEventStoreClient({
    baseUrl: `${config.url}:${config.port}`,
    timeout: 5000, // 5 seconds for tests
    retries: 1, // Only 1 retry for tests
    retryDelay: 100, // 100ms delay for tests
  });
}

// Export the client class
export { BaseEventStoreClient as EventStoreClient };

// Utility: Aggregate topic statistics from events
export function aggregateTopicStats(events: Event[]) {
  function getDateKey(date: Date) {
    return date.toISOString().slice(0, 10); // YYYY-MM-DD
  }
  function getWeekKey(date: Date) {
    const d = new Date(date);
    d.setHours(0, 0, 0, 0);
    // Set to previous Sunday
    d.setDate(d.getDate() - d.getDay());
    return d.toISOString().slice(0, 10);
  }
  function getMonthKey(date: Date) {
    return date.toISOString().slice(0, 7); // YYYY-MM
  }
  const perDay: Record<string, number> = {};
  const perWeek: Record<string, number> = {};
  const perMonth: Record<string, number> = {};
  let totalSize = 0;
  for (const event of events) {
    const date = new Date(event.timestamp);
    const day = getDateKey(date);
    const week = getWeekKey(date);
    const month = getMonthKey(date);
    perDay[day] = (perDay[day] || 0) + 1;
    perWeek[week] = (perWeek[week] || 0) + 1;
    perMonth[month] = (perMonth[month] || 0) + 1;
    totalSize += JSON.stringify(event).length;
  }
  return { perDay, perWeek, perMonth, totalSize };
} 