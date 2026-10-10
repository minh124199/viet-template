import { test, expect, type Page } from '@playwright/test';

function trackPageHealth(page: Page, baseURL?: string, options?: { javaScriptEnabled?: boolean }) {
  const consoleErrors: string[] = [];
  const failedRequests: string[] = [];
  const jsEnabled = options?.javaScriptEnabled ?? true;

  page.on('console', (msg) => {
    if (msg.type() === 'error') {
      consoleErrors.push(msg.text());
    }
  });

  page.on('pageerror', (err) => {
    consoleErrors.push(err.message);
  });

  page.on('response', (resp) => {
    if (resp.status() >= 400 && resp.url().startsWith(baseURL || 'http://127.0.0.1')) {
      failedRequests.push(`${resp.url()} returned ${resp.status()}`);
    }
  });

  page.on('requestfailed', (req) => {
    if (req.url().startsWith(baseURL || 'http://127.0.0.1')) {
      const errorText = req.failure()?.errorText || 'unknown';
      // When JavaScript is disabled, Chromium intentionally blocks script requests with internal csp policy
      if (!jsEnabled && (errorText === 'csp' || errorText === 'net::ERR_BLOCKED_BY_CLIENT')) {
        return;
      }
      failedRequests.push(`${req.url()} failed: ${errorText}`);
    }
  });

  return {
    consoleErrors,
    failedRequests,
    assertCleanHealth() {
      expect(consoleErrors, 'Expected zero console errors during execution').toEqual([]);
      expect(failedRequests, 'Expected zero failed requests during execution').toEqual([]);
    },
  };
}

