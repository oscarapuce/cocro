import { test, expect } from '@playwright/test';

/**
 * Game Board e2e tests — Session Game Board enrichi
 *
 * Architecture note:
 * The GameBoardComponent connects to the BFF via two channels:
 *   1. STOMP/WebSocket — receives SessionWelcome, then subscribes to the broadcast topic.
 *      The welcome event triggers the first resync (GET /api/sessions/{shareCode}/state).
 *   2. REST — GET /api/sessions/{shareCode}/state on resync.
 *
 * The BFF endpoint GET /api/sessions/{shareCode}/grid-template exists and is covered by
 * unit tests. The Angular adapter (SessionGridTemplateHttpAdapter) and mapper
 * (mapGridTemplateToGrid) are also in place, but GameBoardComponent currently does not
 * call grid-template — it renders a simple flat grid from the session state only.
 *
 * WebSocket mocking strategy:
 * Playwright 1.40+ provides page.routeWebSocket(), but the STOMP/SockJS handshake (HTTP
 * upgrade + SockJS transport negotiation) makes full STOMP mocking fragile in e2e tests.
 * Tests that require an active WebSocket with real STOMP events are marked test.skip()
 * with the label "Requires running BFF + integration environment".
 *
 * For tests that do not need a live WS (routing, auth guards, static layout checks),
 * we rely on the fact that the component renders the .game container synchronously
 * before the WebSocket handshake completes, and only the .game__grid section requires
 * the WS-triggered data load.
 */

const SHARE_CODE = 'AB12';
const GAME_URL = `/game/${SHARE_CODE}`;

const fakeUser = {
  userId: 'test-player-id',
  username: 'testplayer',
  roles: ['PLAYER'],
  token: 'fake-jwt-token',
};

// Mock for GET /api/sessions/AB12/state (called after SessionWelcome via resync)
const mockSessionState = {
  sessionId: 'sess-123',
  shareCode: SHARE_CODE,
  revision: 3,
  cells: [
    { x: 1, y: 0, letter: 'A' },
    { x: 2, y: 1, letter: 'B' },
  ],
};

// Mock for GET /api/sessions/AB12/grid-template
// (endpoint exists in BFF; Angular adapter in place but not yet called by GameBoardComponent)
const mockGridTemplate = {
  title: 'Grille du dimanche',
  width: 3,
  height: 3,
  difficulty: '2',
  author: 'user-123',
  reference: 'GDS-2026',
  description: 'Une grille thématique',
  globalClueLabel: 'Animal caché',
  globalClueWordLengths: [3, 5],
  cells: [
    { x: 0, y: 0, type: 'CLUE_SINGLE', separator: null, number: null, clues: [{ direction: 'RIGHT', text: 'Fruit tropical' }] },
    { x: 1, y: 0, type: 'LETTER', separator: 'NONE', number: 1, clues: null },
    { x: 2, y: 0, type: 'LETTER', separator: 'LEFT', number: null, clues: null },
    { x: 0, y: 1, type: 'LETTER', separator: 'NONE', number: null, clues: null },
    { x: 1, y: 1, type: 'BLACK', separator: null, number: null, clues: null },
    { x: 2, y: 1, type: 'LETTER', separator: 'NONE', number: null, clues: null },
    { x: 0, y: 2, type: 'CLUE_SINGLE', separator: null, number: null, clues: [{ direction: 'FROM_SIDE', text: 'Oiseau noir' }] },
    { x: 1, y: 2, type: 'LETTER', separator: 'NONE', number: null, clues: null },
    { x: 2, y: 2, type: 'LETTER', separator: 'NONE', number: null, clues: null },
  ],
};

// ---------------------------------------------------------------------------
// Helper: authenticate via localStorage
// ---------------------------------------------------------------------------
async function authenticate(page: import('@playwright/test').Page): Promise<void> {
  await page.goto('/');
  await page.evaluate((user) => {
    localStorage.setItem('cocro_token', user.token);
    localStorage.setItem('cocro_user', JSON.stringify(user));
  }, fakeUser);
}

// ---------------------------------------------------------------------------
// Helper: set up REST route mocks (WS is NOT mocked — see strategy note above)
// ---------------------------------------------------------------------------
async function setupRouteMocks(page: import('@playwright/test').Page): Promise<void> {
  await page.route('**/api/sessions/AB12/state', async (route) => {
    await route.fulfill({ json: mockSessionState });
  });
  await page.route('**/api/sessions/AB12/grid-template', async (route) => {
    await route.fulfill({ json: mockGridTemplate });
  });
  // Absorb the WebSocket/SockJS HTTP-upgrade and info requests to prevent test noise.
  // The component will not receive STOMP events in this mock setup, so the grid
  // remains in its initial state (empty cells, connected = false).
  await page.route('**/ws/**', async (route) => {
    await route.abort();
  });
}

