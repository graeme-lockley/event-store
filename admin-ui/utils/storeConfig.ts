// Store configuration management with persistent file storage
export interface EventStoreConfig {
  name: string;
  url: string;
  port: number;
}

// Get data directory from environment or use default
const DATA_DIR = Deno.env.get("DATA_DIR") || "./data";
const STORES_FILE = `${DATA_DIR}/stores.json`;

// Ensure data directory exists
async function ensureDataDir() {
  try {
    await Deno.mkdir(DATA_DIR, { recursive: true });
  } catch (error) {
    if (!(error instanceof Deno.errors.AlreadyExists)) {
      throw error;
    }
  }
}

// Load stores from file
async function loadStores(): Promise<EventStoreConfig[]> {
  try {
    await ensureDataDir();
    const fileContent = await Deno.readTextFile(STORES_FILE);
    const stores = JSON.parse(fileContent);
    return Array.isArray(stores) ? stores : [];
  } catch (error) {
    if (error instanceof Deno.errors.NotFound) {
      // File doesn't exist, return default stores
      const defaultStores: EventStoreConfig[] = [
        { name: "Local Store", url: "http://localhost", port: 8000 },
        { name: "Secondary Store", url: "http://localhost", port: 8001 },
      ];
      await saveStores(defaultStores);
      return defaultStores;
    }
    throw error;
  }
}

// Save stores to file
async function saveStores(stores: EventStoreConfig[]): Promise<void> {
  await ensureDataDir();
  await Deno.writeTextFile(STORES_FILE, JSON.stringify(stores, null, 2));
}

// Get all stores
export async function getStores(): Promise<EventStoreConfig[]> {
  const stores = await loadStores();
  return [...stores]; // Return a copy to prevent direct modification
}

// Add a new store
export async function addStore(store: EventStoreConfig): Promise<void> {
  const stores = await loadStores();

  // Check if store with same name already exists
  if (stores.some((s) => s.name === store.name)) {
    throw new Error(`Store with name "${store.name}" already exists`);
  }

  // Check if store with same URL and port already exists
  if (stores.some((s) => s.url === store.url && s.port === store.port)) {
    throw new Error(`Store with URL ${store.url}:${store.port} already exists`);
  }

  stores.push(store);
  await saveStores(stores);
}

// Remove a store
export async function removeStore(name: string): Promise<boolean> {
  const stores = await loadStores();
  const initialLength = stores.length;

  if (Deno.env.get("DEBUG")) {
    console.log(
      `🗑️ RemoveStore: name="${name}", initialLength=${initialLength}`,
    );
    console.log(`🗑️ Available stores:`, stores.map((s) => `"${s.name}"`));
  }

  const filteredStores = stores.filter((store) => {
    const matches = store.name !== name;
    if (Deno.env.get("DEBUG")) {
      console.log(`🗑️ Comparing "${store.name}" !== "${name}" = ${matches}`);
    }
    return matches;
  });

  if (Deno.env.get("DEBUG")) {
    console.log(`🗑️ Filtered length: ${filteredStores.length}`);
  }

  if (filteredStores.length < initialLength) {
    await saveStores(filteredStores);
    if (Deno.env.get("DEBUG")) {
      console.log(`✅ Store "${name}" removed successfully`);
    }
    return true;
  }

  if (Deno.env.get("DEBUG")) {
    console.log(`❌ Store "${name}" not found`);
  }
  return false;
}

// Update a store
export async function updateStore(
  name: string,
  updates: Partial<EventStoreConfig>,
): Promise<boolean> {
  const stores = await loadStores();
  const storeIndex = stores.findIndex((store) => store.name === name);

  if (storeIndex === -1) {
    return false;
  }

  // Check for conflicts with other stores
  const otherStores = stores.filter((_, index) => index !== storeIndex);

  if (updates.name && otherStores.some((s) => s.name === updates.name)) {
    throw new Error(`Store with name "${updates.name}" already exists`);
  }

  if (
    updates.url && updates.port &&
    otherStores.some((s) => s.url === updates.url && s.port === updates.port)
  ) {
    throw new Error(
      `Store with URL ${updates.url}:${updates.port} already exists`,
    );
  }

  stores[storeIndex] = { ...stores[storeIndex], ...updates };
  await saveStores(stores);
  return true;
}

// Get store by name
export async function getStoreByName(
  name: string,
): Promise<EventStoreConfig | undefined> {
  const stores = await loadStores();
  return stores.find((store) => store.name === name);
}
