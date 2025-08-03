import { useState } from "preact/hooks";

interface RemoveStoreProps {
  storeName: string;
  url: string;
  port: number;
  onRemove?: () => void;
}

export default function RemoveStore(
  { storeName, url, port, onRemove }: RemoveStoreProps,
) {
  const [isRemoving, setIsRemoving] = useState(false);
  const [showConfirmation, setShowConfirmation] = useState(false);
  const [result, setResult] = useState<
    {
      success: boolean;
      message: string;
    } | null
  >(null);

  const handleRemove = async () => {
    setIsRemoving(true);
    setResult(null);

    try {
      const response = await fetch(`/api/remove-store`, {
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

      const data = await response.json();

      if (response.ok) {
        setResult({
          success: true,
          message: `✅ Store "${storeName}" removed successfully.`,
        });
        // Call the parent callback to refresh the page or update the UI
        if (onRemove) {
          setTimeout(() => {
            onRemove();
          }, 1500);
        }
      } else {
        setResult({
          success: false,
          message: `❌ Failed to remove store: ${data.error}`,
        });
      }
    } catch (error) {
      setResult({
        success: false,
        message: `❌ Failed to remove store: ${
          error instanceof Error ? error.message : "Unknown error"
        }`,
      });
    } finally {
      setIsRemoving(false);
      setShowConfirmation(false);
    }
  };

  if (showConfirmation) {
    return (
      <div class="flex space-x-2">
        <button
          onClick={handleRemove}
          disabled={isRemoving}
          class="inline-flex items-center px-3 py-2 border border-transparent text-sm leading-4 font-medium rounded-md text-white bg-red-600 hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-red-500 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {isRemoving
            ? (
              <>
                <svg
                  class="animate-spin -ml-1 mr-2 h-4 w-4 text-white"
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
                Removing...
              </>
            )
            : (
              "Confirm Remove"
            )}
        </button>
        <button
          onClick={() => setShowConfirmation(false)}
          disabled={isRemoving}
          class="inline-flex items-center px-3 py-2 border border-gray-300 shadow-sm text-sm leading-4 font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          Cancel
        </button>
      </div>
    );
  }

  return (
    <div>
      <button
        onClick={() => setShowConfirmation(true)}
        disabled={isRemoving}
        class="inline-flex items-center px-3 py-2 border border-transparent text-sm leading-4 font-medium rounded-md text-white bg-red-600 hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-red-500 disabled:opacity-50 disabled:cursor-not-allowed"
      >
        Remove
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
