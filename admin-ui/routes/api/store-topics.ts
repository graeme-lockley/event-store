import { Handlers } from "$fresh/server.ts";
import { getStores } from "../../utils/storeConfig.ts";
import {
  createEventStoreClient,
  type LegacyEventStoreConfig,
} from "../../utils/eventStore.ts";

export const handler: Handlers = {
  async GET(req) {
    const url = new URL(req.url);
    const storeName = url.searchParams.get("store");
    if (!storeName) {
      return new Response("Missing store param", { status: 400 });
    }
    const stores = await getStores();
    const store = stores.find((s) => s.name === storeName);
    if (!store) {
      return new Response("Store not found", { status: 404 });
    }
    try {
      const client = createEventStoreClient(store as LegacyEventStoreConfig);
      const topics = await client.getTopics();
      return Response.json({ topics });
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      return Response.json({ error: message }, { status: 500 });
    }
  },
};
