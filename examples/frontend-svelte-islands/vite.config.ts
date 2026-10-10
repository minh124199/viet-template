import { defineConfig } from 'vite';
import { svelte, vitePreprocess } from '@sveltejs/vite-plugin-svelte';

export default defineConfig({
  plugins: [
    svelte({
      preprocess: vitePreprocess(),
    }),
  ],
  build: {
    manifest: true,
    outDir: 'dist',
    rollupOptions: {
      preserveEntrySignatures: 'allow-extension',
      input: {
        employees: 'src/pages/employees/index.ts',
        counter: 'src/pages/counter/index.ts',
        payroll: 'src/pages/payroll/Payroll.svelte',
      },
    },
  },
  server: {
    cors: true,
    strictPort: true,
    origin: process.env.VITE_DEV_ORIGIN || 'http://localhost:5173',
  },
});