test.describe('Viet Template Frontend Browser E2E Qualification', () => {

  test.beforeEach(async ({ request, baseURL }) => {
    // Reset state before each test run for determinism
    const res = await request.post(`${baseURL || ''}/api/test/reset`);
    expect(res.ok()).toBeTruthy();
  });

  test('renders SSR content, mounts Svelte island, and updates from Java REST', async ({ page, baseURL }) => {
    const health = trackPageHealth(page, baseURL);
    const assetResponses: { url: string; status: number }[] = [];

    // Capture asset network responses
    page.on('response', (resp) => {
      if (resp.url().includes('/assets/')) {
        assetResponses.push({ url: resp.url(), status: resp.status() });
      }
    });

    // 1. Navigate to employee page
    const response = await page.goto('/employees/42');
    expect(response?.status()).toBe(200);

    // 2. Assert server-side rendering happened: semantic HTML visible in initial document
    const ssrHeading = page.locator('[data-testid="ssr-heading"]');
    await expect(ssrHeading).toHaveText('Staff Directory');

    const ssrEmployeeName = page.locator('[data-testid="ssr-employee-name"]');
    await expect(ssrEmployeeName).toHaveText('Employee: Jane Doe');

    const ssrDepartment = page.locator('[data-testid="ssr-department"]');
    await expect(ssrDepartment).toContainText('Department: Engineering');

    // 3. Assert Client Data Bridge emitted valid <script type="application/json"> block
    const clientDataScript = page.locator('script[type="application/json"][data-vt-client-data="employees-data"]');
    await expect(clientDataScript).toHaveCount(1);
    const clientDataText = await clientDataScript.textContent();
    expect(clientDataText).toBeTruthy();
    const parsedData = JSON.parse(clientDataText || '{}');
    expect(parsedData.departmentName).toBe('Engineering');
    expect(parsedData.employees[0].id).toBe('42');
    expect(parsedData.employees[0].followers).toBe(3);

    // 4. Assert Svelte 5 island mounted observably
    const svelteIsland = page.locator('[data-testid="svelte-island"]');
    await expect(svelteIsland).toBeVisible();
    await expect(svelteIsland).toHaveAttribute('data-mounted', 'true');
    await expect(page.locator('#employees-island')).toHaveAttribute('data-mounted', 'true');

    // Initial value in mounted island matches Java model from Client Data Bridge
    const followersVal = page.locator('[data-testid="followers-val-42"]');
    await expect(followersVal).toHaveText('3');

    // 5. REST interaction: click "Follow", observe real network roundtrip
    const followBtn = page.locator('[data-testid="follow-btn-42"]');
    await expect(followBtn).toBeVisible();

    const [restResponse] = await Promise.all([
      page.waitForResponse((resp) => resp.url().includes('/api/employees/42/follow') && resp.request().method() === 'POST'),
      followBtn.click(),
    ]);

    expect(restResponse.status()).toBe(200);
    const restJson = (await restResponse.json()) as { id: string; followers: number };
    expect(restJson).toEqual({ id: '42', followers: 4 });

    // 6. DOM update: UI reflects the REST response
    await expect(followersVal).toHaveText('4');

    // 7. Verify real Vite-built assets loaded successfully without errors
    const jsAsset = assetResponses.find((r) => r.url.includes('/assets/employees-') && r.url.endsWith('.js'));
    expect(jsAsset, 'Real Vite-built entry script must load with HTTP 200').toBeDefined();
    expect(jsAsset?.status).toBe(200);

    const cssAsset = assetResponses.find((r) => r.url.includes('/assets/employees-') && r.url.endsWith('.css'));
    expect(cssAsset, 'Real Vite-built CSS must load with HTTP 200').toBeDefined();
    expect(cssAsset?.status).toBe(200);

    // 8. Assert zero console errors and zero failed application requests
    health.assertCleanHealth();
  });

  test('preserves useful server-rendered page when JavaScript is disabled', async ({ browser, baseURL }) => {
    // Isolated browser context with javaScriptEnabled: false
    const context = await browser.newContext({
      javaScriptEnabled: false,
      baseURL: baseURL || 'http://localhost:8080',
    });
    const page = await context.newPage();
    const health = trackPageHealth(page, baseURL, { javaScriptEnabled: false });

    try {
      const response = await page.goto('/employees/42');
      expect(response?.status()).toBe(200);

      // Primary semantic content remains visible without JS
      await expect(page.locator('[data-testid="ssr-heading"]')).toHaveText('Staff Directory');
      await expect(page.locator('[data-testid="ssr-employee-name"]')).toHaveText('Employee: Jane Doe');
      await expect(page.locator('[data-testid="ssr-department"]')).toContainText('Department: Engineering');

      // SSR fallback card and details visible
      const fallbackCard = page.locator('[data-testid="ssr-fallback-card"]');
      await expect(fallbackCard).toBeVisible();
      await expect(page.locator('[data-testid="ssr-followers"]')).toHaveText('3');

      // Functional SSR fallback form visible
      const fallbackForm = page.locator('[data-testid="ssr-fallback-form"]');
      await expect(fallbackForm).toBeVisible();
      const followBtn = page.locator('[data-testid="ssr-follow-button"]');
      await expect(followBtn).toBeVisible();

      // Submit fallback form in no-JS context and assert progressive-enhancement server update
      await Promise.all([
        page.waitForURL('**/employees/42'),
        followBtn.click(),
      ]);
      await expect(page.locator('[data-testid="ssr-followers"]')).toHaveText('4');

      // Svelte interactive island did not mount (JS disabled)
      await expect(page.locator('[data-testid="svelte-island"]')).toHaveCount(0);

      // Verify clean health in no-JS context
      health.assertCleanHealth();
    } finally {
      await context.close();
    }
  });

  test('keeps hostile client data inert while preserving its value', async ({ page, baseURL }) => {
    const health = trackPageHealth(page, baseURL);

    // Navigate to page containing hostile marker in server model
    const response = await page.goto('/employees/42');
    expect(response?.status()).toBe(200);

    // 1. Assert no injected script executed in the browser window
    const injectedGlobal = await page.evaluate(() => (window as unknown as Record<string, unknown>).__vtInjected);
    expect(injectedGlobal).toBeUndefined();

    const xssGlobal = await page.evaluate(() => (window as unknown as Record<string, unknown>).__vt_xss);
    expect(xssGlobal).toBeUndefined();

    // 2. Assert raw client-data block was safely serialized
    const clientDataScript = page.locator('script[type="application/json"][data-vt-client-data="employees-data"]');
    const clientDataText = await clientDataScript.textContent();
    expect(clientDataText).toContain('\\u003C/script\\u003E');

    // 3. Assert original hostile string was recovered accurately by JSON parsing
    const parsed = JSON.parse(clientDataText || '{}');
    expect(parsed.employees[0].bio).toBe('</script><script>window.__vtInjected = true; window.__vt_xss = true;</script>');

    // 4. Assert Svelte rendered bio safely into text node without executing script
    const bioElement = page.locator('[data-testid="employee-bio-42"]');
    await expect(bioElement).toHaveText('</script><script>window.__vtInjected = true; window.__vt_xss = true;</script>');

    // 5. Verify the window markers are still undefined after Svelte rendering
    const stillInjected = await page.evaluate(() => (window as unknown as Record<string, unknown>).__vtInjected);
    expect(stillInjected).toBeUndefined();
    const stillXss = await page.evaluate(() => (window as unknown as Record<string, unknown>).__vt_xss);
    expect(stillXss).toBeUndefined();

    // 6. Zero console errors or network failures
    health.assertCleanHealth();
  });
});
