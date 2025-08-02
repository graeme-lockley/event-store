import { Handlers } from "$fresh/server.ts";
import { addStore, getStoreByName } from "../../utils/storeConfig.ts";

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

      // Validate port is a number
      const portNum = parseInt(port.toString());
      if (isNaN(portNum) || portNum < 1 || portNum > 65535) {
        return new Response(
          JSON.stringify({ error: "Port must be a valid number between 1 and 65535" }),
          {
            status: 400,
            headers: { "Content-Type": "application/json" },
          }
        );
      }

      // Validate URL format
      try {
        new URL(url);
      } catch {
        return new Response(
          JSON.stringify({ error: "Invalid URL format" }),
          {
            status: 400,
            headers: { "Content-Type": "application/json" },
          }
        );
      }

      // Check if store already exists
      const existingStore = await getStoreByName(name);
      if (existingStore) {
        return new Response(
          JSON.stringify({ error: `Store with name "${name}" already exists` }),
          {
            status: 409,
            headers: { "Content-Type": "application/json" },
          }
        );
      }

      console.log(`Adding store: ${name} (${url}:${portNum})`);

      // Add the store to configuration
      await addStore({ name, url, port: portNum });

      return new Response(
        JSON.stringify({
          success: true,
          message: `Store "${name}" added successfully`,
          store: { name, url, port: portNum },
        }),
        {
          status: 201,
          headers: { "Content-Type": "application/json" },
        }
      );
    } catch (error) {
      console.error("Add store error:", error);
      
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