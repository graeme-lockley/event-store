import { Handlers, PageProps } from "$fresh/server.ts";
import Layout from "../components/Layout.tsx";
import { Event, createEventStoreClient } from "../utils/eventStore.ts";
import { withAuth } from "../utils/middleware.ts";
import { EventStoreConfig, getStores } from "../utils/storeConfig.ts";

interface EventsData {
  stores: Array<{
    config: EventStoreConfig;
    topics: string[];
    events: Event[];
  }>;
  selectedStore?: string;
  selectedTopic?: string;
}

export const handler: Handlers<EventsData> = withAuth({
  async GET(req, ctx) {
    const url = new URL(req.url);
    const selectedStore = url.searchParams.get("store") || "";
    const selectedTopic = url.searchParams.get("topic") || "";
    const limit = parseInt(url.searchParams.get("limit") || "50");

    const stores = await getStores();

    const storeData = [];

    for (const storeConfig of stores) {
      try {
        const client = createEventStoreClient(storeConfig);
        const topics = await client.getTopics();
        let events: Event[] = [];

        // If a specific store and topic are selected, fetch events
        if (selectedStore === storeConfig.name && selectedTopic) {
          try {
            events = await client.getEvents(selectedTopic, { limit });
          } catch (error) {
            console.error(
              `Failed to get events for topic ${selectedTopic}:`,
              error,
            );
          }
        }

        storeData.push({
          config: storeConfig,
          topics,
          events,
        });
      } catch (error) {
        console.error(`Failed to connect to ${storeConfig.name}:`, error);
        storeData.push({
          config: storeConfig,
          topics: [],
          events: [],
        });
      }
    }

    return ctx.render({
      stores: storeData,
      selectedStore,
      selectedTopic,
    });
  },
});

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

        {/* Filters */}
        <div class="bg-white shadow rounded-lg p-6">
          <div class="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <div>
              <label
                for="store"
                class="block text-sm font-medium text-gray-700"
              >
                Store
              </label>
              <select
                id="store"
                name="store"
                class="mt-1 block w-full pl-3 pr-10 py-2 text-base border-gray-300 focus:outline-none focus:ring-blue-500 focus:border-blue-500 sm:text-sm rounded-md"
                onChange={(e) =>
                  window.location.href = "?store=" + e.currentTarget.value}
              >
                <option value="">All Stores</option>
                {data.stores.map((store) => (
                  <option
                    value={store.config.name}
                    selected={data.selectedStore === store.config.name}
                  >
                    {store.config.name}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label
                for="topic"
                class="block text-sm font-medium text-gray-700"
              >
                Topic
              </label>
              <select
                id="topic"
                name="topic"
                class="mt-1 block w-full pl-3 pr-10 py-2 text-base border-gray-300 focus:outline-none focus:ring-blue-500 focus:border-blue-500 sm:text-sm rounded-md"
                onChange={(e) =>
                  window.location.href =
                    `?store=${data.selectedStore}&topic=${e.currentTarget.value}`}
              >
                <option value="">All Topics</option>
                {data.selectedStore &&
                  data.stores.find((s) => s.config.name === data.selectedStore)
                    ?.topics.map((topic) => (
                      <option
                        value={topic}
                        selected={data.selectedTopic === topic}
                      >
                        {topic}
                      </option>
                    ))}
              </select>
            </div>

            <div>
              <label
                for="limit"
                class="block text-sm font-medium text-gray-700"
              >
                Limit
              </label>
              <select
                id="limit"
                name="limit"
                class="mt-1 block w-full pl-3 pr-10 py-2 text-base border-gray-300 focus:outline-none focus:ring-blue-500 focus:border-blue-500 sm:text-sm rounded-md"
                onChange={(e) =>
                  window.location.href =
                    `?store=${data.selectedStore}&topic=${data.selectedTopic}&limit=${e.currentTarget.value}`}
              >
                <option value="10">10 events</option>
                <option value="25" selected>25 events</option>
                <option value="50">50 events</option>
                <option value="100">100 events</option>
              </select>
            </div>
          </div>
        </div>

        {/* Events List */}
        {data.selectedStore && data.selectedTopic
          ? (
            <div class="bg-white shadow rounded-lg">
              <div class="px-4 py-5 sm:p-6">
                <div class="flex items-center justify-between mb-4">
                  <h3 class="text-lg leading-6 font-medium text-gray-900">
                    Events in {data.selectedTopic}
                  </h3>
                  <span class="text-sm text-gray-500">
                    {data.stores.find((s) =>
                      s.config.name === data.selectedStore
                    )?.events.length || 0} events
                  </span>
                </div>

                {data.stores.find((s) => s.config.name === data.selectedStore)
                    ?.events.length === 0
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
                          d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
                        >
                        </path>
                      </svg>
                      <h3 class="mt-2 text-sm font-medium text-gray-900">
                        No events found
                      </h3>
                      <p class="mt-1 text-sm text-gray-500">
                        No events found in this topic. Try selecting a different
                        topic or store.
                      </p>
                    </div>
                  )
                  : (
                    <div class="space-y-4">
                      {data.stores.find((s) =>
                        s.config.name === data.selectedStore
                      )?.events.map((event) => (
                        <div
                          key={event.id}
                          class="border border-gray-200 rounded-lg p-4 hover:bg-gray-50"
                        >
                          <div class="flex items-start justify-between">
                            <div class="flex-1">
                              <div class="flex items-center space-x-2">
                                <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800">
                                  {event.type}
                                </span>
                                <span class="text-sm text-gray-500">
                                  {event.id}
                                </span>
                              </div>
                              <div class="mt-2">
                                <pre class="text-sm text-gray-900 bg-gray-100 p-3 rounded overflow-x-auto">
                              {JSON.stringify(event.payload, null, 2)}
                                </pre>
                              </div>
                            </div>
                            <div class="ml-4 text-right">
                              <div class="text-sm text-gray-500">
                                {new Date(event.timestamp).toLocaleString()}
                              </div>
                            </div>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
              </div>
            </div>
          )
          : (
            <div class="bg-white shadow rounded-lg">
              <div class="px-4 py-5 sm:p-6">
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
                      d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
                    >
                    </path>
                  </svg>
                  <h3 class="mt-2 text-sm font-medium text-gray-900">
                    Select a store and topic
                  </h3>
                  <p class="mt-1 text-sm text-gray-500">
                    Choose a store and topic from the filters above to view
                    events.
                  </p>
                </div>
              </div>
            </div>
          )}
      </div>
    </Layout>
  );
}
