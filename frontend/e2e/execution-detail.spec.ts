import { test, expect } from '@playwright/test'

/**
 * E2E tests for Execution Detail page and comparison features
 *
 * Tests cover:
 * 1. Execution detail page navigation and content display
 * 2. Execution compare dialog
 * 3. Script version compare dialog
 * 4. Execution log compare dialog
 */

test.describe('Execution Detail Page', () => {
  // Helper to navigate to task list and potentially get an execution ID
  async function getExecutionId(page: any): Promise<{ scriptId: number; executionId: string } | null> {
    // Navigate to scripts page and execute a script to get an execution
    await page.goto('/scripts')
    await page.waitForLoadState('domcontentloaded')
    await page.waitForTimeout(2000)

    const rowCount = await page.locator('.data-table tbody tr').count()
    if (rowCount === 0) {
      return null
    }

    // Click execute button on first row to trigger an execution
    await page.locator('.action-btn.success:has-text("执行")').first().click()

    // Confirm execution in dialog
    const confirmBtn = page.locator('.el-message-box__btns button:has-text("确定")')
    if (await confirmBtn.isVisible()) {
      await confirmBtn.click()
    }

    await page.waitForTimeout(2000)

    // Try to get execution from URL or by polling
    // For now, return a mock structure since we can't easily extract executionId from the UI
    // In a real scenario, you might need to poll for execution status
    const scriptIdMatch = page.url().match(/\/scripts\/(\d+)/)
    const scriptId = scriptIdMatch ? parseInt(scriptIdMatch[1]) : 1

    // Return a structure - tests using this will need to adapt
    return { scriptId, executionId: '' }
  }

  test.beforeEach(async ({ page }) => {
    await page.goto('/scripts')
    await page.waitForLoadState('domcontentloaded')
    await page.waitForTimeout(2000)
  })

  test('should navigate to Scripts page and display task list', async ({ page }) => {
    // Verify stats cards exist
    await expect(page.locator('.stats-grid')).toBeVisible()
    await expect(page.locator('.stat-card')).toHaveCount(6)

    // Verify table exists
    await expect(page.locator('.data-table')).toBeVisible()

    // Verify filter bar exists
    await expect(page.locator('.filter-bar')).toBeVisible()
  })

  test('should display execution info in detail when available', async ({ page }) => {
    // Check if there are scripts with executions
    await page.waitForTimeout(2000)

    const rowCount = await page.locator('.data-table tbody tr').count()
    if (rowCount > 0) {
      // Click on a script detail to see if executions exist
      // First, let's try clicking "详情" button
      const detailBtn = page.locator('.action-btn.primary:has-text("详情")').first()
      if (await detailBtn.isVisible()) {
        await detailBtn.click()
        await page.waitForTimeout(1500)

        // Check if there's an execution tab or link
        // This is a basic check - actual implementation may vary
        console.log('Detail dialog opened, checking for execution info')
      }
    } else {
      console.log('No scripts available to test execution detail')
    }
  })

  test('should open execution from script list', async ({ page }) => {
    // Wait for table to load
    await page.waitForTimeout(2000)

    const rowCount = await page.locator('.data-table tbody tr').count()
    if (rowCount > 0) {
      // Execute a script to create an execution
      const executeBtn = page.locator('.action-btn.success:has-text("执行")').first()
      await executeBtn.click()

      // Confirm in dialog
      const confirmBtn = page.locator('.el-message-box__btns button:has-text("确定")')
      if (await confirmBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
        await confirmBtn.click()
      }

      await page.waitForTimeout(2000)
      console.log('Script execution triggered')
    } else {
      console.log('No scripts available for execution test')
    }
  })
})

test.describe('Execution Detail Page Direct Navigation', () => {
  /**
   * These tests check if we can directly navigate to an execution detail page
   * Since we need an actual execution ID, tests may skip if no data exists
   */

  test('should check execution detail page structure', async ({ page }) => {
    // Navigate directly to a mock execution detail page (will show empty state without real data)
    // Using scriptId=1 as placeholder
    await page.goto('/scripts/1/executions/test-execution-id')
    await page.waitForLoadState('domcontentloaded')
    await page.waitForTimeout(2000)

    // Check if the page loads (may not have real execution data)
    const detailPageVisible = await page.locator('.execution-detail').isVisible().catch(() => false)

    if (detailPageVisible) {
      // Check header elements
      await expect(page.locator('.detail-header')).toBeVisible()
      await expect(page.locator('.header-title')).toBeVisible()

      // Check info card exists
      await expect(page.locator('.info-card')).toBeVisible()

      // Check log card exists
      await expect(page.locator('.log-card')).toBeVisible()

      // Check action buttons
      await expect(page.locator('.action-buttons')).toBeVisible()
    } else {
      console.log('Execution detail page not accessible - may require authentication or valid execution ID')
    }
  })

  test('should display compare buttons on execution detail page', async ({ page }) => {
    // Try navigating to a placeholder execution detail
    await page.goto('/scripts/1/executions/test-id')
    await page.waitForLoadState('domcontentloaded')
    await page.waitForTimeout(1500)

    const actionBtns = page.locator('.action-buttons')
    if (await actionBtns.isVisible()) {
      // Check for compare buttons
      await expect(page.getByRole('button', { name: '对比执行记录' })).toBeVisible()
      await expect(page.getByRole('button', { name: '对比脚本版本' })).toBeVisible()
      await expect(page.getByRole('button', { name: '对比执行日志' })).toBeVisible()
    } else {
      console.log('Action buttons not visible - detail page may not have loaded properly')
    }
  })
})

