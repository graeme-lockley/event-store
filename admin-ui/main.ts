/// <reference no-default-lib="true" />
/// <reference lib="dom" />
/// <reference lib="dom.iterable" />
/// <reference lib="dom.asynciterable" />
/// <reference lib="deno.ns" />

import "$std/dotenv/load.ts";

import { start } from "$fresh/server.ts";
import manifest from "./fresh.gen.ts";
import config from "./fresh.config.ts";

// Ensure DATA_DIR exists before starting the server
const DATA_DIR = Deno.env.get("DATA_DIR") || "./data";
try {
  await Deno.mkdir(DATA_DIR, { recursive: true });
  // Optionally, log for debugging
  if (Deno.env.get("DEBUG")) {
    console.log(`✅ Ensured DATA_DIR exists: ${DATA_DIR}`);
  }
} catch (err) {
  console.error(`❌ Failed to create DATA_DIR (${DATA_DIR}):`, err);
  throw err;
}

await start(manifest, config);
