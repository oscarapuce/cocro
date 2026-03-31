import { test, expect } from '@playwright/test';

test.describe('App smoke tests', () => {

  test('app boots without console errors', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', (err) => errors.push(err.message));

    await page.goto('/');
    await page.waitForLoadState('networkidle');

    expect(errors).toEqual([]);
  });

  test('landing page renders hero and join form', async ({ page }) => {
    await page.goto('/');

    // Hero text
    await expect(page.locator('h1')).toContainText('Mots fléchés');

    // Join form
    await expect(page.locator('cocro-input')).toBeVisible();
    await expect(page.locator('cocro-button[type="submit"]')).toBeVisible();

    // Nav buttons
    await expect(page.getByRole('button', { name: 'Se connecter', exact: true })).toBeVisible();
    await expect(page.getByText("S'inscrire")).toBeVisible();
  });

  test('landing page has notebook background styling', async ({ page }) => {
    await page.goto('/');

    const body = page.locator('body');
    const bg = await body.evaluate((el) => getComputedStyle(el).backgroundColor);
    // Should be paper color #F0EDE5 → rgb(240, 237, 229)
    expect(bg).toBe('rgb(240, 237, 229)');
  });
});

test.describe('Auth pages', () => {

  test('login page renders form', async ({ page }) => {
    await page.goto('/auth/login');

    await expect(page.getByText('Connexion').first()).toBeVisible();
    await expect(page.locator('cocro-button[type="submit"]')).toBeVisible();
  });

  test('register page renders form', async ({ page }) => {
    await page.goto('/auth/register');

    await expect(page.locator('h1')).toBeVisible();
    await expect(page.locator('cocro-button[type="submit"]')).toBeVisible();
  });
});

test.describe('Protected routes redirect', () => {

  test('home redirects to default route when unauthenticated', async ({ page }) => {
    await page.goto('/home');
    await page.waitForURL('**/');

    expect(page.url()).not.toContain('/home');
  });

  test('grid editor redirects to login when unauthenticated', async ({ page }) => {
    await page.goto('/grid');
    await page.waitForURL('**/auth/login');

    expect(page.url()).toContain('/auth/login');
  });

  test('lobby redirects to login when unauthenticated', async ({ page }) => {
    await page.goto('/lobby');
    await page.waitForURL('**/auth/login');

    expect(page.url()).toContain('/auth/login');
  });
});

test.describe('Navigation', () => {

  test('clicking "Se connecter" navigates to login', async ({ page }) => {
    await page.goto('/');
    await page.getByText('Se connecter').first().click();
    await page.waitForURL('**/auth/login');

    expect(page.url()).toContain('/auth/login');
  });

  test("clicking S'inscrire navigates to register", async ({ page }) => {
    await page.goto('/');
    await page.getByText("S'inscrire").first().click();
    await page.waitForURL('**/auth/register');

    expect(page.url()).toContain('/auth/register');
  });

  test('unknown route redirects to landing', async ({ page }) => {
    await page.goto('/nonexistent');
    await page.waitForURL('**/');

    await expect(page.locator('h1')).toContainText('Mots fléchés');
  });
});

test.describe('Authenticated routes', () => {
  // Simulate a logged-in PLAYER by setting localStorage before navigation
  const fakeUser = {
    userId: 'test-id',
    username: 'testplayer',
    roles: ['PLAYER'],
    token: 'fake-jwt-token',
  };

  test.beforeEach(async ({ page }) => {
    // Navigate first to set localStorage on the correct origin
    await page.goto('/');
    await page.evaluate((user) => {
      localStorage.setItem('cocro_token', user.token);
      localStorage.setItem('cocro_user', JSON.stringify(user));
    }, fakeUser);
  });

  test('grid/create loads the grid editor', async ({ page }) => {
    await page.goto('/grid/create');

    // Should NOT redirect to login
    expect(page.url()).toContain('/grid/create');

    // Editor should render — look for the grid wrapper or editor tools
    await expect(page.locator('.grid-editor')).toBeVisible({ timeout: 10000 });
  });

  test('home page loads for authenticated user', async ({ page }) => {
    await page.goto('/');

    expect(page.url()).toContain('/');
    await expect(page.locator('cocro-auth-sidebar')).toBeVisible();
  });

  test('sidebar shows username and logout for authenticated user', async ({ page }) => {
    await page.goto('/');

    await expect(page.locator('cocro-auth-sidebar').getByText('testplayer')).toBeVisible();
    await expect(page.locator('cocro-auth-sidebar').getByText('Déconnexion')).toBeVisible();
  });
});
