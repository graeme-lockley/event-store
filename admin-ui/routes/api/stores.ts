import { Handlers } from "$fresh/server.ts";
import { addStore, getStores } from "../../utils/storeConfig.ts";

export const handler: Handlers = {
  async GET() {
    try {
      const stores = await getStores();
      return new Response(JSON.stringify(stores), {
        headers: { "Content-Type": "application/json" },
      });
    } catch (error) {
      return new Response(JSON.stringify({ error: String(error) }), {
        status: 500,
        headers: { "Content-Type": "application/json" },
      });
    }
  },

  async POST(req) {
    try {
      const body = await req.json();

      if (!body.name || !body.url) {
        return new Response(
          JSON.stringify({
            success: false,
            error: "Name and URL are required",
          }),
          {
            status: 400,
            headers: { "Content-Type": "application/json" },
          },
        );
      }

      const store = {
        name: body.name,
        url: body.url,
        port: body.port || 8000,
      };

      try {
        await addStore(store);
        return new Response(JSON.stringify({ success: true }), {
          status: 201,
          headers: { "Content-Type": "application/json" },
        });
      } catch (error) {
        const errorMessage = error instanceof Error
          ? error.message
          : String(error);
        return new Response(
          JSON.stringify({
            success: false,
            error: errorMessage,
          }),
          {
            status: 400,
            headers: { "Content-Type": "application/json" },
          },
        );
      }
    } catch (error) {
      const errorMessage = error instanceof Error
        ? error.message
        : String(error);
      return new Response(
        JSON.stringify({
          success: false,
          error: errorMessage,
        }),
        {
          status: 400,
          headers: { "Content-Type": "application/json" },
        },
      );
    }
  },
};
