// Centralized dependencies for the Event Store
export { Application, Router } from "https://deno.land/x/oak@v12.6.1/mod.ts";
export type { Context } from "https://deno.land/x/oak@v12.6.1/mod.ts";
// Simple UUID generation function
export function uuidv4(): string {
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, function (c) {
    const r = Math.random() * 16 | 0;
    const v = c == "x" ? r : (r & 0x3 | 0x8);
    return v.toString(16);
  });
}
export { ensureDir, walk } from "https://deno.land/std@0.208.0/fs/mod.ts";
export { dirname, join } from "https://deno.land/std@0.208.0/path/mod.ts";
import Ajv from "https://esm.sh/ajv@8.17.1";
import addKeywords from "https://esm.sh/ajv-keywords@5.1.0";

export { addKeywords, Ajv };