// ---------------------------------------------------------------------------
// Guard & routing tests (no WS required)
// ---------------------------------------------------------------------------

test.describe('Game Board — routing and auth guard', () => {
  test('redirects to login when unauthenticated', async ({ page }) => {
    await page.goto(GAME_URL);
    await page.waitForURL('**/auth/login');
    expect(page.url()).toContain('/auth/login');
  });

  test('stays on game board URL when authenticated', async ({ page }) => {
    await authenticate(page);
    await setupRouteMocks(page);
    await page.goto(GAME_URL);
    await page.waitForLoadState('domcontentloaded');
    expect(page.url()).toContain(GAME_URL);
  });
});

// ---------------------------------------------------------------------------
// Static layout tests (navbar, brand, code — visible without WS data)
// ---------------------------------------------------------------------------

test.describe('Game Board — static layout', () => {
  test.beforeEach(async ({ page }) => {
    await authenticate(page);
    await setupRouteMocks(page);
    await page.goto(GAME_URL);
    await page.waitForLoadState('domcontentloaded');
  });

  test('renders the .game container', async ({ page }) => {
    await expect(page.locator('.game')).toBeVisible({ timeout: 10000 });
  });

  test('navbar displays the CoCro brand', async ({ page }) => {
    await expect(page.locator('.game__brand')).toContainText('CoCro');
  });

  test('navbar displays the session share code', async ({ page }) => {
    await expect(page.locator('.game__code')).toContainText(SHARE_CODE);
  });

  test('navbar shows "hors ligne" indicator when WebSocket is not connected', async ({ page }) => {
    // WS requests are aborted in setupRouteMocks, so connected() stays false
    await expect(page.locator('.game__offline')).toBeVisible({ timeout: 5000 });
    await expect(page.locator('.game__offline')).toContainText('hors ligne');
  });

  test('Quitter button is visible in the navbar', async ({ page }) => {
    // The button renders via <cocro-button variant="ghost">Quitter</cocro-button>
    await expect(page.locator('cocro-button').filter({ hasText: 'Quitter' })).toBeVisible();
  });

  test('renders the .game__board area', async ({ page }) => {
    await expect(page.locator('.game__board')).toBeVisible({ timeout: 10000 });
  });

  test('does not show any editor panels (cell-type-toggle, clue-editor, letter-editor)', async ({ page }) => {
    // The game board must not render grid-editor-specific panels
    await expect(page.locator('.cell-type-toggle')).toHaveCount(0);
    await expect(page.locator('cocro-clue-editor')).toHaveCount(0);
    await expect(page.locator('cocro-letter-editor')).toHaveCount(0);
  });

  test('revision counter is visible in the navbar', async ({ page }) => {
    await expect(page.locator('.game__revision')).toBeVisible();
    // Initial revision is 0 before any WS event
    await expect(page.locator('.game__revision')).toContainText('rév.0');
  });
});

// ---------------------------------------------------------------------------
// Quitter navigation test
// ---------------------------------------------------------------------------

test.describe('Game Board — navigation', () => {
  test('clicking Quitter navigates to the home page', async ({ page }) => {
    await authenticate(page);
    await setupRouteMocks(page);

    // Also mock the POST /leave endpoint called in leave()
    await page.route('**/api/sessions/leave', async (route) => {
      await route.fulfill({ json: { sessionId: 'sess-123' } });
    });

    await page.goto(GAME_URL);
    await page.waitForLoadState('domcontentloaded');
    await expect(page.locator('cocro-button').filter({ hasText: 'Quitter' })).toBeVisible();

    await page.locator('cocro-button').filter({ hasText: 'Quitter' }).click();
    await page.waitForURL('**/');
    expect(page.url()).not.toContain(GAME_URL);
  });
});

// ---------------------------------------------------------------------------
// Grid rendering tests (require WS + BFF — skipped without integration env)
// ---------------------------------------------------------------------------

