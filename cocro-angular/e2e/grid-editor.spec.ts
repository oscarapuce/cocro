import { test, expect } from '@playwright/test';

test.describe('Grid Editor', () => {
  const fakeUser = {
    userId: 'test-id',
    username: 'testplayer',
    roles: ['PLAYER'],
    token: 'fake-jwt-token',
  };

  test.beforeEach(async ({ page }) => {
    // Authenticate via localStorage, then navigate to editor
    await page.goto('/');
    await page.evaluate((user) => {
      localStorage.setItem('cocro_token', user.token);
      localStorage.setItem('cocro_user', JSON.stringify(user));
    }, fakeUser);
    await page.goto('/grid/create');
    await page.waitForLoadState('networkidle');
  });

  test('editor page renders without redirect', async ({ page }) => {
    expect(page.url()).toContain('/grid/create');
    await expect(page.locator('.grid-editor')).toBeVisible({ timeout: 10000 });
  });

  test('grid renders with default 10x8 cells', async ({ page }) => {
    await expect(page.locator('.grid-editor')).toBeVisible({ timeout: 10000 });

    // Default grid is 10 columns x 8 rows = 80 cells
    const cells = page.locator('cocro-grid-cell');
    await expect(cells).toHaveCount(80);
  });

  test('parameter card shows grid title input', async ({ page }) => {
    await expect(page.locator('.grid-editor')).toBeVisible({ timeout: 10000 });

    const titleInput = page.locator('#grid-title');
    await expect(titleInput).toBeVisible();
    await expect(titleInput).toHaveValue('Nouvelle grille');
  });

  test('grid title can be edited', async ({ page }) => {
    await expect(page.locator('.grid-editor')).toBeVisible({ timeout: 10000 });

    const titleInput = page.locator('#grid-title');
    await titleInput.clear();
    await titleInput.fill('Ma grille de test');
    await expect(titleInput).toHaveValue('Ma grille de test');
  });

  test('parameter card shows row and column counts', async ({ page }) => {
    await expect(page.locator('.grid-editor')).toBeVisible({ timeout: 10000 });

    // Default: 8 rows, 10 columns
    await expect(page.locator('.grid-size-row').first()).toContainText('8');
    await expect(page.locator('.grid-size-row').last()).toContainText('10');
  });

  test('add row increases grid size', async ({ page }) => {
    await expect(page.locator('.grid-editor')).toBeVisible({ timeout: 10000 });

    // Initial: 80 cells (10x8)
    await expect(page.locator('cocro-grid-cell')).toHaveCount(80);

    // Click "+" on the Lignes row (first grid-size-row)
    const rowControls = page.locator('.grid-size-row').first().locator('.resize-controls');
    await rowControls.locator('cocro-button:last-child').click();

    // Now 10x9 = 90 cells
    await expect(page.locator('cocro-grid-cell')).toHaveCount(90);
    await expect(page.locator('.grid-size-row').first()).toContainText('9');
  });

  test('remove row decreases grid size', async ({ page }) => {
    await expect(page.locator('.grid-editor')).toBeVisible({ timeout: 10000 });

    // Click "-" on Lignes row
    const rowControls = page.locator('.grid-size-row').first().locator('.resize-controls');
    await rowControls.locator('cocro-button:first-child').click();

    // Now 10x7 = 70 cells
    await expect(page.locator('cocro-grid-cell')).toHaveCount(70);
    await expect(page.locator('.grid-size-row').first()).toContainText('7');
  });

  test('add column increases grid size', async ({ page }) => {
    await expect(page.locator('.grid-editor')).toBeVisible({ timeout: 10000 });

    // Click "+" on Colonnes row (second grid-size-row)
    const colControls = page.locator('.grid-size-row').last().locator('.resize-controls');
    await colControls.locator('cocro-button:last-child').click();

    // Now 11x8 = 88 cells
    await expect(page.locator('cocro-grid-cell')).toHaveCount(88);
    await expect(page.locator('.grid-size-row').last()).toContainText('11');
  });

  test('remove column decreases grid size', async ({ page }) => {
    await expect(page.locator('.grid-editor')).toBeVisible({ timeout: 10000 });

    // Click "-" on Colonnes row
    const colControls = page.locator('.grid-size-row').last().locator('.resize-controls');
    await colControls.locator('cocro-button:first-child').click();

    // Now 9x8 = 72 cells
    await expect(page.locator('cocro-grid-cell')).toHaveCount(72);
    await expect(page.locator('.grid-size-row').last()).toContainText('9');
  });

  test('cell type toggle buttons are visible', async ({ page }) => {
    await expect(page.locator('.grid-editor')).toBeVisible({ timeout: 10000 });

    const toggleBtns = page.locator('.cell-type-toggle .toggle-btn');
    await expect(toggleBtns).toHaveCount(4);
    await expect(toggleBtns.nth(0)).toContainText('Lettre');
    await expect(toggleBtns.nth(1)).toContainText('Indice simple');
    await expect(toggleBtns.nth(2)).toContainText('Indice double');
    await expect(toggleBtns.nth(3)).toContainText('Case noire');
  });

  test('clicking a cell selects it', async ({ page }) => {
    await expect(page.locator('.grid-editor')).toBeVisible({ timeout: 10000 });

    const firstCell = page.locator('cocro-grid-cell').first();
    await firstCell.click();

    // The clicked cell should have the "selected" class on its inner div
    await expect(firstCell.locator('.cell.selected')).toBeVisible();
  });

  test('changing cell type to Case noire updates the cell', async ({ page }) => {
    await expect(page.locator('.grid-editor')).toBeVisible({ timeout: 10000 });

    // Click first cell to select it
    const firstCell = page.locator('cocro-grid-cell').first();
    await firstCell.click();
    await expect(firstCell.locator('.cell.selected')).toBeVisible();

    // Click "Case noire" toggle
    await page.locator('.cell-type-toggle .toggle-btn').nth(3).click();

    // The cell should now have a .black div (black cell)
    await expect(firstCell.locator('.black')).toBeVisible();
  });

  test('changing cell type to Indice simple shows clue input', async ({ page }) => {
    await expect(page.locator('.grid-editor')).toBeVisible({ timeout: 10000 });

    const firstCell = page.locator('cocro-grid-cell').first();
    await firstCell.click();

    // Click "Indice simple"
    await page.locator('.cell-type-toggle .toggle-btn').nth(1).click();

    // The cell should now show a clue input
    await expect(firstCell.locator('cocro-clue-input')).toBeVisible();

    // The "Contenu de l'indice" editor card should appear
    await expect(page.getByText("Contenu de l'indice")).toBeVisible();
  });

  test('letter cell shows content editor when selected', async ({ page }) => {
    await expect(page.locator('.grid-editor')).toBeVisible({ timeout: 10000 });

    // Default cells are LETTER type, clicking one should show "Contenu de la lettre"
    const firstCell = page.locator('cocro-grid-cell').first();
    await firstCell.click();

    await expect(page.getByText('Contenu de la lettre')).toBeVisible();
  });

  test('submit button is visible', async ({ page }) => {
    await expect(page.locator('.grid-editor')).toBeVisible({ timeout: 10000 });

    const submitBtn = page.locator('cocro-button[variant="primary"]');
    await expect(submitBtn).toBeVisible();
    await expect(submitBtn).toContainText('Creer la grille');
  });

  test('keyboard arrow navigation moves selection', async ({ page }) => {
    await expect(page.locator('.grid-editor')).toBeVisible({ timeout: 10000 });

    // Click first cell (0,0)
    const firstCell = page.locator('cocro-grid-cell').first();
    await firstCell.click();
    await expect(firstCell.locator('.cell.selected')).toBeVisible();

    // Press ArrowRight to move to cell (1,0)
    await page.keyboard.press('ArrowRight');

    // Second cell should now be selected
    const secondCell = page.locator('cocro-grid-cell').nth(1);
    await expect(secondCell.locator('.cell.selected')).toBeVisible();

    // First cell should no longer be selected
    await expect(firstCell.locator('.cell.selected')).toHaveCount(0);
  });

  test('switching from Case noire back to Lettre restores letter cell', async ({ page }) => {
    await expect(page.locator('.grid-editor')).toBeVisible({ timeout: 10000 });

    const firstCell = page.locator('cocro-grid-cell').first();
    await firstCell.click();

    // Change to Case noire
    await page.locator('.cell-type-toggle .toggle-btn').nth(3).click();
    await expect(firstCell.locator('.black')).toBeVisible();

    // Change back to Lettre
    await page.locator('.cell-type-toggle .toggle-btn').nth(0).click();
    await expect(firstCell.locator('cocro-letter-input')).toBeVisible();
  });
});
