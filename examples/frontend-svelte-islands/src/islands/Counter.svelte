<script lang="ts">
  import { formatNumber } from '../shared/format';

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

<div class="counter-card">
  <p class="count-display">Current count: <strong>{formatNumber(count)}</strong></p>
  <button onclick={increment} type="button" class="btn">
    Increment Count
  </button>
</div>

<style>
  .counter-card {
    border: 1px solid #e2e8f0;
    border-radius: 8px;
    padding: 1.5rem;
    max-width: 300px;
    background: #ffffff;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  }
  .count-display {
    font-size: 1.125rem;
    margin-bottom: 1rem;
  }
  .btn {
    background-color: #3b82f6;
    color: white;
    border: none;
    border-radius: 6px;
    padding: 0.5rem 1rem;
    font-weight: 500;
    cursor: pointer;
    transition: background-color 0.2s;
  }
  .btn:hover {
    background-color: #2563eb;
  }
</style>
