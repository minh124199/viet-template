import { mount } from 'svelte';
import Counter from '../../islands/Counter.svelte';
import { readClientData } from '../../client-data';

interface CounterModel {
  initialCount: number;
}

const target = document.getElementById('counter-island');

if (target) {
  // Clear SSR fallback content before mounting interactive island
  target.innerHTML = '';

  const model = readClientData<CounterModel>('counter-state');
  mount(Counter, {
    target,
    props: {
      initialCount: model.initialCount,
    },
  });
}
