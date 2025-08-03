import { assertEquals, assertExists } from "$std/assert/mod.ts";
import { describe, it } from "$std/testing/bdd.ts";
import {
  aggregateTopicStats,
  createEventStoreClient,
  type LegacyEventStoreConfig,
} from "../../../utils/eventStore.ts";

const EVENT_STORE_CLIENT_CONFIG: LegacyEventStoreConfig = {
  name: "Test Store",
  url: "http://localhost",
  port: 8000,
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
        {
          id: "1",
          timestamp: "2024-01-01T10:00:00Z",
          type: "test",
          payload: {},
        },
        {
          id: "2",
          timestamp: "2024-01-01T11:00:00Z",
          type: "test",
          payload: {},
        },
        {
          id: "3",
          timestamp: "2024-01-02T10:00:00Z",
          type: "test",
          payload: {},
        },
        {
          id: "4",
          timestamp: "2024-01-15T10:00:00Z",
          type: "test",
          payload: {},
        }, // Changed to Jan 15 (different week)
        {
          id: "5",
          timestamp: "2024-02-01T10:00:00Z",
          type: "test",
          payload: {},
        },
      ];

      const result = aggregateTopicStats(events);

      assertEquals(result.perDay["2024-01-01"], 2);
      assertEquals(result.perDay["2024-01-02"], 1);
      assertEquals(result.perDay["2024-01-15"], 1);
      assertEquals(result.perDay["2024-02-01"], 1);

      // Week boundaries using UTC calculation
      assertEquals(result.perWeek["2023-12-31"], 3); // Jan 1, 2, and 15, 2024 (all Mondays/Tuesdays)
      assertEquals(result.perWeek["2024-01-14"], 1); // Jan 15, 2024 (Monday)
      assertEquals(result.perWeek["2024-01-28"], 1); // Feb 1, 2024 (Thursday)

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
