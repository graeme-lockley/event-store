import { useState } from "preact/hooks";

interface TestConnectionProps {
  storeName: string;
  url: string;
  port: number;
}

export default function TestConnection(
  { storeName, url, port }: TestConnectionProps,
) {
  const [isTesting, setIsTesting] = useState(false);
  const [result, setResult] = useState<
    {
      success: boolean;
      message: string;
    } | null
  >(null);

  const testConnection = async () => {
    // Clear any previous result immediately
    setResult(null);
    setIsTesting(true);

    try {
      console.log(
        `TestConnection: Testing connection to ${storeName} at ${url}:${port}`,
      );

      const response = await fetch(`/api/test-connection`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          name: storeName,
          url,
          port,
        }),
      });

      console.log(`TestConnection: Response status: ${response.status}`);

      const data = await response.json();
      console.log(`TestConnection: Response data:`, data);

      // Only show success if both response is ok AND data.success is true
      if (response.ok && data.success === true) {
        setResult({
          success: true,
          message: `✅ Connection successful! Store is healthy with ${
            data.consumers || 0
          } consumers and ${
            (data.runningDispatchers || []).length
          } running dispatchers.`,
        });
      } else {
        setResult({
          success: false,
          message: `❌ Connection failed: ${
            data.error || data.message || "Unknown error"
          }`,
        });
      }
    } catch (error) {
      console.error(`TestConnection: Error:`, error);
      setResult({
        success: false,
        message: `❌ Connection failed: ${
          error instanceof Error ? error.message : "Unknown error"
        }`,
      });
    } finally {
      setIsTesting(false);
    }
  };

  return (
    <div>
      <button
        onClick={testConnection}
        disabled={isTesting}
        class="inline-flex items-center px-3 py-2 border border-gray-300 shadow-sm text-sm leading-4 font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed"
      >
        {isTesting
          ? (
            <>
              <svg
                class="animate-spin -ml-1 mr-2 h-4 w-4 text-gray-500"
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
              Testing...
            </>
          )
          : (
            "Test Connection"
          )}
      </button>

      {result && (
        <div
          class={`mt-2 p-2 rounded text-sm ${
            result.success
              ? "bg-green-50 text-green-800 border border-green-200"
              : "bg-red-50 text-red-800 border border-red-200"
          }`}
        >
          {result.message}
        </div>
      )}
    </div>
  );
}
