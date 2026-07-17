import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [react()],
  server: {
    // Local dev: Vite serves the UI, Spring serves the API
    proxy: { "/api": "http://localhost:8080" },
  },
});
