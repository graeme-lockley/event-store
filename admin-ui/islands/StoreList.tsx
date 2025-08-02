import { useEffect, useState } from "preact/hooks";
import TestConnection from "./TestConnection.tsx";
import RemoveStore from "./RemoveStore.tsx";
import AddStore from "./AddStore.tsx";

interface Store {
  name: string;
  url: string;
  port: number;
}

interface StoreWithHealth extends Store {
  health?: {
    status: "loading" | "healthy" | "unhealthy" | "error";
    message?: string;
    consumers?: number;
    runningDispatchers?: string[];
  };
}

export default function StoreList() {
  const [stores, setStores] = useState<StoreWithHealth[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);

  const fetchStores = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch("/api/stores");
      if (!res.ok) throw new Error("Failed to fetch stores");
      const data = await res.json();
      
      console.log("StoreList: Received data from /api/stores:", data);
      
      // Handle both array response and object with stores property
      const storesArray = Array.isArray(data) ? data : (data.stores || []);
      
      console.log("StoreList: Processed stores array:", storesArray);
      
      // Initialize stores with loading health status
      const storesWithHealth: StoreWithHealth[] = storesArray.map((store: Store) => ({
        ...store,
        health: { status: "loading" }
      }));
      
      setStores(storesWithHealth);
      
      // Check health for each store asynchronously
      storesWithHealth.forEach((store, index) => {
        checkStoreHealth(store, index);
      });
    } catch (err) {
      console.error("StoreList: Error fetching stores:", err);
      setError(err instanceof Error ? err.message : "Unknown error");
    } finally {
      setLoading(false);
    }
  };

  const checkStoreHealth = async (store: Store, index: number) => {
    try {
      const response = await fetch(`/api/store-health?store=${encodeURIComponent(store.name)}`, {
        // Use a shorter timeout for health checks
        signal: AbortSignal.timeout(5000)
      });

      if (response.ok) {
        const healthData = await response.json();
        console.log(`StoreList: Health data for ${store.name}:`, healthData);
        
        // Handle the new response structure
        const health = healthData.health || healthData;
        const consumers = health.consumers || 0;
        const runningDispatchers = health.runningDispatchers || [];
        
        setStores(prev => prev.map((s, i) => 
          i === index 
            ? { ...s, health: { 
                status: "healthy", 
                message: `Healthy - ${consumers} consumers, ${runningDispatchers.length} dispatchers`,
                consumers: consumers,
                runningDispatchers: runningDispatchers
              }}
            : s
        ));
      } else {
        const errorData = await response.json().catch(() => ({}));
        console.log(`StoreList: Health check failed for ${store.name}:`, errorData);
        
        setStores(prev => prev.map((s, i) => 
          i === index 
            ? { ...s, health: { status: "unhealthy", message: errorData.error || "Store unavailable" }}
            : s
        ));
      }
    } catch (error) {
      console.error(`StoreList: Health check error for ${store.name}:`, error);
      setStores(prev => prev.map((s, i) => 
        i === index 
          ? { ...s, health: { status: "error", message: "Health check failed" }}
          : s
      ));
    }
  };

  const refreshHealth = async () => {
    setRefreshing(true);
    // Reset all stores to loading state
    setStores(prev => prev.map(store => ({
      ...store,
      health: { status: "loading" }
    })));
    
    // Check health for each store
    stores.forEach((store, index) => {
      checkStoreHealth(store, index);
    });
    
    // Reset refreshing state after a short delay
    setTimeout(() => setRefreshing(false), 1000);
  };

  useEffect(() => {
    fetchStores();
  }, []);

  const handleStoreChanged = () => {
    fetchStores();
  };

  const getHealthStatusIcon = (health: StoreWithHealth["health"]) => {
    if (!health) return null;
    
    switch (health.status) {
      case "loading":
        return (
          <svg class="animate-spin h-4 w-4 text-gray-400" fill="none" viewBox="0 0 24 24">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
          </svg>
        );
      case "healthy":
        return <div class="h-3 w-3 bg-green-500 rounded-full"></div>;
      case "unhealthy":
        return <div class="h-3 w-3 bg-red-500 rounded-full"></div>;
      case "error":
        return <div class="h-3 w-3 bg-yellow-500 rounded-full"></div>;
      default:
        return null;
    }
  };

  const getHealthStatusText = (health: StoreWithHealth["health"]) => {
    if (!health) return "Unknown";
    
    switch (health.status) {
      case "loading":
        return "Checking health...";
      case "healthy":
        return "Healthy";
      case "unhealthy":
        return "Unavailable";
      case "error":
        return "Error";
      default:
        return "Unknown";
    }
  };

  if (loading) {
    return <div class="text-gray-500">Loading stores...</div>;
  }
  if (error) {
    return <div class="text-red-600">Error: {error}</div>;
  }

  return (
    <div class="space-y-4">
      {/* Header with refresh button */}
      <div class="flex justify-between items-center">
        <h4 class="text-sm font-medium text-gray-700">Configured Stores</h4>
        <button
          onClick={refreshHealth}
          disabled={refreshing}
          class="inline-flex items-center px-3 py-2 border border-gray-300 shadow-sm text-sm leading-4 font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {refreshing ? (
            <>
              <svg class="animate-spin -ml-1 mr-2 h-4 w-4 text-gray-500" fill="none" viewBox="0 0 24 24">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
              </svg>
              Refreshing...
            </>
          ) : (
            <>
              <svg class="-ml-1 mr-2 h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"></path>
              </svg>
              Refresh Health
            </>
          )}
        </button>
      </div>

      {stores.map((store, index) => (
        <div key={store.name + store.port} class="border border-gray-200 rounded-lg p-4">
          <div class="grid grid-cols-1 gap-4 sm:grid-cols-4">
            <div>
              <label class="block text-sm font-medium text-gray-700">Store Name</label>
              <input
                type="text"
                value={store.name}
                class="mt-1 block w-full border-gray-300 rounded-md shadow-sm focus:ring-blue-500 focus:border-blue-500 sm:text-sm"
                placeholder="Store Name"
                readOnly
              />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700">URL</label>
              <input
                type="text"
                value={store.url}
                class="mt-1 block w-full border-gray-300 rounded-md shadow-sm focus:ring-blue-500 focus:border-blue-500 sm:text-sm"
                placeholder="http://localhost"
                readOnly
              />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700">Port</label>
              <input
                type="number"
                value={store.port}
                class="mt-1 block w-full border-gray-300 rounded-md shadow-sm focus:ring-blue-500 focus:border-blue-500 sm:text-sm"
                placeholder="8000"
                readOnly
              />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700">Health Status</label>
              <div class="mt-1 flex items-center space-x-2">
                {getHealthStatusIcon(store.health)}
                <span class={`text-sm ${
                  store.health?.status === "healthy" ? "text-green-600" :
                  store.health?.status === "unhealthy" ? "text-red-600" :
                  store.health?.status === "error" ? "text-yellow-600" :
                  "text-gray-500"
                }`}>
                  {getHealthStatusText(store.health)}
                </span>
              </div>
              {store.health?.message && (
                <p class="mt-1 text-xs text-gray-500">{store.health.message}</p>
              )}
            </div>
          </div>
          <div class="mt-4 flex justify-end space-x-2">
            <TestConnection storeName={store.name} url={store.url} port={store.port} />
            <RemoveStore storeName={store.name} url={store.url} port={store.port} onRemove={handleStoreChanged} />
          </div>
        </div>
      ))}
      <AddStore onAdd={handleStoreChanged} />
    </div>
  );
} 