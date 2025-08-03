import { assertEquals } from "$std/assert/mod.ts";
import { afterAll, beforeAll, describe, it } from "$std/testing/bdd.ts";
import { startIntegrationServers } from "../../helpers/integration_server.ts";

describe("Test Connection API", () => {
  let servers: Awaited<ReturnType<typeof startIntegrationServers>>;
  let baseUrl: string;

  beforeAll(async () => {
    // Start both Event Store and Admin UI servers
    servers = await startIntegrationServers();
    baseUrl = servers.adminUI.url;
  });

  afterAll(async () => {
    // Stop both servers and clean up
    await servers.stop();
  });

  describe("POST /api/test-connection", () => {
    it("should test valid Event Store connection", async () => {
      const testData = {
        name: "Test Store",
        url: servers.eventStore.url,
        port: 18000,
      };

      const response = await fetch(`${baseUrl}/api/test-connection`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(testData),
      });

      assertEquals(response.status, 200);

      const data = await response.json();
      assertEquals(typeof data.success, "boolean");
      assertEquals(typeof data.message, "string");
    });

    it("should handle invalid Event Store URL", async () => {
      const testData = {
        name: "Invalid Store",
        url: "http://localhost:9999", // Non-existent port
        port: 9999,
      };

      const response = await fetch(`${baseUrl}/api/test-connection`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(testData),
      });

      assertEquals(response.status, 200);

      const data = await response.json();
      assertEquals(data.success, false);
      assertEquals(typeof data.message, "string");
    });

    it("should handle malformed request data", async () => {
      const testData = {
        name: "Test Store",
        // Missing url and port
      };

      const response = await fetch(`${baseUrl}/api/test-connection`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(testData),
      });

      assertEquals(response.status, 400);

      const data = await response.json();
      assertEquals(data.success, false);
      assertEquals(typeof data.error, "string");
    });

    it("should handle invalid JSON", async () => {
      const response = await fetch(`${baseUrl}/api/test-connection`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: "invalid json",
      });

      assertEquals(response.status, 400);

      const data = await response.json();
      assertEquals(data.success, false);
      assertEquals(typeof data.error, "string");
    });

    it("should handle missing Content-Type header", async () => {
      const testData = {
        name: "Test Store",
        url: servers.eventStore.url,
        port: 18000,
      };

      const response = await fetch(`${baseUrl}/api/test-connection`, {
        method: "POST",
        body: JSON.stringify(testData),
      });

      assertEquals(response.status, 400);

      const data = await response.json();
      assertEquals(data.success, false);
      assertEquals(typeof data.error, "string");
    });
  });
});
