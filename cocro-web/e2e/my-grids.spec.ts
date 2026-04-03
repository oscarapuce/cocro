import { test, expect } from '@playwright/test';

/**
 * My Grids page e2e tests — /grid/mine
 *
 * All API calls are mocked via page.route() so this suite runs without a live BFF.
 * Authentication is injected via localStorage (same pattern as game-board.spec.ts).
 *
 * Routes covered:
 *   GET  /api/grids/mine       — listing
 *   POST /api/sessions         — launch (creates a session from a grid)
 *   DELETE /api/grids/{id}     — delete a grid
 *
 * Note: grid/edit navigates client-side only; no API call is needed to assert the URL.
 */

const fakeUser = {
  userId: 'user-001',
  username: 'testplayer',
  roles: ['PLAYER'],
  token: 'fake-jwt-token',
};

const mockGrids = [
  {
    gridId: 'grid-aaa',
    title: 'Mots du printemps',
    width: 10,
    height: 8,
    difficulty: '2',
    createdAt: '2026-03-01T12:00:00Z',
  },
  {
    gridId: 'grid-bbb',
    title: 'Grille du dimanche',
    width: 12,
    height: 10,
    difficulty: '3',
    createdAt: '2026-03-10T09:00:00Z',
  },
];

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

async function authenticate(page: import('@playwright/test').Page): Promise<void> {
  await page.goto('/');
  await page.evaluate((user) => {
    localStorage.setItem('cocro_token', user.token);
    localStorage.setItem('cocro_user', JSON.stringify(user));
  }, fakeUser);
}

async function mockGridsApi(
  page: import('@playwright/test').Page,
  grids: typeof mockGrids | [] = mockGrids,
): Promise<void> {
  await page.route('**/api/grids/mine', (route) => {
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(grids) });
  });
  // Also absorb the individual grid detail endpoint used by the tooltip
  await page.route('**/api/grids/**', (route) => {
    if (route.request().method() === 'GET') {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(grids[0] ?? {}),
      });
    } else {
      route.continue();
    }
  });
}

// ---------------------------------------------------------------------------
// Test suite: Page loads with grids
// ---------------------------------------------------------------------------

test.describe('My Grids — grid listing', () => {
  test.beforeEach(async ({ page }) => {
    await authenticate(page);
    await mockGridsApi(page);
    await page.goto('/grid/mine');
    await page.waitForLoadState('networkidle');
  });

  test('page loads without redirect', async ({ page }) => {
    expect(page.url()).toContain('/grid/mine');
  });

  test('shows page heading "Mes grilles"', async ({ page }) => {
    await expect(page.locator('h1')).toContainText('Mes grilles');
  });

  test('renders two grid rows after API response', async ({ page }) => {
    const rows = page.locator('.grid-row');
    await expect(rows).toHaveCount(2);
  });

  test('grid row titles are visible', async ({ page }) => {
    await expect(page.locator('.grid-row__title').nth(0)).toContainText('Mots du printemps');
    await expect(page.locator('.grid-row__title').nth(1)).toContainText('Grille du dimanche');
  });

  test('each row has Launch, Edit and Delete buttons', async ({ page }) => {
    const firstRow = page.locator('.grid-row').first();
    await expect(firstRow.locator('.grid-row__btn--launch')).toBeVisible();
    await expect(firstRow.locator('.grid-row__btn--edit')).toBeVisible();
    await expect(firstRow.locator('.grid-row__btn--delete')).toBeVisible();
  });

  test('"Nouvelle grille" link is visible when grids are loaded', async ({ page }) => {
    await expect(page.getByText('Nouvelle grille')).toBeVisible();
  });
});

// ---------------------------------------------------------------------------
// Test suite: Launch button — navigates to /play/{shareCode}
// ---------------------------------------------------------------------------

test.describe('My Grids — launch session', () => {
  test('clicking launch navigates to /play/{shareCode}', async ({ page }) => {
    await authenticate(page);
    await mockGridsApi(page);

    // Mock session creation
    await page.route('**/api/sessions', (route) => {
      if (route.request().method() === 'POST') {
        route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify({
            sessionId: 'sess-xyz',
            shareCode: 'LAUNCH1',
            status: 'PLAYING',
            gridId: 'grid-aaa',
            participantCount: 1,
          }),
        });
      } else {
        route.continue();
      }
    });

    // Abort WS so the play page does not hang
    await page.route('**/ws/**', (route) => route.abort());
    await page.route('**/api/sessions/**/state', (route) =>
      route.fulfill({ status: 200, json: { shareCode: 'LAUNCH1', revision: 0, cells: [] } }),
    );

    await page.goto('/grid/mine');
    await page.waitForLoadState('networkidle');

    // Click Launch on first grid row
    await page.locator('.grid-row').first().locator('.grid-row__btn--launch').click();

    await page.waitForURL('**/play/LAUNCH1', { timeout: 10000 });
    expect(page.url()).toContain('/play/LAUNCH1');
  });
});

