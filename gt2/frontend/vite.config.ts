import { createHash } from "node:crypto";
import { readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import react from "@vitejs/plugin-react";
import { defineConfig, type Plugin } from "vite";

/**
 * Stamps a build id into the service worker's cache names.
 *
 * public/sw.js is copied verbatim by Vite, so it cannot use an import or an env var.
 * Without this the cache names would be constant and a deploy would keep serving the
 * previous build's assets. The id is a hash of the emitted filenames, which Vite
 * content-hashes, so any change to the app's code or styles produces a new id.
 */
function pwaBuildId(): Plugin {
  return {
    name: "grindtrack-pwa-build-id",
    apply: "build",
    writeBundle(options, bundle) {
      const dir = options.dir ?? "dist";
      const hash = createHash("sha256");
      for (const name of Object.keys(bundle).sort()) hash.update(name);
      const id = hash.digest("hex").slice(0, 12);

      const sw = join(dir, "sw.js");
      const source = readFileSync(sw, "utf8");
      if (!source.includes("__BUILD_ID__")) {
        throw new Error("sw.js has no __BUILD_ID__ placeholder — cache names would never change");
      }
      writeFileSync(sw, source.replace("__BUILD_ID__", id));
    },
  };
}

export default defineConfig({
  plugins: [react(), pwaBuildId()],
  server: {
    // Local dev: Vite serves the UI, Spring serves the API
    proxy: { "/api": "http://localhost:8080" },
  },
});
