// Integration test harness for Event Store and Admin UI
// Starts both servers on custom test ports with clean data/config directories
// Provides start/stop/wait helpers for integration tests

import { createTestEventStoreClient } from "../../utils/eventStore.ts";

const TEST_EVENT_STORE_PORT = 18000;
const TEST_ADMIN_UI_PORT = 18002;
const TEST_ADMIN_UI_DATA = "./test-data";
const TEST_EVENT_STORE_DATA = `${TEST_ADMIN_UI_DATA}/event-store-data`;
const TEST_EVENT_STORE_CONFIG = `${TEST_ADMIN_UI_DATA}/event-store-config`;

interface TestServer {
  process: Deno.ChildProcess;
  url: string;
  stop: () => Promise<void>;
}

async function swallowAsyncError(body: () => Promise<any>) {
  try {
    await body();
  } catch (_) {
    // Ignore errors
  }
}

async function waitForHealth(url: string, timeout = 10000): Promise<boolean> {
  const start = Date.now();

  while (Date.now() - start < timeout) {
    try {
      const res = await fetch(url);
      if (res.ok) {
        await swallowAsyncError(() => res.text());
        return true;
      } else {
        await swallowAsyncError(() => res.text());
      }
    } catch (_error) {
      // Ignore errors
    }

    await new Promise(r => setTimeout(r, 200));
  }

  return false;
}

async function cleanDir(path: string) {
  await swallowAsyncError(() => Deno.remove(path, { recursive: true }));
}

export async function startEventStore(): Promise<TestServer> {
  // Start Event Store server with environment variables
  const process = new Deno.Command("deno", {
    args: [
      "run", "-A", "--unstable", "../event-store/mod.ts"
    ],
    env: {
      "PORT": String(TEST_EVENT_STORE_PORT),
      "DATA_DIR": TEST_EVENT_STORE_DATA,
      "CONFIG_DIR": TEST_EVENT_STORE_CONFIG
    },
    stdout: "null",
    stderr: "null"
  }).spawn();

  // Use test-optimized EventStoreClient for faster startup detection
  const client = createTestEventStoreClient({
    name: "test-event-store",
    url: "http://localhost",
    port: TEST_EVENT_STORE_PORT,
  });

  try {
    await client.waitForServer({
      maxWaitTime: 5000, // 5 seconds for integration tests
      pollInterval: 100,
      throwOnTimeout: true,
    });
  } catch (error) {
    process.kill("SIGTERM");
    await process.status;
    throw new Error(`Event Store failed to start: ${error}`);
  }

  return {
    process,
    url: `http://localhost:${TEST_EVENT_STORE_PORT}`,
    stop: async () => {
      process.kill("SIGTERM");
      await process.status;
      await cleanDir(TEST_EVENT_STORE_DATA);
      await cleanDir(TEST_EVENT_STORE_CONFIG);
    }
  };
}

export async function startAdminUI(): Promise<TestServer> {
  // Start Admin UI server with environment variables
  const process = new Deno.Command("deno", {
    args: [
      "run", "-A", "main.ts"
    ],
    cwd: ".",
    env: {
      "PORT": String(TEST_ADMIN_UI_PORT),
      "DATA_DIR": TEST_ADMIN_UI_DATA,
      "DEBUG": "1"
    },
    stdout: "piped",
    stderr: "piped"
  }).spawn();

  const url = `http://localhost:${TEST_ADMIN_UI_PORT}/api/stores`;
  const ready = await waitForHealth(url);
  if (!ready) {
    // Try to get error output
    try {
      const stderr = await process.stderr.getReader().read();
      const stderrText = stderr.value ? new TextDecoder().decode(stderr.value) : "";
      console.error("Test server failed to start. Stderr:", stderrText);
    } catch (e) {
      console.error("Could not read stderr:", e);
    }
    throw new Error("Test server did not start in time");
  }

  return {
    process,
    url: `http://localhost:${TEST_ADMIN_UI_PORT}`,
    stop: async () => {
      process.kill("SIGTERM");
      await process.status;

      // Properly close/cancel stdout and stderr to avoid resource leaks
      if (process.stdout) {
        await swallowAsyncError(() => process.stdout.cancel());
      }
      if (process.stderr) {
        await swallowAsyncError(() => process.stderr.cancel());
      }

      await cleanDir(TEST_ADMIN_UI_DATA);
    }
  };
}

export async function startIntegrationServers() {
  await cleanDir(TEST_ADMIN_UI_DATA);
  await Deno.mkdir(TEST_ADMIN_UI_DATA, { recursive: true });
  await Deno.mkdir(TEST_EVENT_STORE_DATA, { recursive: true });
  await Deno.mkdir(TEST_EVENT_STORE_CONFIG, { recursive: true });

  const eventStore = await startEventStore();
  const adminUI = await startAdminUI();
  return {
    eventStore,
    adminUI,
    stop: async () => {
      await adminUI.stop();
      await eventStore.stop();
    }
  };
} 