import { PageProps } from "$fresh/server.ts";
import Layout from "../../../components/Layout.tsx";
import TopicStats from "../../../islands/TopicStats.tsx";
import { getStores } from "../../../utils/storeConfig.ts";

export default async function TopicStatsPage(req: PageProps) {
  const { topic } = req.params;
  const url = new URL(req.url);
  const store = url.searchParams.get("store");
  // Fallback to first store if not specified
  const stores = await getStores();
  const storeName = store || (stores.length > 0 ? stores[0].name : "");
  return (
    <Layout title={`Topic Statistics - ${topic}`}>
      <div class="max-w-3xl mx-auto py-8">
        <h1 class="text-2xl font-bold mb-4">Topic Statistics: <span class="text-blue-700">{topic}</span></h1>
        <TopicStats store={storeName} topic={topic} />
      </div>
    </Layout>
  );
} 