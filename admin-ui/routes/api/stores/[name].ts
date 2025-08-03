import { Handlers } from "$fresh/server.ts";
import { removeStore } from "../../../utils/storeConfig.ts";

export const handler: Handlers = {
  async DELETE(_req, ctx) {
    try {
      const storeName = decodeURIComponent(ctx.params.name);

      if (!storeName) {
        return new Response(
          JSON.stringify({
            success: false,
            error: "Store name is required",
          }),
          {
            status: 400,
            headers: { "Content-Type": "application/json" },
          },
        );
      }

      const success = await removeStore(storeName);

      if (success) {
        return new Response(JSON.stringify({ success: true }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        });
      } else {
        return new Response(
          JSON.stringify({
            success: false,
            error: `Store not found: ${storeName}`,
          }),
          {
            status: 404,
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
          status: 500,
          headers: { "Content-Type": "application/json" },
        },
      );
    }
  },
};
