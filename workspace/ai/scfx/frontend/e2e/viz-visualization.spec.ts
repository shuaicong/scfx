import { test, expect } from '@playwright/test'

test.describe('Knowledge Visualization Page', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/knowledge/visualization')
    await page.waitForLoadState('domcontentloaded')
    await page.waitForTimeout(2000)
  })

  test('should show empty state when no category selected', async ({ page }) => {
    await expect(page.getByText('请先选择一个分类')).toBeVisible({ timeout: 10000 })
    await expect(page.locator('.el-select').first()).toBeVisible()
  })

  test('should load categories, select one, and render chart', async ({ page }) => {
    // Open the select dropdown
    const selectWrapper = page.locator('.el-select__wrapper').first()
    await expect(selectWrapper).toBeVisible({ timeout: 10000 })
    await selectWrapper.click()
    await page.waitForTimeout(500)

    // Verify categories appear in dropdown
    await expect(page.locator('.el-select-dropdown__item').first()).toBeVisible({ timeout: 5000 })
    const itemCount = await page.locator('.el-select-dropdown__item').count()
    expect(itemCount).toBeGreaterThan(0)
    console.log(`Categories loaded: ${itemCount}`)

    // Select first category
    const catName = await page.locator('.el-select-dropdown__item').first().textContent()
    console.log(`Selected: "${catName}"`)
    await page.locator('.el-select-dropdown__item').first().click()

    // Wait for data to load and chart to render
    await page.waitForTimeout(3000)

    // Stats bar should show data
    await expect(page.locator('.viz-stats')).toBeVisible({ timeout: 10000 })
    const statsText = await page.locator('.viz-stats').textContent()
    console.log(`Stats: ${statsText?.trim()}`)

    // Chart canvas should exist (ECharts renders it)
    await expect(page.locator('.viz-chart canvas').first()).toBeVisible({ timeout: 5000 })
    console.log('✓ Chart canvas rendered')

    // Verify canvas has content dimensions
    const box = await page.locator('.viz-chart canvas').first().boundingBox()
    expect(box).not.toBeNull()
    expect(box!.width).toBeGreaterThan(100)
    expect(box!.height).toBeGreaterThan(100)
    console.log(`Canvas dimensions: ${box!.width.toFixed(0)}x${box!.height.toFixed(0)}`)

    await page.screenshot({ path: '/tmp/viz-chart-ok.png', fullPage: true })
  })

  test('should complete PCA recompute cycle', async ({ page }) => {
    // Select a category
    const selectWrapper = page.locator('.el-select__wrapper').first()
    await expect(selectWrapper).toBeVisible({ timeout: 10000 })
    await selectWrapper.click()
    await page.waitForTimeout(500)

    await expect(page.locator('.el-select-dropdown__item').first()).toBeVisible({ timeout: 5000 })
    await page.locator('.el-select-dropdown__item').first().click()
    await page.waitForTimeout(2000)

    // Click PCA recompute
    const pcaBtn = page.getByRole('button', { name: '重算 PCA' })
    await expect(pcaBtn).toBeEnabled({ timeout: 10000 })
    await pcaBtn.click()

    // Should show success message
    await expect(page.locator('.el-message--success')).toBeVisible({ timeout: 15000 })
    const msg = await page.locator('.el-message--success .el-message__content').textContent()
    console.log(`PCA result: ${msg?.trim()}`)

    // After recompute, data reloads and chart should render
    await page.waitForTimeout(3000)
    await expect(page.locator('.viz-chart canvas').first()).toBeVisible({ timeout: 5000 })
    const statsText = await page.locator('.viz-stats').textContent()
    console.log(`Stats after PCA: ${statsText?.trim()}`)
  })
})
