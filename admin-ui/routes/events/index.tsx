import { PageProps } from "$fresh/server.ts";
import Layout from "../../components/Layout.tsx";
import EventBrowser from "../../islands/EventBrowser.tsx";
import { getStores } from "../../utils/storeConfig.ts";

interface EventsData {
  stores: Array<{ name: string; url: string; port: number }>;
}

export const handler = {
  async GET(req: Request, ctx: any) {
    const stores = await getStores();
    return ctx.render({ stores });
  },
};

export default function Events({ data }: PageProps<EventsData>) {
  return (
    <Layout title="Events - Event Store Admin">
      <div class="space-y-6">
        <div>
          <h1 class="text-2xl font-bold text-gray-900">Events</h1>
          <p class="mt-1 text-sm text-gray-500">
            Browse and search events across your Event Store instances
          </p>
        </div>

        <EventBrowser stores={data.stores} />
      </div>
    </Layout>
  );
}