test.describe('Game Board — grid rendering (requires integration environment)', () => {
  /**
   * These tests require:
   *   - A running BFF (./gradlew cocro-bff:bootRun)
   *   - MongoDB + Redis started (bash scripts/compose-script.sh)
   *   - A seeded session with shareCode AB12 and a participant matching fakeUser
   *
   * They are skipped in CI/CD without the full integration environment.
   * Run them locally with: npx playwright test e2e/game-board.spec.ts --grep "requires integration"
   */

  test.skip(true, 'Requires running BFF + integration environment');

  test('renders the .game__grid after receiving SessionWelcome', async ({ page }) => {
    await authenticate(page);
    await page.goto(GAME_URL);
    await page.waitForLoadState('networkidle');

    await expect(page.locator('.game__grid')).toBeVisible({ timeout: 15000 });
  });

  test('renders game__row and game__cell elements after state load', async ({ page }) => {
    await authenticate(page);
    await page.goto(GAME_URL);
    await page.waitForLoadState('networkidle');

    await expect(page.locator('.game__row').first()).toBeVisible({ timeout: 15000 });
    const cells = page.locator('.game__cell');
    await expect(cells.first()).toBeVisible();
  });

  test('displays pre-loaded letters from session state', async ({ page }) => {
    // After resync, cells with letters should render .game__letter spans
    await authenticate(page);
    await page.goto(GAME_URL);
    await page.waitForLoadState('networkidle');

    await expect(page.locator('.game__letter').first()).toBeVisible({ timeout: 15000 });
  });

  test('participant count updates after SessionWelcome', async ({ page }) => {
    await authenticate(page);
    await page.goto(GAME_URL);
    await page.waitForLoadState('networkidle');

    await expect(page.locator('.section-label').filter({ hasText: 'joueur' })).toBeVisible({ timeout: 15000 });
  });

  test('revision number updates after state load', async ({ page }) => {
    await authenticate(page);
    await page.goto(GAME_URL);
    await page.waitForLoadState('networkidle');

    // Revision should be > 0 after receiving session state
    const revisionEl = page.locator('.game__revision');
    await expect(revisionEl).not.toContainText('rév.0', { timeout: 15000 });
  });
});

// ---------------------------------------------------------------------------
// Keyboard interaction tests (require WS + BFF — skipped without integration env)
// ---------------------------------------------------------------------------

test.describe('Game Board — keyboard interactions (requires integration environment)', () => {
  test.skip(true, 'Requires running BFF + integration environment');

  test('clicking a cell selects it (game__cell--selected class)', async ({ page }) => {
    await authenticate(page);
    await page.goto(GAME_URL);
    await page.waitForLoadState('networkidle');

    await page.locator('.game').click();
    const firstCell = page.locator('.game__cell').first();
    await firstCell.click();
    await expect(firstCell).toHaveClass(/game__cell--selected/);
  });

  test('typing a letter places it in the selected cell', async ({ page }) => {
    await authenticate(page);
    await page.goto(GAME_URL);
    await page.waitForLoadState('networkidle');

    await page.locator('.game').click();
    await page.locator('.game__cell').first().click();
    await page.keyboard.press('A');

    const firstCell = page.locator('.game__cell').first();
    await expect(firstCell.locator('.game__letter')).toContainText('A');
  });

  test('placed letter has game__cell--filled-me class', async ({ page }) => {
    await authenticate(page);
    await page.goto(GAME_URL);
    await page.waitForLoadState('networkidle');

    await page.locator('.game').click();
    const firstCell = page.locator('.game__cell').first();
    await firstCell.click();
    await page.keyboard.press('B');

    await expect(firstCell).toHaveClass(/game__cell--filled-me/);
  });

  test('Backspace clears the letter in the selected cell', async ({ page }) => {
    await authenticate(page);
    await page.goto(GAME_URL);
    await page.waitForLoadState('networkidle');

    await page.locator('.game').click();
    const firstCell = page.locator('.game__cell').first();
    await firstCell.click();
    await page.keyboard.press('C');
    await expect(firstCell.locator('.game__letter')).toContainText('C');

    await page.keyboard.press('Backspace');
    await expect(firstCell.locator('.game__letter')).toHaveCount(0);
  });

  test('ArrowRight moves selection to the next cell', async ({ page }) => {
    await authenticate(page);
    await page.goto(GAME_URL);
    await page.waitForLoadState('networkidle');

    await page.locator('.game').click();
    const cells = page.locator('.game__cell');
    await cells.first().click();
    await expect(cells.first()).toHaveClass(/game__cell--selected/);

    await page.keyboard.press('ArrowRight');
    await expect(cells.nth(1)).toHaveClass(/game__cell--selected/);
    await expect(cells.first()).not.toHaveClass(/game__cell--selected/);
  });

  test('typing a letter after placing moves selection right', async ({ page }) => {
    await authenticate(page);
    await page.goto(GAME_URL);
    await page.waitForLoadState('networkidle');

    await page.locator('.game').click();
    const cells = page.locator('.game__cell');
    await cells.first().click();
    await page.keyboard.press('D');

    // After typing, selection should have moved to second cell
    await expect(cells.nth(1)).toHaveClass(/game__cell--selected/);
  });

  test('letter from other player gets game__cell--filled-other class', async ({ page }) => {
    // Requires a second connected player sending a GridUpdated WS event.
    // This scenario requires the full integration environment with two browser sessions.
    // Covered by SessionWebSocketIT integration test in cocro-bff.
    test.fail(true, 'This test documents a scenario requiring two concurrent WS sessions');
  });
});
