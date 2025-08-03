import { useEffect, useState } from "preact/hooks";
import { HealthStatus, Topic } from "../utils/eventStore.ts";

interface EventStoreConfig {
  name: string;
  url: string;
  port: number;
}

interface StoreTopicsProps {
  store: EventStoreConfig;
}

interface StoreData {
  topics: Topic[];
  health: HealthStatus | null;
  isLoading: boolean;
  error: string | null;
  lastUpdated: Date | null;
}

export default function StoreTopics({ store }: StoreTopicsProps) {
  const [storeData, setStoreData] = useState<StoreData>({
    topics: [],
    health: null,
    isLoading: true,
    error: null,
    lastUpdated: null,
  });

  const loadStoreData = async () => {
    setStoreData((prev) => ({
      ...prev,
      isLoading: true,
      error: null,
    }));

    try {
      const res = await fetch(
        `/api/store-topics?store=${encodeURIComponent(store.name)}`,
      );
      if (!res.ok) throw new Error(`API error: ${res.status}`);
      const { topics, error } = await res.json();
      if (error) throw new Error(error);
      setStoreData({
        topics,
        health: null, // Optionally fetch health if needed
        isLoading: false,
        error: null,
        lastUpdated: new Date(),
      });
    } catch (error) {
      setStoreData({
        topics: [],
        health: null,
        isLoading: false,
        error: error instanceof Error
          ? error.message
          : "Failed to load store data",
        lastUpdated: new Date(),
      });
    }
  };

  useEffect(() => {
    loadStoreData();
  }, [store.name]);

  const handleRefresh = () => {
    loadStoreData();
  };

  const getHealthStatusColor = (health: HealthStatus | null) => {
    if (!health) return "gray";
    if (health.status === "healthy") return "green";
    if (health.status === "degraded") return "yellow";
    return "red";
  };

  const getHealthStatusText = (health: HealthStatus | null) => {
    if (!health) return "Unknown";
    return health.status.charAt(0).toUpperCase() + health.status.slice(1);
  };

  if (storeData.isLoading) {
    return (
      <div class="bg-white shadow rounded-lg">
        <div class="px-4 py-5 sm:p-6">
          <div class="flex items-center justify-between mb-4">
            <h3 class="text-lg leading-6 font-medium text-gray-900">
              {store.name}
            </h3>
            <div class="flex items-center space-x-2">
              <div class="animate-pulse bg-gray-200 h-4 w-20 rounded"></div>
              <button
                type="button"
                disabled
                class="inline-flex items-center px-3 py-1 border border-gray-300 shadow-sm text-sm font-medium rounded-md text-gray-400 bg-white cursor-not-allowed"
              >
                <svg
                  class="animate-spin -ml-1 mr-2 h-4 w-4"
                  fill="none"
                  viewBox="0 0 24 24"
                >
                  <circle
                    class="opacity-25"
                    cx="12"
                    cy="12"
                    r="10"
                    stroke="currentColor"
                    stroke-width="4"
                  >
                  </circle>
                  <path
                    class="opacity-75"
                    fill="currentColor"
                    d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
                  >
                  </path>
                </svg>
                Loading...
              </button>
            </div>
          </div>
          <div class="text-center py-12">
            <svg
              class="animate-spin mx-auto h-8 w-8 text-gray-400"
              fill="none"
              viewBox="0 0 24 24"
            >
              <circle
                class="opacity-25"
                cx="12"
                cy="12"
                r="10"
                stroke="currentColor"
                stroke-width="4"
              >
              </circle>
              <path
                class="opacity-75"
                fill="currentColor"
                d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
              >
              </path>
            </svg>
            <p class="mt-2 text-sm text-gray-500">Loading topics...</p>
          </div>
        </div>
      </div>
    );
  }

  if (storeData.error) {
    return (
      <div class="bg-white shadow rounded-lg">
        <div class="px-4 py-5 sm:p-6">
          <div class="flex items-center justify-between mb-4">
            <h3 class="text-lg leading-6 font-medium text-gray-900">
              {store.name}
            </h3>
            <div class="flex items-center space-x-2">
              <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-red-100 text-red-800">
                <svg
                  class="-ml-0.5 mr-1.5 h-2 w-2 text-red-400"
                  fill="currentColor"
                  viewBox="0 0 8 8"
                >
                  <circle cx="4" cy="4" r="3" />
                </svg>
                Unavailable
              </span>
              <button
                type="button"
                onClick={handleRefresh}
                class="inline-flex items-center px-3 py-1 border border-gray-300 shadow-sm text-sm font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
              >
                <svg
                  class="-ml-1 mr-2 h-4 w-4"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="2"
                    d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"
                  >
                  </path>
                </svg>
                Retry
              </button>
            </div>
          </div>
          <div class="text-center py-12">
            <svg
              class="mx-auto h-12 w-12 text-red-400"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
              >
              </path>
            </svg>
            <h3 class="mt-2 text-sm font-medium text-gray-900">
              Store Unavailable
            </h3>
            <p class="mt-1 text-sm text-gray-500">{storeData.error}</p>
            <div class="mt-6">
              <button
                type="button"
                onClick={handleRefresh}
                class="inline-flex items-center px-4 py-2 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-red-600 hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-red-500"
              >
                Try Again
              </button>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div class="bg-white shadow rounded-lg">
      <div class="px-4 py-5 sm:p-6">
        <div class="flex items-center justify-between mb-4">
          <div class="flex items-center space-x-3">
            <h3 class="text-lg leading-6 font-medium text-gray-900">
              {store.name}
            </h3>
            <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800">
              <svg
                class="-ml-0.5 mr-1.5 h-2 w-2 text-green-400"
                fill="currentColor"
                viewBox="0 0 8 8"
              >
                <circle cx="4" cy="4" r="3" />
              </svg>
              {getHealthStatusText(storeData.health)}
            </span>
          </div>
          <div class="flex items-center space-x-3">
            <span class="text-sm text-gray-500">
              {store.url}:{store.port}
            </span>
            {storeData.lastUpdated && (
              <span class="text-xs text-gray-400">
                Updated {storeData.lastUpdated.toLocaleTimeString()}
              </span>
            )}
            <button
              type="button"
              onClick={handleRefresh}
              class="inline-flex items-center px-3 py-1 border border-gray-300 shadow-sm text-sm font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
            >
              <svg
                class="-ml-1 mr-2 h-4 w-4"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  stroke-width="2"
                  d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"
                >
                </path>
              </svg>
              Refresh
            </button>
          </div>
        </div>

        {/* Health Status Details */}
        {storeData.health && (
          <div class="mb-4 p-3 bg-gray-50 rounded-md">
            <div class="flex items-center justify-between text-sm">
              <span class="text-gray-600">
                Consumers:{" "}
                <span class="font-medium">{storeData.health.consumers}</span>
              </span>
              <span class="text-gray-600">
                Dispatchers:{" "}
                <span class="font-medium">
                  {storeData.health.runningDispatchers.length}
                </span>
              </span>
            </div>
          </div>
        )}

        {storeData.topics.length === 0
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
                  d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10"
                >
                </path>
              </svg>
              <h3 class="mt-2 text-sm font-medium text-gray-900">No topics</h3>
              <p class="mt-1 text-sm text-gray-500">
                This store has no topics configured.
              </p>
            </div>
          )
          : (
            <div class="overflow-hidden">
              <table class="min-w-full divide-y divide-gray-200">
                <thead class="bg-gray-50">
                  <tr>
                    <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      Topic Name
                    </th>
                    <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      Sequence
                    </th>
                    <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      Schemas
                    </th>
                    <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      Actions
                    </th>
                  </tr>
                </thead>
                <tbody class="bg-white divide-y divide-gray-200">
                  {storeData.topics.map((topic) => (
                    <tr key={topic.name} class="hover:bg-gray-50">
                      <td class="px-6 py-4 whitespace-nowrap">
                        <div class="text-sm font-medium text-gray-900">
                          {topic.name}
                        </div>
                      </td>
                      <td class="px-6 py-4 whitespace-nowrap">
                        <div class="text-sm text-gray-900">
                          {topic.sequence}
                        </div>
                      </td>
                      <td class="px-6 py-4 whitespace-nowrap">
                        <div class="text-sm text-gray-900">
                          {topic.schemas.length}{" "}
                          schema{topic.schemas.length !== 1 ? "s" : ""}
                        </div>
                        <div class="text-xs text-gray-500">
                          {topic.schemas.map((s) =>
                            s.eventType
                          ).join(", ")}
                        </div>
                      </td>
                      <td class="px-6 py-4 whitespace-nowrap text-sm font-medium">
                        <a
                          href={`/topics/stats?topic=${
                            encodeURIComponent(topic.name)
                          }&store=${encodeURIComponent(store.name)}`}
                          class="text-blue-600 hover:text-blue-900 mr-4"
                        >
                          View Details
                        </a>
                        <a
                          href={`/events?store=${
                            encodeURIComponent(store.name)
                          }&topic=${encodeURIComponent(topic.name)}`}
                          class="text-green-600 hover:text-green-900"
                        >
                          View Events
                        </a>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
      </div>
    </div>
  );
}