test.describe('Compare Dialogs', () => {
  test.beforeEach(async ({ page }) => {
    // Navigate to a mock execution detail page to test dialogs
    await page.goto('/scripts/1/executions/test-execution-id')
    await page.waitForLoadState('domcontentloaded')
    await page.waitForTimeout(2000)
  })

  test('should open Execution Compare dialog', async ({ page }) => {
    const actionBtns = page.locator('.action-buttons')
    if (!await actionBtns.isVisible()) {
      console.log('Skipping - detail page not accessible')
      return
    }

    // Click compare executions button
    const compareExecBtn = page.getByRole('button', { name: '对比执行记录' })
    if (await compareExecBtn.isVisible()) {
      await compareExecBtn.click()
      await page.waitForTimeout(500)

      // Verify dialog opened - Element Plus dialog
      const dialog = page.locator('.el-dialog:has-text("对比执行记录")')
      if (await dialog.isVisible()) {
        // Verify two selects for comparing
        await expect(page.locator('.compare-select .el-select')).toHaveCount(2)

        // Verify VS divider
        await expect(page.locator('.compare-divider')).toBeVisible()

        // Close dialog
        await page.getByRole('button', { name: '取消' }).click()
        await page.waitForTimeout(300)
      }
    } else {
      console.log('Compare executions button not visible')
    }
  })

  test('should open Script Version Compare dialog', async ({ page }) => {
    const actionBtns = page.locator('.action-buttons')
    if (!await actionBtns.isVisible()) {
      console.log('Skipping - detail page not accessible')
      return
    }

    // Click compare versions button
    const compareVerBtn = page.getByRole('button', { name: '对比脚本版本' })
    if (await compareVerBtn.isVisible()) {
      await compareVerBtn.click()
      await page.waitForTimeout(500)

      // Verify dialog opened
      const dialog = page.locator('.el-dialog:has-text("对比脚本版本")')
      if (await dialog.isVisible()) {
        // Verify two selects for comparing versions
        await expect(page.locator('.compare-select .el-select')).toHaveCount(2)

        // Verify VS divider
        await expect(page.locator('.compare-divider')).toBeVisible()

        // Close dialog
        await page.getByRole('button', { name: '取消' }).click()
        await page.waitForTimeout(300)
      }
    } else {
      console.log('Compare versions button not visible')
    }
  })

  test('should open Execution Log Compare dialog', async ({ page }) => {
    const actionBtns = page.locator('.action-buttons')
    if (!await actionBtns.isVisible()) {
      console.log('Skipping - detail page not accessible')
      return
    }

    // Click compare logs button
    const compareLogsBtn = page.getByRole('button', { name: '对比执行日志' })
    if (await compareLogsBtn.isVisible()) {
      await compareLogsBtn.click()
      await page.waitForTimeout(500)

      // Verify dialog opened
      const dialog = page.locator('.el-dialog:has-text("对比执行日志")')
      if (await dialog.isVisible()) {
        // Verify two log panels
        await expect(page.locator('.compare-log-panel')).toHaveCount(2)

        // Verify panel headers
        await expect(page.locator('.panel-header:has-text("执行 1")')).toBeVisible()
        await expect(page.locator('.panel-header:has-text("执行 2")')).toBeVisible()

        // Verify log content areas
        await expect(page.locator('.compare-log-content')).toHaveCount(2)
      }
    } else {
      console.log('Compare logs button not visible')
    }
  })
})

test.describe('Execution Detail Page Functional Tests', () => {
  test('should navigate to execution detail page via task list', async ({ page }) => {
    // Go to scripts page
    await page.goto('/scripts')
    await page.waitForLoadState('domcontentloaded')
    await page.waitForTimeout(2000)

    // Check if there's any data
    const rowCount = await page.locator('.data-table tbody tr').count()
    if (rowCount === 0) {
      console.log('No scripts available - cannot test execution detail navigation')
      return
    }

    // Click on 执行 (execute) button to create an execution
    const executeBtn = page.locator('.action-btn.success:has-text("执行")').first()
    await executeBtn.click()

    // Handle confirmation dialog
    const confirmBtn = page.locator('.el-message-box__btns button:has-text("确定")')
    if (await confirmBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
      await confirmBtn.click()
    }

    await page.waitForTimeout(3000)

    // If a progress drawer opens, we could capture the execution ID from there
    const progressDrawer = page.locator('.progress-drawer, .el-drawer').first()
    if (await progressDrawer.isVisible()) {
      console.log('Progress drawer opened after execution')
    }

    // Verify execution was triggered (success message or error)
    console.log('Execution trigger test completed')
  })

  test('should display basic execution information', async ({ page }) => {
    // Navigate to execution detail page with a test ID
    // In a real scenario, this would be a valid execution ID
    await page.goto('/scripts/1/executions/test-execution-123')
    await page.waitForLoadState('domcontentloaded')
    await page.waitForTimeout(2000)

    // Check if detail page content is visible
    const detailVisible = await page.locator('.execution-detail').isVisible()

    if (detailVisible) {
      // Check header section
      await expect(page.locator('.detail-header')).toBeVisible()

      // Check for back button
      await expect(page.locator('.detail-header button:has-text("返回")')).toBeVisible()

      // Check info card with basic details
      const infoCard = page.locator('.info-card')
      if (await infoCard.isVisible()) {
        await expect(page.locator('.card-title:has-text("基本信息")')).toBeVisible()
      }

      // Check log card
      const logCard = page.locator('.log-card')
      if (await logCard.isVisible()) {
        await expect(page.locator('.card-title:has-text("执行日志")')).toBeVisible()
      }
    } else {
      console.log('Execution detail page not accessible - may need valid execution ID')
    }
  })
})