import { useState, useEffect } from "preact/hooks";
import { Event } from "../utils/eventStore.ts";

interface EventBrowserProps {
  stores: Array<{ name: string; url: string; port: number }>;
}

interface EventFilters {
  store: string;
  topic: string;
  eventType: string;
  dateFrom: string;
  dateTo: string;
  search: string;
}

interface EventData {
  events: Event[];
  totalEvents: number;
  isLoading: boolean;
  error: string | null;
}

interface PaginationState {
  page: number;
  pageSize: number;
  total: number;
}

export default function EventBrowser({ stores }: EventBrowserProps) {
  const [filters, setFilters] = useState<EventFilters>({
    store: stores[0]?.name || "",
    topic: "",
    eventType: "",
    dateFrom: "",
    dateTo: "",
    search: "",
  });

  const [eventData, setEventData] = useState<EventData>({
    events: [],
    totalEvents: 0,
    isLoading: false,
    error: null,
  });

  const [pagination, setPagination] = useState<PaginationState>({
    page: 1,
    pageSize: 20,
    total: 0,
  });

  const [topics, setTopics] = useState<string[]>([]);
  const [eventTypes, setEventTypes] = useState<string[]>([]);
  const [selectedEvent, setSelectedEvent] = useState<Event | null>(null);

  // Load topics when store changes
  useEffect(() => {
    if (filters.store) {
      loadTopics(filters.store);
    }
  }, [filters.store]);

  // Load events when filters or pagination changes
  useEffect(() => {
    if (filters.store) {
      loadEvents();
    }
  }, [filters, pagination.page, pagination.pageSize]);

  const loadTopics = async (storeName: string) => {
    try {
      const res = await fetch(`/api/store-topics?store=${encodeURIComponent(storeName)}`);
      if (!res.ok) throw new Error(`API error: ${res.status}`);
      const { topics, error } = await res.json();
      if (error) throw new Error(error);
      setTopics(topics.map((t: any) => t.name));
    } catch (error) {
      console.error("Failed to load topics:", error);
      setTopics([]);
    }
  };

  const loadEvents = async () => {
    setEventData(prev => ({ ...prev, isLoading: true, error: null }));

    try {
      const params = new URLSearchParams({
        store: filters.store,
        page: pagination.page.toString(),
        pageSize: pagination.pageSize.toString(),
      });

      if (filters.topic) params.append("topic", filters.topic);
      if (filters.eventType) params.append("eventType", filters.eventType);
      if (filters.dateFrom) params.append("dateFrom", filters.dateFrom);
      if (filters.dateTo) params.append("dateTo", filters.dateTo);
      if (filters.search) params.append("search", filters.search);

      const res = await fetch(`/api/events?${params.toString()}`);
      if (!res.ok) throw new Error(`API error: ${res.status}`);
      const { events, total, eventTypes: types } = await res.json();
      
      setEventData({
        events,
        totalEvents: total,
        isLoading: false,
        error: null,
      });
      
      setPagination(prev => ({ ...prev, total }));
      setEventTypes(types || []);
    } catch (error) {
      setEventData({
        events: [],
        totalEvents: 0,
        isLoading: false,
        error: error instanceof Error ? error.message : "Failed to load events",
      });
    }
  };

  const handleFilterChange = (key: keyof EventFilters, value: string) => {
    setFilters(prev => ({ ...prev, [key]: value }));
    setPagination(prev => ({ ...prev, page: 1 })); // Reset to first page
  };

  const handlePageChange = (page: number) => {
    setPagination(prev => ({ ...prev, page }));
  };

  const handlePageSizeChange = (pageSize: number) => {
    setPagination(prev => ({ ...prev, page: 1, pageSize }));
  };

  const exportEvents = async (format: "json" | "csv") => {
    try {
      const params = new URLSearchParams({
        store: filters.store,
        format,
        ...(filters.topic && { topic: filters.topic }),
        ...(filters.eventType && { eventType: filters.eventType }),
        ...(filters.dateFrom && { dateFrom: filters.dateFrom }),
        ...(filters.dateTo && { dateTo: filters.dateTo }),
        ...(filters.search && { search: filters.search }),
      });

      const res = await fetch(`/api/events/export?${params.toString()}`);
      if (!res.ok) throw new Error(`Export failed: ${res.status}`);

      const blob = await res.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `events-${new Date().toISOString().split("T")[0]}.${format}`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
    } catch (error) {
      console.error("Export failed:", error);
      alert("Export failed. Please try again.");
    }
  };

  const totalPages = Math.ceil(pagination.total / pagination.pageSize);

  return (
    <div class="space-y-6">
      {/* Filters */}
      <div class="bg-white shadow rounded-lg p-6">
        <h3 class="text-lg font-medium text-gray-900 mb-4">Filters</h3>
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {/* Store */}
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Store</label>
            <select
              value={filters.store}
              onChange={(e) => handleFilterChange("store", e.currentTarget.value)}
              class="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              {stores.map(store => (
                <option key={store.name} value={store.name}>{store.name}</option>
              ))}
            </select>
          </div>

          {/* Topic */}
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Topic</label>
            <select
              value={filters.topic}
              onChange={(e) => handleFilterChange("topic", e.currentTarget.value)}
              class="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="">All Topics</option>
              {topics.map(topic => (
                <option key={topic} value={topic}>{topic}</option>
              ))}
            </select>
          </div>

          {/* Event Type */}
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Event Type</label>
            <select
              value={filters.eventType}
              onChange={(e) => handleFilterChange("eventType", e.currentTarget.value)}
              class="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="">All Types</option>
              {eventTypes.map(type => (
                <option key={type} value={type}>{type}</option>
              ))}
            </select>
          </div>

          {/* Date From */}
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Date From</label>
            <input
              type="date"
              value={filters.dateFrom}
              onChange={(e) => handleFilterChange("dateFrom", e.currentTarget.value)}
              class="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          {/* Date To */}
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Date To</label>
            <input
              type="date"
              value={filters.dateTo}
              onChange={(e) => handleFilterChange("dateTo", e.currentTarget.value)}
              class="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          {/* Search */}
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Search Payload</label>
            <input
              type="text"
              value={filters.search}
              onChange={(e) => handleFilterChange("search", e.currentTarget.value)}
              placeholder="Search in event payload..."
              class="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
        </div>

        {/* Export Buttons */}
        <div class="mt-4 flex space-x-2">
          <button
            onClick={() => exportEvents("json")}
            class="inline-flex items-center px-3 py-2 border border-gray-300 shadow-sm text-sm font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
          >
            Export JSON
          </button>
          <button
            onClick={() => exportEvents("csv")}
            class="inline-flex items-center px-3 py-2 border border-gray-300 shadow-sm text-sm font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
          >
            Export CSV
          </button>
        </div>
      </div>

      {/* Results */}
      <div class="bg-white shadow rounded-lg">
        <div class="px-6 py-4 border-b border-gray-200">
          <div class="flex items-center justify-between">
            <h3 class="text-lg font-medium text-gray-900">
              Events ({eventData.totalEvents.toLocaleString()})
            </h3>
            <div class="flex items-center space-x-4">
              <span class="text-sm text-gray-500">Page size:</span>
              <select
                value={pagination.pageSize}
                onChange={(e) => handlePageSizeChange(Number(e.currentTarget.value))}
                class="border border-gray-300 rounded-md px-2 py-1 text-sm"
              >
                <option value={10}>10</option>
                <option value={20}>20</option>
                <option value={50}>50</option>
                <option value={100}>100</option>
              </select>
            </div>
          </div>
        </div>

        {eventData.isLoading ? (
          <div class="p-6 text-center">
            <div class="animate-spin mx-auto h-8 w-8 text-gray-400" />
            <p class="mt-2 text-sm text-gray-500">Loading events...</p>
          </div>
        ) : eventData.error ? (
          <div class="p-6 text-center">
            <svg class="mx-auto h-12 w-12 text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
            </svg>
            <h3 class="mt-2 text-sm font-medium text-gray-900">Error Loading Events</h3>
            <p class="mt-1 text-sm text-gray-500">{eventData.error}</p>
          </div>
        ) : eventData.events.length === 0 ? (
          <div class="p-6 text-center">
            <svg class="mx-auto h-12 w-12 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10"></path>
            </svg>
            <h3 class="mt-2 text-sm font-medium text-gray-900">No events found</h3>
            <p class="mt-1 text-sm text-gray-500">Try adjusting your filters.</p>
          </div>
        ) : (
          <>
            <div class="overflow-x-auto">
              <table class="min-w-full divide-y divide-gray-200">
                <thead class="bg-gray-50">
                  <tr>
                    <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Event ID</th>
                    <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Topic</th>
                    <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Type</th>
                    <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Timestamp</th>
                    <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Actions</th>
                  </tr>
                </thead>
                <tbody class="bg-white divide-y divide-gray-200">
                  {eventData.events.map((event) => (
                    <tr key={event.id} class="hover:bg-gray-50">
                      <td class="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">{event.id}</td>
                      <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-900">{event.topic || "N/A"}</td>
                      <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-900">{event.type}</td>
                      <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                        {new Date(event.timestamp).toLocaleString()}
                      </td>
                      <td class="px-6 py-4 whitespace-nowrap text-sm font-medium">
                        <button
                          onClick={() => setSelectedEvent(event)}
                          class="text-blue-600 hover:text-blue-900"
                        >
                          View Details
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Pagination */}
            {totalPages > 1 && (
              <div class="px-6 py-4 border-t border-gray-200">
                <div class="flex items-center justify-between">
                  <div class="text-sm text-gray-700">
                    Showing {((pagination.page - 1) * pagination.pageSize) + 1} to{" "}
                    {Math.min(pagination.page * pagination.pageSize, pagination.total)} of{" "}
                    {pagination.total} results
                  </div>
                  <div class="flex space-x-2">
                    <button
                      onClick={() => handlePageChange(pagination.page - 1)}
                      disabled={pagination.page <= 1}
                      class="px-3 py-2 border border-gray-300 rounded-md text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      Previous
                    </button>
                    <span class="px-3 py-2 text-sm text-gray-700">
                      Page {pagination.page} of {totalPages}
                    </span>
                    <button
                      onClick={() => handlePageChange(pagination.page + 1)}
                      disabled={pagination.page >= totalPages}
                      class="px-3 py-2 border border-gray-300 rounded-md text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      Next
                    </button>
                  </div>
                </div>
              </div>
            )}
          </>
        )}
      </div>

      {/* Event Details Modal */}
      {selectedEvent && (
        <div class="fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full z-50">
          <div class="relative top-20 mx-auto p-5 border w-11/12 md:w-3/4 lg:w-1/2 shadow-lg rounded-md bg-white">
            <div class="mt-3">
              <div class="flex items-center justify-between mb-4">
                <h3 class="text-lg font-medium text-gray-900">Event Details</h3>
                <button
                  onClick={() => setSelectedEvent(null)}
                  class="text-gray-400 hover:text-gray-600"
                >
                  <svg class="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
                  </svg>
                </button>
              </div>
              
              <div class="space-y-4">
                <div>
                  <h4 class="text-sm font-medium text-gray-500 uppercase tracking-wider">Event ID</h4>
                  <p class="mt-1 text-sm text-gray-900">{selectedEvent.id}</p>
                </div>
                
                <div>
                  <h4 class="text-sm font-medium text-gray-500 uppercase tracking-wider">Topic</h4>
                  <p class="mt-1 text-sm text-gray-900">{selectedEvent.topic || "N/A"}</p>
                </div>
                
                <div>
                  <h4 class="text-sm font-medium text-gray-500 uppercase tracking-wider">Type</h4>
                  <p class="mt-1 text-sm text-gray-900">{selectedEvent.type}</p>
                </div>
                
                <div>
                  <h4 class="text-sm font-medium text-gray-500 uppercase tracking-wider">Timestamp</h4>
                  <p class="mt-1 text-sm text-gray-900">{new Date(selectedEvent.timestamp).toLocaleString()}</p>
                </div>
                
                <div>
                  <h4 class="text-sm font-medium text-gray-500 uppercase tracking-wider">Payload</h4>
                  <div class="mt-1 bg-gray-50 rounded border p-3">
                    <pre class="text-xs text-gray-700 overflow-x-auto">{JSON.stringify(selectedEvent.payload, null, 2)}</pre>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
} 