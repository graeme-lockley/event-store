import { useState, useEffect } from "preact/hooks";
import { EventStoreClient, Topic } from "../utils/eventStore.ts";

interface TopicConfigProps {
  store: string;
  topic: string;
}

interface TopicConfigData {
  config: Topic | null;
  isLoading: boolean;
  error: string | null;
}

export default function TopicConfig({ store, topic }: TopicConfigProps) {
  const [data, setData] = useState<TopicConfigData>({
    config: null,
    isLoading: true,
    error: null,
  });

  const loadConfig = async () => {
    setData(prev => ({
      ...prev,
      isLoading: true,
      error: null,
    }));

    try {
      const res = await fetch(`/api/store-topics?store=${encodeURIComponent(store)}`);
      if (!res.ok) throw new Error(`API error: ${res.status}`);
      const { topics, error } = await res.json();
      if (error) throw new Error(error);
      
      const topicConfig = topics.find((t: Topic) => t.name === topic);
      if (!topicConfig) throw new Error(`Topic '${topic}' not found`);
      
      setData({
        config: topicConfig,
        isLoading: false,
        error: null,
      });
    } catch (error) {
      setData({
        config: null,
        isLoading: false,
        error: error instanceof Error ? error.message : "Failed to load topic config",
      });
    }
  };

  useEffect(() => {
    loadConfig();
  }, [store, topic]);

  const formatSchema = (schema: any) => {
    return JSON.stringify(schema, null, 2);
  };

  if (data.isLoading) {
    return (
      <div class="bg-white shadow rounded-lg p-6">
        <div class="animate-pulse">
          <div class="h-4 bg-gray-200 rounded w-1/4 mb-4"></div>
          <div class="h-4 bg-gray-200 rounded w-1/2 mb-2"></div>
          <div class="h-4 bg-gray-200 rounded w-3/4"></div>
        </div>
      </div>
    );
  }

  if (data.error) {
    return (
      <div class="bg-white shadow rounded-lg p-6">
        <div class="text-center">
          <svg class="mx-auto h-12 w-12 text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
          </svg>
          <h3 class="mt-2 text-sm font-medium text-gray-900">Configuration Error</h3>
          <p class="mt-1 text-sm text-gray-500">{data.error}</p>
          <div class="mt-6">
            <button
              onClick={loadConfig}
              class="inline-flex items-center px-4 py-2 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-red-600 hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-red-500"
            >
              Try Again
            </button>
          </div>
        </div>
      </div>
    );
  }

  if (!data.config) {
    return (
      <div class="bg-white shadow rounded-lg p-6">
        <div class="text-center">
          <svg class="mx-auto h-12 w-12 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10"></path>
          </svg>
          <h3 class="mt-2 text-sm font-medium text-gray-900">No Configuration</h3>
          <p class="mt-1 text-sm text-gray-500">Topic configuration not found.</p>
        </div>
      </div>
    );
  }

  return (
    <div class="bg-white shadow rounded-lg">
      <div class="px-6 py-4 border-b border-gray-200">
        <h3 class="text-lg leading-6 font-medium text-gray-900">Topic Configuration</h3>
        <p class="mt-1 text-sm text-gray-500">Schema definitions and topic settings</p>
      </div>
      
      <div class="px-6 py-4">
        {/* Basic Info */}
        <div class="grid grid-cols-1 md:grid-cols-2 gap-6 mb-6">
          <div>
            <h4 class="text-sm font-medium text-gray-500 uppercase tracking-wider">Topic Name</h4>
            <p class="mt-1 text-lg text-gray-900">{data.config.name}</p>
          </div>
          <div>
            <h4 class="text-sm font-medium text-gray-500 uppercase tracking-wider">Current Sequence</h4>
            <p class="mt-1 text-lg text-gray-900">{data.config.sequence.toLocaleString()}</p>
          </div>
        </div>

        {/* Schemas */}
        <div class="mt-8">
          <h4 class="text-sm font-medium text-gray-500 uppercase tracking-wider mb-4">
            Event Schemas ({data.config.schemas.length})
          </h4>
          
          {data.config.schemas.map((schema, index) => (
            <div key={index} class="mb-6 last:mb-0">
              <div class="bg-gray-50 rounded-lg p-4">
                <div class="flex items-center justify-between mb-3">
                  <h5 class="text-sm font-medium text-gray-900">
                    Event Type: <span class="text-blue-600">{schema.eventType}</span>
                  </h5>
                  <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800">
                    {schema.type}
                  </span>
                </div>
                
                <div class="space-y-3">
                  {/* Schema Properties */}
                  {schema.properties && Object.keys(schema.properties).length > 0 && (
                    <div>
                      <h6 class="text-xs font-medium text-gray-500 uppercase tracking-wider mb-2">Properties</h6>
                      <div class="bg-white rounded border p-3">
                        <pre class="text-xs text-gray-700 overflow-x-auto">{formatSchema(schema.properties)}</pre>
                      </div>
                    </div>
                  )}
                  
                  {/* Required Fields */}
                  {schema.required && schema.required.length > 0 && (
                    <div>
                      <h6 class="text-xs font-medium text-gray-500 uppercase tracking-wider mb-2">Required Fields</h6>
                      <div class="flex flex-wrap gap-2">
                        {schema.required.map((field: string) => (
                          <span key={field} class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800">
                            {field}
                          </span>
                        ))}
                      </div>
                    </div>
                  )}
                  
                  {/* Full Schema */}
                  <div>
                    <h6 class="text-xs font-medium text-gray-500 uppercase tracking-wider mb-2">Full Schema</h6>
                    <div class="bg-white rounded border p-3">
                      <pre class="text-xs text-gray-700 overflow-x-auto">{formatSchema(schema)}</pre>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
} 