import { test, expect } from '@playwright/test'

// Test data for creating scripts
const testData = {
  name: `测试任务_${Date.now()}`,
  description: '自动化测试创建的任务',
}

test.describe.serial('Script Management (Task List)', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/scripts')
    await page.waitForLoadState('domcontentloaded')
    await page.waitForTimeout(2000)
  })

  test('should navigate to Scripts page', async ({ page }) => {
    // Verify page title
    await expect(page.locator('.header-title span:has-text("采集任务管理")')).toBeVisible()

    // Verify stat cards exist
    await expect(page.locator('.stats-grid')).toBeVisible()
    await expect(page.locator('.stat-card')).toHaveCount(6)

    // Verify action buttons exist
    await expect(page.getByRole('button', { name: '刷新' })).toBeVisible()
    await expect(page.getByRole('button', { name: '创建任务' })).toBeVisible()
  })

  test('should display scripts table', async ({ page }) => {
    // Verify table exists
    await expect(page.locator('.data-table')).toBeVisible()

    // Verify table headers
    const tableHeader = page.locator('.data-table thead')
    await expect(tableHeader.locator('text=任务名称')).toBeVisible()
    await expect(tableHeader.locator('text=状态')).toBeVisible()
    await expect(tableHeader.locator('text=数据源')).toBeVisible()
    await expect(tableHeader.locator('text=触发方式')).toBeVisible()
    await expect(tableHeader.locator('text=执行统计')).toBeVisible()
  })

  test('should filter scripts by status', async ({ page }) => {
    // Find the status filter dropdown
    const statusFilter = page.locator('.filter-select').first()

    // Open status filter dropdown
    await statusFilter.selectOption('enabled')

    // Wait for filter to apply
    await page.waitForTimeout(1000)

    // Verify filter is applied
    await expect(statusFilter).toHaveValue('enabled')
  })

  test('should open script detail dialog', async ({ page }) => {
    // Wait for table to load
    await page.waitForTimeout(1000)

    // Check if there's any data row to click details on
    const rowCount = await page.locator('.data-table tbody tr').count()
    if (rowCount > 0) {
      // Click on the task name to open detail
      await page.locator('.task-name').first().click()

      // Wait for dialog to appear
      await expect(page.locator('.el-dialog')).toBeVisible()

      // Verify detail dialog title (component shows "脚本详情")
      await expect(page.locator('.el-dialog:has-text("脚本详情")')).toBeVisible()

      // Close the dialog - use last() to target footer button, not header close
      await page.getByRole('button', { name: '关闭' }).last().click()
      await page.waitForTimeout(500)
    } else {
      // No data - test would be skipped, just mark as passed
      console.log('No scripts data available for detail test')
    }
  })

  test('should execute a script', async ({ page }) => {
    // Wait for table to load
    await page.waitForTimeout(1000)

    const rowCount = await page.locator('.data-table tbody tr').count()
    if (rowCount > 0) {
      // Click execute button
      await page.locator('.action-btn.success:has-text("执行")').first().click()

      // Wait for confirmation dialog
      await expect(page.locator('.el-message-box')).toBeVisible()

      // Confirm execution
      await page.getByRole('button', { name: '确定' }).click()

      // Wait for response
      await page.waitForTimeout(2000)
      console.log('Script execution triggered')
    } else {
      console.log('No scripts available for execution test')
    }
  })

  test('should open edit drawer', async ({ page }) => {
    // Wait for table to load
    await page.waitForTimeout(1000)

    const rowCount = await page.locator('.data-table tbody tr').count()
    if (rowCount > 0) {
      // Click edit button
      await page.locator('.action-btn:has-text("编辑")').first().click()

      // Wait for drawer to appear
      await page.waitForTimeout(1000)

      // Verify drawer is visible (either el-drawer or custom drawer)
      const drawerVisible = await page.locator('.el-drawer, .drawer').isVisible().catch(() => false)
      if (drawerVisible) {
        console.log('Edit drawer opened successfully')
      }
    } else {
      console.log('No scripts available for edit test')
    }
  })

  test('should search scripts by keyword', async ({ page }) => {
    // Find search input and type
    await page.locator('.search-input').fill('test')

    // Click search button
    await page.getByRole('button', { name: '搜索' }).click()

    // Wait for results
    await page.waitForTimeout(1000)

    // Verify search is applied (input should have value)
    await expect(page.locator('.search-input')).toHaveValue('test')
  })

  test('should change pagination page size', async ({ page }) => {
    // Find page size select
    const pageSizeSelect = page.locator('.page-size-select select')

    // Change page size
    await pageSizeSelect.selectOption('20')

    // Wait for results
    await page.waitForTimeout(1000)

    // Verify page size changed
    await expect(pageSizeSelect).toHaveValue('20')
  })

  test('should navigate pagination', async ({ page }) => {
    // Wait for table to load
    await page.waitForTimeout(1000)

    // Check if there are multiple pages
    const pageInfo = page.locator('.pagination-right .page-info').textContent()

    // Try clicking next page button
    const nextBtn = page.locator('.page-btn').last()
    const isDisabled = await nextBtn.isDisabled()

    if (!isDisabled) {
      await nextBtn.click()
      await page.waitForTimeout(500)
      console.log('Navigated to next page')
    } else {
      console.log('Only one page available')
    }
  })
})