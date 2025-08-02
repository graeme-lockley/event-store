import { Handlers } from "$fresh/server.ts";
import { createEventStoreClient, createTestEventStoreClient, type LegacyEventStoreConfig } from "../../utils/eventStore.ts";

export const handler: Handlers = {
  async POST(req) {
    try {
      // Check Content-Type header
      const contentType = req.headers.get("content-type");
      if (!contentType || !contentType.includes("application/json")) {
        return new Response(JSON.stringify({ 
          success: false, 
          error: "Content-Type must be application/json" 
        }), {
          status: 400,
          headers: { "Content-Type": "application/json" },
        });
      }

      const body = await req.json();
      
      // Validate required fields
      if (!body.name || !body.url) {
        return new Response(JSON.stringify({ 
          success: false, 
          error: "Name and URL are required" 
        }), {
          status: 400,
          headers: { "Content-Type": "application/json" },
        });
      }

      // Create EventStoreClient configuration
      const storeConfig = {
        name: body.name,
        url: body.url,
        port: body.port || 8000
      };

      try {
        console.log(`TestConnection API: Testing connection to ${storeConfig.name} at ${storeConfig.url}:${storeConfig.port}`);
        
        // Use test-optimized client for faster response times
        const client = createTestEventStoreClient(storeConfig as LegacyEventStoreConfig);
        
        // Test connection by getting health status only (faster than multiple calls)
        console.log(`TestConnection API: Calling client.getHealth()`);
        const health = await client.getHealth();
        console.log(`TestConnection API: Health response:`, health);
        
        return new Response(JSON.stringify({
          success: true,
          message: `✅ Connection successful! Store is healthy with ${health.consumers} consumers and ${health.runningDispatchers.length} running dispatchers.`,
          consumers: health.consumers,
          runningDispatchers: health.runningDispatchers,
        }), {
          headers: { "Content-Type": "application/json" },
        });
      } catch (error) {
        console.error(`TestConnection API: Error:`, error);
        const errorMessage = error instanceof Error ? error.message : String(error);
        return new Response(JSON.stringify({
          success: false,
          message: `❌ Connection failed: ${errorMessage}`
        }), {
          headers: { "Content-Type": "application/json" },
        });
      }
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      return new Response(JSON.stringify({ 
        success: false, 
        error: errorMessage 
      }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      });
    }
  },
}; 