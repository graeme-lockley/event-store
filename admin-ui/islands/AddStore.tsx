import { useState } from "preact/hooks";

interface AddStoreProps {
  onAdd?: () => void;
}

export default function AddStore({ onAdd }: AddStoreProps) {
  const [isAdding, setIsAdding] = useState(false);
  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState({
    name: "",
    url: "http://localhost",
    port: "8000",
  });
  const [result, setResult] = useState<
    {
      success: boolean;
      message: string;
    } | null
  >(null);

  const handleSubmit = async (e: Event) => {
    e.preventDefault();
    setIsAdding(true);
    setResult(null);

    try {
      const response = await fetch(`/api/add-store`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(formData),
      });

      const data = await response.json();

      if (response.ok) {
        setResult({
          success: true,
          message: `✅ Store "${formData.name}" added successfully!`,
        });
        // Reset form
        setFormData({ name: "", url: "http://localhost", port: "8000" });
        setShowForm(false);
        // Call the parent callback to refresh the page
        if (onAdd) {
          setTimeout(() => {
            onAdd();
          }, 1500);
        }
      } else {
        setResult({
          success: false,
          message: `❌ Failed to add store: ${data.error}`,
        });
      }
    } catch (error) {
      setResult({
        success: false,
        message: `❌ Failed to add store: ${
          error instanceof Error ? error.message : "Unknown error"
        }`,
      });
    } finally {
      setIsAdding(false);
    }
  };

  const handleInputChange = (field: string, value: string) => {
    setFormData((prev) => ({ ...prev, [field]: value }));
  };

  if (showForm) {
    return (
      <div class="border border-gray-200 rounded-lg p-4">
        <form onSubmit={handleSubmit} class="space-y-4">
          <div class="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <div>
              <label class="block text-sm font-medium text-gray-700">
                Store Name *
              </label>
              <input
                type="text"
                value={formData.name}
                onChange={(e) =>
                  handleInputChange(
                    "name",
                    (e.target as HTMLInputElement).value,
                  )}
                required
                class="mt-1 block w-full border-gray-300 rounded-md shadow-sm focus:ring-blue-500 focus:border-blue-500 sm:text-sm"
                placeholder="Store Name"
              />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700">
                URL *
              </label>
              <input
                type="url"
                value={formData.url}
                onChange={(e) =>
                  handleInputChange(
                    "url",
                    (e.target as HTMLInputElement).value,
                  )}
                required
                class="mt-1 block w-full border-gray-300 rounded-md shadow-sm focus:ring-blue-500 focus:border-blue-500 sm:text-sm"
                placeholder="http://localhost"
              />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700">
                Port *
              </label>
              <input
                type="number"
                value={formData.port}
                onChange={(e) =>
                  handleInputChange(
                    "port",
                    (e.target as HTMLInputElement).value,
                  )}
                required
                min="1"
                max="65535"
                class="mt-1 block w-full border-gray-300 rounded-md shadow-sm focus:ring-blue-500 focus:border-blue-500 sm:text-sm"
                placeholder="8000"
              />
            </div>
          </div>

          <div class="flex justify-end space-x-2">
            <button
              type="button"
              onClick={() => setShowForm(false)}
              disabled={isAdding}
              class="inline-flex items-center px-3 py-2 border border-gray-300 shadow-sm text-sm leading-4 font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isAdding}
              class="inline-flex items-center px-3 py-2 border border-transparent text-sm leading-4 font-medium rounded-md text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {isAdding
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
                    Adding...
                  </>
                )
                : (
                  "Add Store"
                )}
            </button>
          </div>
        </form>

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

  return (
    <button
      type="button"
      onClick={() => setShowForm(true)}
      class="w-full inline-flex justify-center items-center px-4 py-2 border border-gray-300 shadow-sm text-sm font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
    >
      <svg
        class="-ml-1 mr-2 h-5 w-5"
        fill="none"
        stroke="currentColor"
        viewBox="0 0 24 24"
      >
        <path
          stroke-linecap="round"
          stroke-linejoin="round"
          stroke-width="2"
          d="M12 6v6m0 0v6m0-6h6m-6 0H6"
        >
        </path>
      </svg>
      Add Event Store
    </button>
  );
}
