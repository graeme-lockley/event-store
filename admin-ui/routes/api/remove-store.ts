import { Handlers } from "$fresh/server.ts";
import { removeStore, getStoreByName } from "../../utils/storeConfig.ts";

export const handler: Handlers = {
  async POST(req) {
    try {
      const body = await req.json();
      const { name, url, port } = body;

      if (!name || !url || !port) {
        return new Response(
          JSON.stringify({ error: "Missing required fields: name, url, port" }),
          {
            status: 400,
            headers: { "Content-Type": "application/json" },
          }
        );
      }

      // Validate that the store exists
      const existingStore = await getStoreByName(name);
      if (!existingStore) {
        return new Response(
          JSON.stringify({ error: `Store "${name}" not found` }),
          {
            status: 404,
            headers: { "Content-Type": "application/json" },
          }
        );
      }

      // Validate that the store matches the provided URL and port
      if (existingStore.url !== url || existingStore.port !== port) {
        return new Response(
          JSON.stringify({ error: `Store configuration mismatch for "${name}"` }),
          {
            status: 400,
            headers: { "Content-Type": "application/json" },
          }
        );
      }

      console.log(`Removing store: ${name} (${url}:${port})`);

      // Remove the store from configuration
      const wasRemoved = await removeStore(name);
      
      if (!wasRemoved) {
        return new Response(
          JSON.stringify({ error: `Failed to remove store "${name}"` }),
          {
            status: 500,
            headers: { "Content-Type": "application/json" },
          }
        );
      }

      return new Response(
        JSON.stringify({
          success: true,
          message: `Store "${name}" removed successfully`,
          removedStore: { name, url, port },
        }),
        {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }
      );
    } catch (error) {
      console.error("Remove store error:", error);
      
      return new Response(
        JSON.stringify({ 
          error: error instanceof Error ? error.message : "Unknown error occurred" 
        }),
        {
          status: 500,
          headers: { "Content-Type": "application/json" },
        }
      );
    }
  },
}; 