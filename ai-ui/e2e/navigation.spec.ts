import { test, expect } from '@playwright/test';

test.describe('Navigation', () => {
  test('loads the app with nav bar', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('.nav-brand')).toHaveText('FitnessAI');
    await expect(page.locator('.nav-link').first()).toBeVisible();
  });

  test('defaults to chat page', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('.nav-link.active')).toHaveText('Chat');
    await expect(page.locator('.chat-page')).toBeVisible();
  });

  test('can switch to dashboard', async ({ page }) => {
    await page.goto('/');
    await page.click('.nav-link:has-text("Dashboard")');
    await expect(page.locator('.nav-link.active')).toHaveText('Dashboard');
    await expect(page.locator('.dashboard')).toBeVisible();
  });

  test('can switch back to chat', async ({ page }) => {
    await page.goto('/');
    await page.click('.nav-link:has-text("Dashboard")');
    await page.click('.nav-link:has-text("Chat")');
    await expect(page.locator('.chat-page')).toBeVisible();
  });
});
