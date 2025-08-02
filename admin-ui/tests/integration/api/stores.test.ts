import { assertEquals } from "$std/assert/mod.ts";
import { describe, it, beforeAll, afterAll } from "$std/testing/bdd.ts";
import { startIntegrationServers } from "../../helpers/integration_server.ts";
import { type LegacyEventStoreConfig } from "../../../utils/eventStore.ts";

async function getStores(baseUrl: string): Promise<LegacyEventStoreConfig[]> {
  const response = await fetch(`${baseUrl}/api/stores`);
  assertEquals(response.status, 200);

  const data = await response.json();
  assertEquals(Array.isArray(data), true);

  return data;
}

async function addStore(baseUrl: string, store: LegacyEventStoreConfig): Promise<boolean> {
  const response = await fetch(`${baseUrl}/api/stores`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(store),
  });

  const result = await response.json();
  if (response.status !== 201) {
    return false;
  } else {
    return result.success;
  }
}

async function deleteStore(baseUrl: string, storeName: string): Promise<boolean> {
  const response = await fetch(`${baseUrl}/api/stores/${encodeURIComponent(storeName)}`, {
    method: "DELETE",
  });

  const result = await response.json();
  if (response.status === 200) {
    return result.success;
  } else {
    return false;
  }
}

describe("Stores API", () => {
  let servers: Awaited<ReturnType<typeof startIntegrationServers>>;
  let baseUrl: string;

  beforeAll(async () => {
    servers = await startIntegrationServers();
    baseUrl = servers.adminUI.url;
  });

  afterAll(async () => {
    await servers.stop();
  });

  describe("GET /api/stores", () => {
    it("should return list of stores", async () => {
      const stores = await getStores(baseUrl);
    
      assertEquals(stores.length, 2);
    });
  });

  describe("POST /api/stores", () => {
    const storeName = "Test Integration Store";
    const storeOtherName = "Test Integration Store 2";

    it("should add a new store", async () => {
      const newStore: LegacyEventStoreConfig = {
        name: storeName,
        url: servers.eventStore.url,
        port: 18000
      };
  
      assertEquals(await addStore(baseUrl, newStore), true);
    });

    it("should reject duplicate store names", async () => {
      const duplicateStore: LegacyEventStoreConfig = {
        name: storeName, // Same name as above
        url: servers.eventStore.url,
        port: 18001
      };

      assertEquals(await addStore(baseUrl, duplicateStore), false);
    });

    it("should reject duplicate store names", async () => {
      const duplicateStore: LegacyEventStoreConfig = {
        name: storeOtherName,
        url: servers.eventStore.url,
        port: 18000
      };

      assertEquals(await addStore(baseUrl, duplicateStore), false);
    });
  });

  describe("DELETE /api/stores/:name", () => {
    it("should remove an existing store and handle non-existent store", async () => {
      const storeName = "Test Delete Integration Store";

      const stores = await getStores(baseUrl);

      // First delete should fail as this store doesn't exist
      assertEquals(await deleteStore(baseUrl, storeName), false);

      // Add the store
      assertEquals(await addStore(baseUrl, {
        name: storeName,
        url: servers.eventStore.url,
        port: 18001
      }), true);

      assertEquals(stores.length + 1, (await getStores(baseUrl)).length);

      // Delete the store
      assertEquals(await deleteStore(baseUrl, storeName), true);

      // Check that the store is deleted
      assertEquals(stores, (await getStores(baseUrl)));

      // Attempt to delete should fail as this store doesn't exist
      assertEquals(await deleteStore(baseUrl, storeName), false);
    });
  });
}); 