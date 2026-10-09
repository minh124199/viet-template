/**
 * Safe client data extraction utility for Viet Template applications.
 */
function escapeSelector(id: string): string {
  if (typeof CSS !== 'undefined' && typeof CSS.escape === 'function') {
    return CSS.escape(id);
  }
  return id.replace(/["\\]/g, '\\$&');
}

export function readClientData<T>(id: string): T {
  const selector = `script[type="application/json"][data-vt-client-data="${escapeSelector(id)}"]`;
  const element = document.querySelector<HTMLScriptElement>(selector);

  if (!element) {
    throw new Error(`Client data element with ID '${id}' was not found in the DOM.`);
  }

  const content = element.textContent?.trim();
  if (!content) {
    throw new Error(`Client data element with ID '${id}' has empty content.`);
  }

  return JSON.parse(content) as T;
}

export function optionalClientData<T>(id: string, fallback?: T): T | undefined {
  try {
    const selector = `script[type="application/json"][data-vt-client-data="${escapeSelector(id)}"]`;
    const element = document.querySelector<HTMLScriptElement>(selector);
    if (!element) {
      return fallback;
    }
    const content = element.textContent?.trim();
    if (!content) {
      return fallback;
    }
    return JSON.parse(content) as T;
  } catch {
    return fallback;
  }
}
