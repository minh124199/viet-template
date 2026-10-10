import { test, expect, type Page } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import { fileURLToPath } from 'url';
import { execSync } from 'child_process';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const REPO_ROOT = path.resolve(__dirname, '../../../../');
const SVELTE_PATH = path.join(
  REPO_ROOT,
  'examples/frontend-svelte-islands/src/pages/employees/Employees.svelte'
);
const CSS_PATH = path.join(
  REPO_ROOT,
  'examples/frontend-svelte-islands/src/pages/employees/employees.css'
);

function trackPageHealth(page: Page, baseURL?: string) {
  const consoleErrors: string[] = [];
  const failedRequests: string[] = [];

  page.on('console', (msg) => {
    if (msg.type() === 'error') {
      consoleErrors.push(msg.text());
    }
  });

  page.on('pageerror', (err) => {
    consoleErrors.push(err.message);
  });

  page.on('requestfailed', (req) => {
    const url = req.url();
    const errorText = req.failure()?.errorText || 'unknown';
    // Ignore cancelled requests due to page reload or fast tear-down
    if (errorText !== 'net::ERR_ABORTED') {
      failedRequests.push(`${url} failed: ${errorText}`);
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

async function pollUntil(
  condition: () => Promise<boolean>,
  timeoutMs = 15000,
  intervalMs = 300
): Promise<void> {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    try {
      if (await condition()) {
        return;
      }
    } catch {
      // Continue polling
    }
    await new Promise((r) => setTimeout(r, intervalMs));
  }
  throw new Error(`pollUntil timed out after ${timeoutMs}ms`);
}

test.describe('Viet Template Frontend Dev Mode & HMR Qualification', () => {
  const framework = process.env.FRAMEWORK || 'spring';
  const viteDevUrl = (process.env.VITE_DEV_URL || 'http://127.0.0.1:5173').replace(/\/+$/, '');

  const vtlPath =
    framework === 'quarkus'
      ? path.join(
          REPO_ROOT,
          'integration-tests/quarkus/frontend-dev-mode-e2e/src/main/resources/templates/employees.vtl'
        )
      : path.join(
          REPO_ROOT,
          'integration-tests/spring/frontend-dev-mode-e2e/src/main/viet-template/employees.vtl'
        );

  const javaPath =
    framework === 'quarkus'
      ? path.join(
          REPO_ROOT,
          'integration-tests/quarkus/frontend-dev-mode-e2e/src/main/java/io/github/minh124199/test/frontend/dev/EmployeeResource.java'
        )
      : path.join(
          REPO_ROOT,
          'integration-tests/spring/frontend-dev-mode-e2e/src/main/java/io/github/minh124199/test/frontend/dev/EmployeeController.java'
        );

  const springFixturePom = path.join(
    REPO_ROOT,
    'integration-tests/spring/frontend-dev-mode-e2e/pom.xml'
  );
  const springTriggerFile = path.join(
    REPO_ROOT,
    'integration-tests/spring/frontend-dev-mode-e2e/target/classes/.restart-trigger'
  );

  let originalSvelte = '';
  let originalCss = '';
  let originalVtl = '';
  let originalJava = '';

  test.beforeEach(async ({ request, baseURL }) => {
    // Read and save original contents for 100% byte-for-byte restoration guarantee
    originalSvelte = fs.readFileSync(SVELTE_PATH, 'utf-8');
    originalCss = fs.readFileSync(CSS_PATH, 'utf-8');
    originalVtl = fs.readFileSync(vtlPath, 'utf-8');
    originalJava = fs.readFileSync(javaPath, 'utf-8');

    // Reset REST state
    const res = await request.post(`${baseURL || ''}/api/test/reset`);
    expect(res.ok()).toBeTruthy();
  });

  test.afterEach(async ({ request, baseURL }) => {
    // Byte-for-byte source restoration
    if (originalSvelte) fs.writeFileSync(SVELTE_PATH, originalSvelte, 'utf-8');
    if (originalCss) fs.writeFileSync(CSS_PATH, originalCss, 'utf-8');
    if (originalVtl) fs.writeFileSync(vtlPath, originalVtl, 'utf-8');
    if (originalJava) {
      fs.writeFileSync(javaPath, originalJava, 'utf-8');
      if (framework === 'spring') {
        try {
          const mvnw = path.join(REPO_ROOT, 'mvnw');
          execSync(`${mvnw} compile -f "${springFixturePom}" -B -q`, { cwd: REPO_ROOT });
          fs.writeFileSync(springTriggerFile, `${Date.now()}\n`, 'utf-8');
        } catch {
          // Best effort restoration
        }
      } else {
        try {
          await request.get(`${baseURL || ''}/api/version`);
        } catch {
          // Best effort
        }
      }
    }
  });

  test('executes end-to-end dev lifecycle: SSR -> Svelte HMR -> CSS HMR -> VTL reload -> Java restart -> Post-restart HMR', async ({
    page,
    request,
    baseURL,
  }) => {
    test.setTimeout(90_000);
    const health = trackPageHealth(page, baseURL);

    // Track frame navigations on the main frame
    let mainFrameNavigations = 0;
    page.on('framenavigated', (frame) => {
      if (frame === page.mainFrame()) {
        mainFrameNavigations++;
      }
    });

    // =========================================================================
    // STEP 1: Initial SSR + Svelte Island Mounting
    // =========================================================================
    const initialResponse = await page.goto('/employees/42');
    expect(initialResponse?.status()).toBe(200);
    expect(mainFrameNavigations).toBe(1);

    // Assert SSR DOM
    await expect(page.locator('[data-testid="ssr-heading"]')).toHaveText('Staff Directory');
    await expect(page.locator('[data-testid="ssr-employee-name"]')).toHaveText('Employee: Jane Doe');
    await expect(page.locator('[data-testid="ssr-department"]')).toContainText('Department: Engineering');
    await expect(page.locator('[data-testid="vtl-version"]')).toHaveText('VTL-A');
    await expect(page.locator('[data-testid="java-version"]')).toHaveText('JAVA-A');

    // Assert ClientData bridge script
    const clientDataScript = page.locator(
      'script[type="application/json"][data-vt-client-data="employees-data"]'
    );
    await expect(clientDataScript).toHaveCount(1);
    const clientDataJson = JSON.parse((await clientDataScript.textContent()) || '{}');
    expect(clientDataJson.departmentName).toBe('Engineering');
    expect(clientDataJson.employees[0].id).toBe('42');
    expect(clientDataJson.employees[0].followers).toBe(3);

    // Assert dev-server asset URLs emitted in head and body
    const viteClientScript = page.locator(`script[type="module"][src="${viteDevUrl}/@vite/client"]`);
    await expect(viteClientScript).toHaveCount(1);
    const viteEntryScript = page.locator(
      `script[type="module"][src="${viteDevUrl}/src/pages/employees/index.ts"]`
    );
    await expect(viteEntryScript).toHaveCount(1);

    // Assert Svelte 5 island mounted
    const svelteIsland = page.locator('[data-testid="svelte-island"]');
    await expect(svelteIsland).toBeVisible();
    await expect(svelteIsland).toHaveAttribute('data-mounted', 'true');
    const islandHeading = page.locator('[data-testid="island-heading"]');
    await expect(islandHeading).toHaveText('Engineering Department (1 members)');
    await expect(page.locator('[data-testid="followers-val-42"]')).toHaveText('3');

    // Assert initial CSS marker
    const initialMarker = await page.evaluate(() =>
      window.getComputedStyle(document.documentElement).getPropertyValue('--vt-e2e-marker').trim()
    );
    expect(initialMarker).toBe('1');

    // =========================================================================
    // STEP 2: Svelte Source HMR (Zero Main-Frame Navigation)
    // =========================================================================
    const svelteHmr1 = originalSvelte.replace(
      '{departmentName} Department ({employees.length} members)',
      '{departmentName} Department [HMR-1] ({employees.length} members)'
    );
    fs.writeFileSync(SVELTE_PATH, svelteHmr1, 'utf-8');

    // Wait for Svelte HMR update in DOM
    await expect(islandHeading).toHaveText('Engineering Department [HMR-1] (1 members)', {
      timeout: 10_000,
    });
    // Critical assertion: zero main-frame navigations during Svelte HMR
    expect(mainFrameNavigations, 'Svelte HMR must not cause a main-frame navigation').toBe(1);

    // =========================================================================
    // STEP 3: CSS HMR (Zero Main-Frame Navigation)
    // =========================================================================
    const cssHmr = originalCss.replace('--vt-e2e-marker: 1;', '--vt-e2e-marker: 2;');
    fs.writeFileSync(CSS_PATH, cssHmr, 'utf-8');

    // Wait for CSS variable change via computed style
    await pollUntil(async () => {
      const val = await page.evaluate(() =>
        window.getComputedStyle(document.documentElement).getPropertyValue('--vt-e2e-marker').trim()
      );
      return val === '2';
    }, 10_000);

    // Critical assertion: zero main-frame navigations during CSS HMR
    expect(mainFrameNavigations, 'CSS HMR must not cause a main-frame navigation').toBe(1);

    // =========================================================================
    // STEP 4: VTL Template Reload
    // =========================================================================
    const vtlModified = originalVtl.replace(
      '<span data-testid="vtl-version">VTL-A</span>',
      '<span data-testid="vtl-version">VTL-B</span>'
    );
    fs.writeFileSync(vtlPath, vtlModified, 'utf-8');

    // Poll backend via HTTP request until updated VTL template is rendered
    await pollUntil(async () => {
      try {
        const res = await request.get(`${baseURL || ''}/employees`);
        if (!res.ok()) return false;
        const text = await res.text();
        return text.includes('VTL-B');
      } catch {
        return false;
      }
    }, 15_000, 300);

    // Single browser reload to display reloaded template
    const vtlReloadResponse = await page.reload();
    expect(vtlReloadResponse?.status()).toBe(200);
    expect(mainFrameNavigations).toBe(2);

    // Assert reloaded VTL template content
    await expect(page.locator('[data-testid="vtl-version"]')).toHaveText('VTL-B');
    await expect(page.locator('[data-testid="java-version"]')).toHaveText('JAVA-A');
    // Island re-mounted cleanly
    await expect(page.locator('[data-testid="svelte-island"]')).toBeVisible();

    // =========================================================================
    // STEP 5: Java Source Reload / Restart
    // =========================================================================
    // Fetch initial generation metadata
    const initialMetaResp = await request.get(`${baseURL || ''}/__test/restart-generation`);
    const initialMeta = await initialMetaResp.json();
    const initialClId = initialMeta.classLoaderId;

    // Mutate Java source to return JAVA-B
    const javaModified = originalJava.replace('"JAVA-A"', '"JAVA-B"');
    fs.writeFileSync(javaPath, javaModified, 'utf-8');

    if (framework === 'spring') {
      const mvnw = path.join(REPO_ROOT, 'mvnw');
      execSync(`${mvnw} compile -f "${springFixturePom}" -B -q`, { cwd: REPO_ROOT });
      fs.writeFileSync(springTriggerFile, `${Date.now()}\n`, 'utf-8');

      // Poll until DevTools RestartClassLoader turnover completes
      await pollUntil(async () => {
        try {
          const res = await request.get(`${baseURL || ''}/__test/restart-generation`);
          if (!res.ok()) return false;
          const meta = await res.json();
          return meta.classLoaderId !== initialClId && meta.javaVersion === 'JAVA-B';
        } catch {
          return false;
        }
      }, 20_000);
    } else {
      // Quarkus live-reload triggers upon next HTTP request
      await pollUntil(async () => {
        try {
          const res = await request.get(`${baseURL || ''}/__test/restart-generation`);
          if (!res.ok()) return false;
          const meta = await res.json();
          return meta.classLoaderId !== initialClId && meta.javaVersion === 'JAVA-B';
        } catch {
          return false;
        }
      }, 20_000);
    }

    // Reload page to observe Java changes
    const javaReloadResponse = await page.reload();
    expect(javaReloadResponse?.status()).toBe(200);
    expect(mainFrameNavigations).toBe(3);

    // Assert Java updated and VTL updated
    await expect(page.locator('[data-testid="java-version"]')).toHaveText('JAVA-B');
    await expect(page.locator('[data-testid="vtl-version"]')).toHaveText('VTL-B');

    // =========================================================================
    // STEP 6: Vite Process Survives Java Reload
    // =========================================================================
    const viteClientResp = await request.get(`${viteDevUrl}/@vite/client`);
    expect(viteClientResp.status()).toBe(200);
    const viteEntryResp = await request.get(`${viteDevUrl}/src/pages/employees/index.ts`);
    expect(viteEntryResp.status()).toBe(200);

    // =========================================================================
    // STEP 7: Post-Reload Svelte HMR
    // =========================================================================
    const navBeforeHmr2 = mainFrameNavigations;
    const svelteHmr2 = originalSvelte.replace(
      '{departmentName} Department ({employees.length} members)',
      '{departmentName} Department [HMR-2] ({employees.length} members)'
    );
    fs.writeFileSync(SVELTE_PATH, svelteHmr2, 'utf-8');

    // Wait for Svelte HMR update in DOM
    await expect(page.locator('[data-testid="island-heading"]')).toHaveText(
      'Engineering Department [HMR-2] (1 members)',
      { timeout: 10_000 }
    );
    // Critical assertion: zero main-frame navigations during post-reload Svelte HMR
    expect(mainFrameNavigations, 'Post-restart Svelte HMR must not cause a navigation').toBe(
      navBeforeHmr2
    );

    // =========================================================================
    // STEP 8: ClientData Freshness & REST Interaction
    // =========================================================================
    // Click Follow button
    const followBtn = page.locator('[data-testid="follow-btn-42"]');
    await expect(followBtn).toBeVisible();
    await followBtn.click();
    await expect(page.locator('[data-testid="followers-val-42"]')).toHaveText('4');

    // Assert zero console errors and zero unexpected network failures
    health.assertCleanHealth();
  });
});
