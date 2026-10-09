# Framework Islands Architecture

Viet Template supports the **Islands Architecture** pattern: mounting interactive framework components (such as Svelte, Vue, or React) into specific DOM containers on top of a server-rendered HTML page.

## Mounting vs. Hydration

It is crucial to distinguish between **Client Mounting** and **SSR Hydration**:

- **Client Mounting (Supported & Recommended)**: Viet Template renders semantic HTML into a target element (e.g. `<div id="counter-island"><p>Current count: 0</p></div>`). On page load, client-side JavaScript reads initial state from `$clientData` and mounts a framework component into that DOM node.
- **SSR Hydration**: Requires running Node.js or a JavaScript runtime on the server to execute the component code and generate framework-specific hydration markers. Viet Template intentionally avoids running Node.js in the JVM request pipeline. Client mounting provides the same end-user reactivity without server-side JavaScript runtime overhead.

## Recommended Pattern: TypeScript Bootstrap

Rather than pointing directly to `.svelte` or `.vue` files in your templates, the recommended practice is to use a lightweight TypeScript bootstrap module (e.g. `index.ts`):

```text
src/pages/counter/
├── Counter.svelte
└── index.ts
```

### 1. The Component (`Counter.svelte`)

```svelte
<script lang="ts">
  interface Props {
    initialCount?: number;
  }

  let { initialCount = 0 }: Props = $props();
  // svelte-ignore state_referenced_locally
  let count = $state(initialCount);

  function increment() {
    count += 1;
  }
</script>

<div class="counter-box">
  <button onclick={increment} type="button">
    Clicked {count} times
  </button>
</div>

<style>
  .counter-box {
    margin: 1rem 0;
  }
  button {
    padding: 0.5rem 1rem;
    font-size: 1rem;
    cursor: pointer;
  }
</style>
```

### 2. The Bootstrap Script (`index.ts`)

```typescript
import { mount } from 'svelte';
import Counter from './Counter.svelte';
import { readClientData } from '../../client-data';

interface CounterState {
  initialCount: number;
}

const target = document.getElementById('counter-island');

if (target) {
  const state = readClientData<CounterState>('counter-state');
  mount(Counter, {
    target,
    props: {
      initialCount: state.initialCount,
    },
  });
}
```

### 3. The Server Template (`counter.vtl`)

```vtl
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>Counter Island</title>
  $assets.head("src/pages/counter/index.ts")
</head>
<body>
  <h1>Interactive Counter Island</h1>

  <!-- Fallback SSR content rendered for users without JavaScript -->
  <div id="counter-island">
    <p>Current count: $!model.initialCount (JavaScript loading...)</p>
  </div>

  $clientData.script("counter-state", $model)
  $assets.body("src/pages/counter/index.ts")
</body>
</html>
```

## Direct Framework Entries

If your Vite configuration includes plugins such as `@sveltejs/vite-plugin-svelte` or `@vitejs/plugin-vue`, Vite may emit `.svelte` or `.vue` files directly in `manifest.json`.

Viet Template does not enforce a file extension whitelist:
```vtl
## Valid if emitted as an entrypoint in manifest.json:
$assets.head("src/pages/counter/Counter.svelte")
$assets.body("src/pages/counter/Counter.svelte")
```

However, a TypeScript bootstrap (`index.ts`) is strongly recommended because it cleanly decouples DOM element selection, client-data extraction, and error handling from the component definition.
