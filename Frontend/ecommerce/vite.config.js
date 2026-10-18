import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Always use http://localhost:5173 — the backend's CORS setting allows exactly this origin.
    // strictPort: if 5173 is busy (an old dev server still running), Vite stops with an error
    // instead of silently moving to 5174, which the backend would block.
    port: 5173,
    strictPort: true,
  },
  preview: {
    port: 5173,
    strictPort: true,
  },
})
