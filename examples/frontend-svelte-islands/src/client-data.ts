/**
 * Safe client data extraction utility for Viet Template applications.
 */
export function readClientData<T>(id: string): T {
  const element = document.querySelector<HTMLScriptElement>(
    `script[type="application/json"][data-vt-client-data="${id}"]`
  );

  if (!element) {
    throw new Error(`Client data element with ID '${id}' was not found in the DOM.`);
  }

  const content = element.textContent?.trim();
  if (!content) {
    throw new Error(`Client data element with ID '${id}' has empty content.`);
  }

  return JSON.parse(content) as T;
}
