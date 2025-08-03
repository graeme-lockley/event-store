import { useEffect, useState } from "preact/hooks";
import { EventStoreClient, HealthStatus } from "../utils/eventStore.ts";

interface EventStoreConfig {
  name: string;
  url: string;
  port: number;
}

interface DashboardStoresProps {
  stores: EventStoreConfig[];
}

interface StoreData {
  health: HealthStatus | null;
  topics: string[];
  consumers: any[];
  isLoading: boolean;
  error: string | null;
}

export default function DashboardStores({ stores }: DashboardStoresProps) {
  const [storesData, setStoresData] = useState<Record<string, StoreData>>({});

  const loadStoreData = async (store: EventStoreConfig) => {
    setStoresData((prev) => ({
      ...prev,
      [store.name]: { ...prev[store.name], isLoading: true, error: null },
    }));

    try {
      const res = await fetch(
        `/api/store-health?store=${encodeURIComponent(store.name)}`,
      );
      if (!res.ok) throw new Error(`API error: ${res.status}`);
      const { health, topics, consumers, error } = await res.json();
      if (error) throw new Error(error);
      setStoresData((prev) => ({
        ...prev,
        [store.name]: {
          health,
          topics,
          consumers,
          isLoading: false,
          error: null,
        },
      }));
    } catch (error) {
      setStoresData((prev) => ({
        ...prev,
        [store.name]: {
          health: { status: "unhealthy", consumers: 0, runningDispatchers: [] },
          topics: [],
          consumers: [],
          isLoading: false,
          error: error instanceof Error
            ? error.message
            : "Failed to load store data",
        },
      }));
    }
  };

  useEffect(() => {
    // Load data for all stores in parallel
    stores.forEach((store) => {
      loadStoreData(store);
    });
  }, [stores.length]); // Only depend on the number of stores

  const handleRefresh = (store: EventStoreConfig) => {
    loadStoreData(store);
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

  return (
    <div class="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
      {stores.map((store) => {
        const storeData = storesData[store.name] || {
          health: null,
          topics: [],
          consumers: [],
          isLoading: true,
          error: null,
        };

        return (
          <div
            key={store.name}
            class="bg-white overflow-hidden shadow rounded-lg"
          >
            <div class="p-5">
              <div class="flex items-center justify-between">
                <div class="flex items-center">
                  <div class="flex-shrink-0">
                    {storeData.isLoading
                      ? (
                        <div class="w-8 h-8 rounded-full bg-gray-100 flex items-center justify-center">
                          <svg
                            class="animate-spin w-5 h-5 text-gray-400"
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
                        </div>
                      )
                      : (
                        <div
                          class={`w-8 h-8 rounded-full flex items-center justify-center ${
                            storeData.health?.status === "healthy"
                              ? "bg-green-100 text-green-600"
                              : "bg-red-100 text-red-600"
                          }`}
                        >
                          <svg
                            class="w-5 h-5"
                            fill="currentColor"
                            viewBox="0 0 20 20"
                          >
                            <path
                              fill-rule="evenodd"
                              d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z"
                              clip-rule="evenodd"
                            >
                            </path>
                          </svg>
                        </div>
                      )}
                  </div>
                  <div class="ml-5 w-0 flex-1">
                    <dl>
                      <dt class="text-sm font-medium text-gray-500 truncate">
                        {store.name}
                      </dt>
                      <dd class="text-lg font-medium text-gray-900">
                        {store.url}:{store.port}
                      </dd>
                    </dl>
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => handleRefresh(store)}
                  disabled={storeData.isLoading}
                  class="inline-flex items-center px-2 py-1 border border-gray-300 shadow-sm text-xs font-medium rounded text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  <svg
                    class="h-3 w-3"
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
                </button>
              </div>
            </div>
            <div class="bg-gray-50 px-5 py-3">
              <div class="text-sm">
                <div class="flex justify-between">
                  <span class="text-gray-500">Status:</span>
                  <span
                    class={`font-medium ${
                      storeData.isLoading
                        ? "text-gray-400"
                        : storeData.health?.status === "healthy"
                        ? "text-green-600"
                        : "text-red-600"
                    }`}
                  >
                    {storeData.isLoading
                      ? "Loading..."
                      : storeData.error
                      ? "Unavailable"
                      : getHealthStatusText(storeData.health)}
                  </span>
                </div>
                <div class="flex justify-between mt-1">
                  <span class="text-gray-500">Topics:</span>
                  <span class="font-medium text-gray-900">
                    {storeData.isLoading ? "..." : storeData.topics.length}
                  </span>
                </div>
                <div class="flex justify-between mt-1">
                  <span class="text-gray-500">Consumers:</span>
                  <span class="font-medium text-gray-900">
                    {storeData.isLoading ? "..." : storeData.consumers.length}
                  </span>
                </div>
                {storeData.error && (
                  <div class="mt-2 text-xs text-red-600">
                    {storeData.error}
                  </div>
                )}
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}
