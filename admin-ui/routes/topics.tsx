import { PageProps } from "$fresh/server.ts";
import Layout from "../components/Layout.tsx";
import StoreTopics from "../islands/StoreTopics.tsx";
import { getStores, EventStoreConfig } from "../utils/storeConfig.ts";

interface TopicsData {
  stores: EventStoreConfig[];
}

export const handler = {
  async GET(req: Request, ctx: any) {
    const stores = await getStores();
    return ctx.render({ stores });
  },
};

export default function Topics({ data }: PageProps<TopicsData>) {
  return (
    <Layout title="Topics - Event Store Admin">
      <div class="space-y-6">
        <div>
          <h1 class="text-2xl font-bold text-gray-900">Topics</h1>
          <p class="mt-1 text-sm text-gray-500">
            Manage topics across your Event Store instances
          </p>
        </div>

        {data.stores.map((store) => (
          <StoreTopics key={store.name} store={store} />
        ))}
      </div>
    </Layout>
  );
} 