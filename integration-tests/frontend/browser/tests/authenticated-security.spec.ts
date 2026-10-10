import { test, expect, type Page, type BrowserContext } from '@playwright/test';

function trackPageHealth(
  page: Page,
  baseURL?: string,
  options?: {
    javaScriptEnabled?: boolean;
    isIgnoredResponse?: (url: string, status: number) => boolean;
    isIgnoredConsoleError?: (text: string) => boolean;
  }
) {
  const consoleErrors: string[] = [];
  const failedRequests: string[] = [];
  const jsEnabled = options?.javaScriptEnabled ?? true;
  const isIgnored = options?.isIgnoredResponse ?? (() => false);
  const isIgnoredConsole = options?.isIgnoredConsoleError ?? (() => false);

  page.on('console', (msg) => {
    if (msg.type() === 'error') {
      const text = msg.text();
      if (isIgnoredConsole(text)) {
        return;
      }
      consoleErrors.push(text);
    }
  });

  page.on('pageerror', (err) => {
    consoleErrors.push(err.message);
  });

  page.on('response', (resp) => {
    const url = resp.url();
    const status = resp.status();
    const base = baseURL || 'http://127.0.0.1';
    if (status >= 400 && url.startsWith(base)) {
      if (!isIgnored(url, status)) {
        failedRequests.push(`${url} returned ${status}`);
      }
    }
  });

  page.on('requestfailed', (req) => {
    const base = baseURL || 'http://127.0.0.1';
    if (req.url().startsWith(base)) {
      const errorText = req.failure()?.errorText || 'unknown';
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

async function loginUser(page: Page, username: string, password: string) {
  await page.goto('/login');
  await expect(page.locator('[data-testid="login-form"]')).toBeVisible();
  await page.fill('[data-testid="login-username"]', username);
  await page.fill('[data-testid="login-password"]', password);
  await Promise.all([
    page.waitForURL((url) => !url.pathname.endsWith('/login')),
    page.click('[data-testid="login-submit"]'),
  ]);
}

test.describe('Viet Template Authenticated Security & CSRF Browser E2E Qualification', () => {

  test.beforeEach(async ({ request, baseURL }) => {
    const res = await request.post(`${baseURL || ''}/api/test/reset`);
    expect(res.ok()).toBeTruthy();
  });

  test('anonymous user accessing protected route is redirected to login', async ({ page, baseURL }) => {
    const response = await page.goto('/secure/employees/42');
    expect(response).not.toBeNull();
    // After redirects, page should be on /login
    expect(page.url()).toContain('/login');
    await expect(page.locator('[data-testid="login-form"]')).toBeVisible();
    await expect(page.locator('[data-testid="login-username"]')).toBeVisible();
    await expect(page.locator('[data-testid="login-password"]')).toBeVisible();

    // Anonymous mutation attempt is rejected according to framework policy (redirect 302, 401, or 403)
    const anonMutationResp = await page.request.post(`${baseURL || ''}/secure/api/employees/42/follow`, {
      headers: { Accept: 'application/json' },
      maxRedirects: 0,
    });
    expect([302, 400, 401, 403], 'Anonymous mutation must redirect or return 400/401/403').toContain(
      anonMutationResp.status()
    );

    // Logging in confirms that server state was preserved (counter remains 3)
    await loginUser(page, 'alice', 'secret');
    await page.goto('/secure/employees/42');
    await expect(page.locator('[data-testid="followers-val-42"]')).toHaveText('3');
  });

  test('form login establishes session, SSR renders security & CSRF metadata, Svelte island mounts and follows with CSRF', async ({ page, baseURL }) => {
    const health = trackPageHealth(page, baseURL);

    // 1. Authenticate as alice (USER role)
    await loginUser(page, 'alice', 'secret');
    await expect(page).toHaveURL(/\/secure\/employees\/42/);

    // 2. Assert SSR heading and semantic content
    await expect(page.locator('[data-testid="ssr-heading"]')).toHaveText('Staff Directory');
    await expect(page.locator('[data-testid="ssr-employee-name"]')).toHaveText('Employee: Jane Doe');
    await expect(page.locator('[data-testid="ssr-department"]')).toContainText('Department: Engineering');

    // 3. Assert $security facade rendered principal name and role-dependent markup
    const securityUser = page.locator('[data-testid="security-user"]');
    await expect(securityUser).toHaveText('alice');
    // alice is not ADMIN -> admin link must NOT be rendered
    await expect(page.locator('[data-testid="admin-link"]')).toHaveCount(0);

    // 4. Assert $csrf metadata rendered in DOM
    const csrfTokenMeta = page.locator('meta[data-vt-csrf-token]');
    await expect(csrfTokenMeta).toHaveCount(1);
    const tokenVal = await csrfTokenMeta.getAttribute('content');
    expect(tokenVal).toBeTruthy();

    const csrfHeaderMeta = page.locator('meta[data-vt-csrf-header]');
    await expect(csrfHeaderMeta).toHaveCount(1);
    const headerVal = await csrfHeaderMeta.getAttribute('content');
    expect(headerVal).toBeTruthy();

    // 5. Assert Svelte 5 interactive island mounted with production Vite assets
    const svelteIsland = page.locator('[data-testid="svelte-island"]');
    await expect(svelteIsland).toBeVisible();
    await expect(svelteIsland).toHaveAttribute('data-mounted', 'true');
    await expect(page.locator('#employees-island')).toHaveAttribute('data-mounted', 'true');

    // Initial followers count
    const followersVal = page.locator('[data-testid="followers-val-42"]');
    await expect(followersVal).toHaveText('3');

    // 6. REST interaction: click follow button, island reads CSRF and sends POST with CSRF header
    const followBtn = page.locator('[data-testid="follow-btn-42"]');
    await expect(followBtn).toBeVisible();

    const [restResponse] = await Promise.all([
      page.waitForResponse(
        (resp) =>
          resp.url().includes('/secure/api/employees/42/follow') &&
          resp.request().method() === 'POST'
      ),
      followBtn.click(),
    ]);

    expect(restResponse.status()).toBe(200);
    const restJson = (await restResponse.json()) as { id: string; followers: number };
    expect(restJson).toEqual({ id: '42', followers: 4 });

    // 7. DOM update: reactive UI reflects incremented counter
    await expect(followersVal).toHaveText('4');

    // Clean browser health
    health.assertCleanHealth();
  });

  test('role-based authorization: admin renders admin-link and accesses admin route, alice is forbidden', async ({ browser, baseURL }) => {
    // Context 1: Alice (USER only)
    const aliceContext = await browser.newContext({ baseURL });
    const alicePage = await aliceContext.newPage();
    const aliceHealth = trackPageHealth(alicePage, baseURL, {
      isIgnoredResponse: (url, status) => url.includes('/secure/admin') && status === 403,
      isIgnoredConsoleError: (text) => text.includes('403'),
    });

    try {
      await loginUser(alicePage, 'alice', 'secret');
      await expect(alicePage.locator('[data-testid="security-user"]')).toHaveText('alice');
      await expect(alicePage.locator('[data-testid="admin-link"]')).toHaveCount(0);

      // Direct access to /secure/admin as alice is forbidden (HTTP 403)
      const aliceAdminResp = await alicePage.goto('/secure/admin');
      expect(aliceAdminResp?.status()).toBe(403);
      aliceHealth.assertCleanHealth();
    } finally {
      await aliceContext.close();
    }

    // Context 2: Admin (ADMIN + USER roles)
    const adminContext = await browser.newContext({ baseURL });
    const adminPage = await adminContext.newPage();
    const adminHealth = trackPageHealth(adminPage, baseURL);

    try {
      await loginUser(adminPage, 'admin', 'admin-secret');
      await expect(adminPage.locator('[data-testid="security-user"]')).toHaveText('admin');
      // Admin link is rendered
      const adminLink = adminPage.locator('[data-testid="admin-link"]');
      await expect(adminLink).toBeVisible();
      await expect(adminLink).toHaveText('Admin Dashboard');

      // Direct access or link click to /secure/admin succeeds (HTTP 200)
      const adminResp = await adminPage.goto('/secure/admin');
      expect(adminResp?.status()).toBe(200);
      const adminText = await adminPage.textContent('body');
      expect(adminText).toContain('ADMIN ACCESS GRANTED');
      adminHealth.assertCleanHealth();
    } finally {
      await adminContext.close();
    }
  });

  test('server enforces CSRF protection: missing or invalid CSRF is rejected and state preserved', async ({ page, baseURL }) => {
    // 1. Log in to establish authenticated session
    await loginUser(page, 'alice', 'secret');
    await expect(page.locator('[data-testid="followers-val-42"]')).toHaveText('3');

    // 2. Reject POST without CSRF header/param (Spring: 403, Quarkus: 400)
    const missingCsrfResp = await page.request.post(`${baseURL || ''}/secure/api/employees/42/follow`, {
      headers: { 'Accept': 'application/json' },
    });
    expect([400, 403], 'Missing CSRF must be rejected with 400 or 403').toContain(missingCsrfResp.status());

    // 3. Reject POST with invalid CSRF token (Spring: 403, Quarkus: 400)
    const invalidCsrfResp = await page.request.post(`${baseURL || ''}/secure/api/employees/42/follow`, {
      headers: {
        'Accept': 'application/json',
        'X-CSRF-TOKEN': 'invalid-csrf-token-secret-999',
      },
    });
    expect([400, 403], 'Invalid CSRF must be rejected with 400 or 403').toContain(invalidCsrfResp.status());

    // 4. Verify server state was preserved (counter remains 3)
    await page.reload();
    await expect(page.locator('[data-testid="followers-val-42"]')).toHaveText('3');
  });

  test('non-JavaScript fallback form with CSRF succeeds when JavaScript is disabled', async ({ browser, baseURL }) => {
    // Context with javaScriptEnabled: false
    const context = await browser.newContext({
      javaScriptEnabled: false,
      baseURL: baseURL || 'http://localhost:8080',
    });
    const page = await context.newPage();
    const health = trackPageHealth(page, baseURL, { javaScriptEnabled: false });

    try {
      // 1. Log in via server-rendered form without JS
      await loginUser(page, 'alice', 'secret');
      await expect(page.locator('[data-testid="ssr-heading"]')).toHaveText('Staff Directory');
      await expect(page.locator('[data-testid="security-user"]')).toHaveText('alice');

      // 2. Initial follower count in SSR fallback
      await expect(page.locator('[data-testid="ssr-followers"]')).toHaveText('3');

      // 3. SSR fallback form visible with CSRF token
      const fallbackForm = page.locator('[data-testid="ssr-fallback-form"]');
      await expect(fallbackForm).toBeVisible();
      const csrfInput = page.locator('[data-testid="ssr-csrf-token"]');
      await expect(csrfInput).toHaveCount(1);
      const csrfVal = await csrfInput.getAttribute('value');
      expect(csrfVal).toBeTruthy();

      // 4. Submit fallback form in no-JS context and assert server-side update and redirect
      const followBtn = page.locator('[data-testid="ssr-follow-button"]');
      await Promise.all([
        page.waitForURL('**/secure/employees/42'),
        followBtn.click(),
      ]);

      // 5. Follower count incremented to 4 in refreshed SSR document
      await expect(page.locator('[data-testid="ssr-followers"]')).toHaveText('4');

      // Interactive island was not mounted (no JS)
      await expect(page.locator('[data-testid="svelte-island"]')).toHaveCount(0);

      health.assertCleanHealth();
    } finally {
      await context.close();
    }
  });
});
