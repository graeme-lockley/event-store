import { PageProps } from "$fresh/server.ts";
import Layout from "../../components/Layout.tsx";
import TopicStats from "../../islands/TopicStats.tsx";
import TopicConfig from "../../islands/TopicConfig.tsx";
import { getStores } from "../../utils/storeConfig.ts";

export default async function TopicStatsPage(req: PageProps) {
  const url = new URL(req.url);
  const topic = url.searchParams.get("topic");
  const store = url.searchParams.get("store");

  if (!topic) {
    return new Response("Missing topic parameter", { status: 400 });
  }

  // Fallback to first store if not specified
  const stores = await getStores();
  const storeName = store || (stores.length > 0 ? stores[0].name : "");

  return (
    <Layout title={`Topic Statistics - ${topic}`}>
      <div class="max-w-6xl mx-auto py-8">
        <div class="mb-6">
          <h1 class="text-2xl font-bold mb-2">
            Topic Statistics: <span class="text-blue-700">{topic}</span>
          </h1>
          <p class="text-sm text-gray-500">Store: {storeName}</p>
        </div>

        <div class="grid grid-cols-1 lg:grid-cols-2 gap-8">
          {/* Statistics */}
          <div>
            <TopicStats store={storeName} topic={topic} />
          </div>

          {/* Configuration */}
          <div>
            <TopicConfig store={storeName} topic={topic} />
          </div>
        </div>
      </div>
    </Layout>
  );
}