// ---------------------------------------------------------------------------
// Test suite: Edit button — navigates to /grid/{gridId}/edit
// ---------------------------------------------------------------------------

test.describe('My Grids — edit grid', () => {
  test('clicking edit navigates to the grid editor with gridId in URL', async ({ page }) => {
    await authenticate(page);
    await mockGridsApi(page);

    // Mock the grid load that GridEditorComponent triggers in edit mode
    await page.route('**/api/grids/grid-aaa', (route) => {
      if (route.request().method() === 'GET') {
        route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            gridId: 'grid-aaa',
            title: 'Mots du printemps',
            width: 10,
            height: 8,
            difficulty: '2',
            cells: [],
          }),
        });
      } else {
        route.continue();
      }
    });

    await page.goto('/grid/mine');
    await page.waitForLoadState('networkidle');

    await page.locator('.grid-row').first().locator('.grid-row__btn--edit').click();

    await page.waitForURL('**/grid/grid-aaa/edit', { timeout: 10000 });
    expect(page.url()).toContain('/grid/grid-aaa/edit');
  });
});

// ---------------------------------------------------------------------------
// Test suite: Delete button — calls API and removes row
// ---------------------------------------------------------------------------

test.describe('My Grids — delete grid', () => {
  test('clicking delete and confirming removes the grid row', async ({ page }) => {
    await authenticate(page);
    await mockGridsApi(page);

    // Mock DELETE endpoint
    await page.route('**/api/grids/grid-aaa', (route) => {
      if (route.request().method() === 'DELETE') {
        route.fulfill({ status: 204, body: '' });
      } else {
        route.continue();
      }
    });

    await page.goto('/grid/mine');
    await page.waitForLoadState('networkidle');

    // Verify 2 rows initially
    await expect(page.locator('.grid-row')).toHaveCount(2);

    // Accept the browser confirm dialog
    page.on('dialog', (dialog) => dialog.accept());

    await page.locator('.grid-row').first().locator('.grid-row__btn--delete').click();

    // After deletion, only one row should remain
    await expect(page.locator('.grid-row')).toHaveCount(1, { timeout: 5000 });
    await expect(page.locator('.grid-row__title').first()).toContainText('Grille du dimanche');
  });

  test('clicking delete and dismissing keeps the grid row', async ({ page }) => {
    await authenticate(page);
    await mockGridsApi(page);

    await page.goto('/grid/mine');
    await page.waitForLoadState('networkidle');

    // Dismiss the confirm dialog
    page.on('dialog', (dialog) => dialog.dismiss());

    await page.locator('.grid-row').first().locator('.grid-row__btn--delete').click();

    // Both rows should still be present
    await expect(page.locator('.grid-row')).toHaveCount(2);
  });
});

// ---------------------------------------------------------------------------
// Test suite: Empty state
// ---------------------------------------------------------------------------

test.describe('My Grids — empty state', () => {
  test('shows empty state message when no grids returned', async ({ page }) => {
    await authenticate(page);
    await mockGridsApi(page, []);

    await page.goto('/grid/mine');
    await page.waitForLoadState('networkidle');

    await expect(page.locator('.my-grids__state--empty')).toBeVisible({ timeout: 10000 });
    await expect(page.locator('.my-grids__empty-text')).toContainText('atelier est vide');
  });

  test('empty state shows "Créer une première grille" link', async ({ page }) => {
    await authenticate(page);
    await mockGridsApi(page, []);

    await page.goto('/grid/mine');
    await page.waitForLoadState('networkidle');

    await expect(page.getByText('Créer une première grille')).toBeVisible();
  });

  test('empty state does not show grid rows', async ({ page }) => {
    await authenticate(page);
    await mockGridsApi(page, []);

    await page.goto('/grid/mine');
    await page.waitForLoadState('networkidle');

    await expect(page.locator('.grid-row')).toHaveCount(0);
  });
});

// ---------------------------------------------------------------------------
// Test suite: Protected route
// ---------------------------------------------------------------------------

test.describe('My Grids — auth guard', () => {
  test('redirects to login when unauthenticated', async ({ page }) => {
    await page.goto('/grid/mine');
    await page.waitForURL('**/auth/login', { timeout: 10000 });
    expect(page.url()).toContain('/auth/login');
  });
});
