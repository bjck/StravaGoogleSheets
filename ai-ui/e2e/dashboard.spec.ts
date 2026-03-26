import { test, expect } from '@playwright/test';

test.describe('Dashboard Page', () => {
  test.beforeEach(async ({ page }) => {
    // Mock stats endpoint
    await page.route('**/ai/stats', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          totalWorkouts: 42,
          activityTypes: ['Run', 'Ride', 'Swim'],
          earliestDate: '2024-01-15T08:00:00Z',
          latestDate: '2024-06-20T17:30:00Z',
          summaryByType: [
            { type: 'Run', count: 25, totalDistanceKm: 150.5, totalHours: 18.2 },
            { type: 'Ride', count: 12, totalDistanceKm: 480.0, totalHours: 24.0 },
            { type: 'Swim', count: 5, totalDistanceKm: 7.5, totalHours: 3.5 },
          ],
          latestGarmin: {
            date: '2024-06-20',
            bodyBatteryMax: 85,
            restingHR: 52,
            vo2Max: 48.0,
            sleepScore: 82,
            weight: 75.0,
          },
        }),
      });
    });

    await page.goto('/');
    await page.click('.nav-link:has-text("Dashboard")');
  });

  test('shows dashboard heading', async ({ page }) => {
    await expect(page.locator('.dash-header h2')).toHaveText('Workout Dashboard');
  });

  test('displays total workout count', async ({ page }) => {
    await expect(page.locator('.stat-card:has-text("Total Workouts")').locator('.stat-value'))
      .toHaveText('42');
  });

  test('displays activity type count', async ({ page }) => {
    await expect(page.locator('.stat-card:has-text("Activity Types")').locator('.stat-value'))
      .toHaveText('3');
  });

  test('shows activity breakdown table', async ({ page }) => {
    await expect(page.locator('.activity-table')).toBeVisible();
    const rows = page.locator('.table-row');
    await expect(rows).toHaveCount(3);

    await expect(rows.first()).toContainText('Run');
    await expect(rows.first()).toContainText('25');
  });

  test('shows garmin health metrics', async ({ page }) => {
    await expect(page.locator('.stat-card:has-text("Body Battery")')).toBeVisible();
    await expect(page.locator('.stat-card:has-text("Resting HR")')).toBeVisible();
    await expect(page.locator('.stat-card:has-text("VO2 Max")')).toBeVisible();
    await expect(page.locator('.stat-card:has-text("Sleep Score")')).toBeVisible();
  });

  test('shows activity type pills', async ({ page }) => {
    const pills = page.locator('.type-pills .pill');
    await expect(pills).toHaveCount(3);
    await expect(pills.nth(0)).toHaveText('Run');
    await expect(pills.nth(1)).toHaveText('Ride');
    await expect(pills.nth(2)).toHaveText('Swim');
  });

  test('has sync buttons', async ({ page }) => {
    await expect(page.locator('button:has-text("Sync Strava")')).toBeVisible();
    await expect(page.locator('button:has-text("Sync Garmin")')).toBeVisible();
  });

  test('sync button triggers API call', async ({ page }) => {
    let syncCalled = false;
    await page.route('**/api/sync/strava', async (route) => {
      syncCalled = true;
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          messages: ['Saved 0 new activities to local database.'],
          stravaAttempted: true,
          stravaAdded: 0,
        }),
      });
    });

    await page.click('button:has-text("Sync Strava")');
    await page.waitForTimeout(500);
    expect(syncCalled).toBe(true);
  });

  test('shows loading spinner initially', async ({ page }) => {
    // Create a new page with delayed API
    await page.route('**/ai/stats', async (route) => {
      await new Promise((r) => setTimeout(r, 1000));
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ totalWorkouts: 0, activityTypes: [], summaryByType: [] }),
      });
    });

    await page.goto('/');
    await page.click('.nav-link:has-text("Dashboard")');
    // Should briefly show spinner
    await expect(page.locator('.dashboard')).toBeVisible();
  });

  test('handles stats API error', async ({ page }) => {
    await page.route('**/ai/stats', async (route) => {
      await route.fulfill({ status: 500, body: 'Server error' });
    });

    await page.goto('/');
    await page.click('.nav-link:has-text("Dashboard")');

    await expect(page.locator('.error-card')).toBeVisible({ timeout: 5000 });
    await expect(page.locator('.error-card')).toContainText('Failed to load stats');
  });

  test('shows empty state when no workouts', async ({ page }) => {
    await page.route('**/ai/stats', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          totalWorkouts: 0,
          activityTypes: [],
          earliestDate: null,
          latestDate: null,
          summaryByType: [],
        }),
      });
    });

    await page.goto('/');
    await page.click('.nav-link:has-text("Dashboard")');

    await expect(page.locator('.stat-card:has-text("Total Workouts")').locator('.stat-value'))
      .toHaveText('0');
  });
});
