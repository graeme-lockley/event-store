import { assertEquals, assertExists } from "$std/assert/mod.ts";
import { describe, it } from "$std/testing/bdd.ts";
import { createEventStoreClient, type LegacyEventStoreConfig, aggregateTopicStats } from "../../../utils/eventStore.ts";

const EVENT_STORE_CLIENT_CONFIG: LegacyEventStoreConfig = {
  name: "Test Store",
  url: "http://localhost",
  port: 8000
};

describe("EventStoreClient", () => {
  describe("constructor", () => {
    it("should create client with config", () => {
      const client = createEventStoreClient(EVENT_STORE_CLIENT_CONFIG);
      assertExists(client);
    });
  });

  describe("aggregateTopicStats", () => {
    it("should aggregate perDay, perWeek, perMonth, and totalSize correctly", () => {
      const events = [
        { id: "1", timestamp: "2024-01-01T10:00:00Z", type: "test", payload: {} },
        { id: "2", timestamp: "2024-01-01T11:00:00Z", type: "test", payload: {} },
        { id: "3", timestamp: "2024-01-02T10:00:00Z", type: "test", payload: {} },
        { id: "4", timestamp: "2024-01-08T10:00:00Z", type: "test", payload: {} },
        { id: "5", timestamp: "2024-02-01T10:00:00Z", type: "test", payload: {} },
      ];

      const result = aggregateTopicStats(events);

      assertEquals(result.perDay["2024-01-01"], 2);
      assertEquals(result.perDay["2024-01-02"], 1);
      assertEquals(result.perDay["2024-01-08"], 1);
      assertEquals(result.perDay["2024-02-01"], 1);

      assertEquals(result.perWeek["2023-12-30"], 3); // Week starting Dec 30, 2023 (Sunday)
      assertEquals(result.perWeek["2024-01-06"], 1); // Week starting Jan 6, 2024 (Sunday)
      assertEquals(result.perWeek["2024-01-27"], 1); // Week starting Jan 27, 2024 (Sunday)

      assertEquals(result.perMonth["2024-01"], 4);
      assertEquals(result.perMonth["2024-02"], 1);

      assertEquals(result.totalSize > 0, true);
    });

    it("should handle empty event array", () => {
      const result = aggregateTopicStats([]);

      assertEquals(Object.keys(result.perDay).length, 0);
      assertEquals(Object.keys(result.perWeek).length, 0);
      assertEquals(Object.keys(result.perMonth).length, 0);
      assertEquals(result.totalSize, 0);
    });
  });
}); 