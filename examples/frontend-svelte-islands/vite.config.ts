import { defineConfig } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';

export default defineConfig({
  plugins: [svelte()],
  build: {
    manifest: true,
    outDir: '../../viet-template-runtime/target/classes/static/dist',
    rollupOptions: {
      input: {
        counter: 'src/pages/counter/index.ts',
      },
    },
  },
  server: {
    cors: true,
    strictPort: true,
    origin: 'http://localhost:5173',
  },
});
