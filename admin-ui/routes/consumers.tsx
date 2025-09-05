import { Handlers, PageProps } from "$fresh/server.ts";
import Layout from "../components/Layout.tsx";
import { Consumer, createEventStoreClient } from "../utils/eventStore.ts";
import { withAuth } from "../utils/middleware.ts";
import { EventStoreConfig, getStores } from "../utils/storeConfig.ts";

interface ConsumersData {
  stores: Array<{
    config: EventStoreConfig;
    consumers: Consumer[];
  }>;
}

export const handler: Handlers<ConsumersData> = withAuth({
  async GET(_req, ctx) {
    const stores = await getStores();

    const storeData = [];

    for (const storeConfig of stores) {
      try {
        const client = createEventStoreClient(storeConfig);
        const consumers = await client.getConsumers();

        storeData.push({
          config: storeConfig,
          consumers,
        });
      } catch (error) {
        console.error(`Failed to connect to ${storeConfig.name}:`, error);
        storeData.push({
          config: storeConfig,
          consumers: [],
        });
      }
    }

    return ctx.render({ stores: storeData });
  },
});

export default function Consumers({ data }: PageProps<ConsumersData>) {
  return (
    <Layout title="Consumers - Event Store Admin">
      <div class="space-y-6">
        <div>
          <h1 class="text-2xl font-bold text-gray-900">Consumers</h1>
          <p class="mt-1 text-sm text-gray-500">
            Manage and monitor consumers across your Event Store instances
          </p>
        </div>

        {data.stores.map((store) => (
          <div key={store.config.name} class="bg-white shadow rounded-lg">
            <div class="px-4 py-5 sm:p-6">
              <div class="flex items-center justify-between mb-4">
                <h3 class="text-lg leading-6 font-medium text-gray-900">
                  {store.config.name}
                </h3>
                <span class="text-sm text-gray-500">
                  {store.config.url}:{store.config.port}
                </span>
              </div>

              {store.consumers.length === 0
                ? (
                  <div class="text-center py-12">
                    <svg
                      class="mx-auto h-12 w-12 text-gray-400"
                      fill="none"
                      stroke="currentColor"
                      viewBox="0 0 24 24"
                    >
                      <path
                        stroke-linecap="round"
                        stroke-linejoin="round"
                        stroke-width="2"
                        d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z"
                      >
                      </path>
                    </svg>
                    <h3 class="mt-2 text-sm font-medium text-gray-900">
                      No consumers
                    </h3>
                    <p class="mt-1 text-sm text-gray-500">
                      No consumers registered for this store.
                    </p>
                  </div>
                )
                : (
                  <div class="overflow-hidden">
                    <table class="min-w-full divide-y divide-gray-200">
                      <thead class="bg-gray-50">
                        <tr>
                          <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                            Consumer ID
                          </th>
                          <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                            Callback URL
                          </th>
                          <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                            Topics
                          </th>
                          <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                            Actions
                          </th>
                        </tr>
                      </thead>
                      <tbody class="bg-white divide-y divide-gray-200">
                        {store.consumers.map((consumer) => (
                          <tr key={consumer.id} class="hover:bg-gray-50">
                            <td class="px-6 py-4 whitespace-nowrap">
                              <div class="text-sm font-medium text-gray-900">
                                {consumer.id}
                              </div>
                            </td>
                            <td class="px-6 py-4 whitespace-nowrap">
                              <div class="text-sm text-gray-900">
                                {consumer.callback}
                              </div>
                            </td>
                            <td class="px-6 py-4 whitespace-nowrap">
                              <div class="text-sm text-gray-900">
                                {Object.keys(consumer.topics).length}{" "}
                                topic{Object.keys(consumer.topics).length !== 1
                                  ? "s"
                                  : ""}
                              </div>
                              <div class="text-xs text-gray-500">
                                {Object.entries(consumer.topics).map((
                                  [topic, lastEventId],
                                ) => (
                                  <div key={topic}>
                                    {topic}: {lastEventId || "from beginning"}
                                  </div>
                                ))}
                              </div>
                            </td>
                            <td class="px-6 py-4 whitespace-nowrap text-sm font-medium">
                              <button
                                type="button"
                                class="text-red-600 hover:text-red-900"
                              >
                                Unregister
                              </button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
            </div>
          </div>
        ))}
      </div>
    </Layout>
  );
}
