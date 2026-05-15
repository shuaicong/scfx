import { test, expect } from '@playwright/test'

// Generate unique test data once - shared across all tests
const testData = {
  code: `test_ds_${Date.now()}`,
  name: `测试数据源_${Date.now()}`,
  description: '自动化测试创建的数据源',
  loginUrl: 'https://example.com/login',
  authType: 'form'
}

test.describe.serial('DataSource CRUD', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/system/datasource')
    await page.waitForLoadState('domcontentloaded')
    await page.waitForTimeout(2000)
  })

  test('should navigate to DataSource page', async ({ page }) => {
    await expect(page.getByRole('heading', { name: '数据源管理' })).toBeVisible()
    await expect(page.getByRole('button', { name: '新增数据源' })).toBeVisible()
  })

  test('should create a new DataSource', async ({ page }) => {
    // Click create button
    await page.getByRole('button', { name: '新增数据源' }).click()

    // Wait for dialog to appear
    await expect(page.locator('.el-dialog')).toBeVisible()

    // Fill form fields
    await page.fill('input[placeholder="唯一标识，如 mysteel"]', testData.code)
    await page.fill('input[placeholder="显示名称，如 我的钢铁"]', testData.name)
    await page.fill('textarea[placeholder="数据源描述"]', testData.description)
    await page.fill('input[placeholder="登录页面URL"]', testData.loginUrl)

    // Select auth type - click on the el-select trigger (the visible element with "无认证" text)
    // Based on snapshot, click on the combobox to open dropdown
    await page.locator('.el-dialog .el-select').click()

    // Wait for dropdown to appear and click on option
    await page.waitForTimeout(500)
    await page.locator('.el-select-dropdown__item:has-text("登录表单")').click()

    // Submit
    await page.getByRole('button', { name: '确定' }).last().click()

    // Wait for success message
    await expect(page.locator('.el-message--success')).toBeVisible({ timeout: 10000 })

    // Verify new DataSource appears in table
    await expect(page.locator(`.el-table__body >> text="${testData.code}"`)).toBeVisible()
  })

  test('should list DataSources', async ({ page }) => {
    // Verify table headers exist in DOM
    const tableHeader = page.locator('.el-table__header')
    await expect(tableHeader.locator('text=标识')).toBeVisible()
    await expect(tableHeader.locator('text=名称')).toBeVisible()
    await expect(tableHeader.locator('text=描述')).toBeVisible()
    await expect(tableHeader.locator('text=状态')).toBeVisible()

    // Verify the created DataSource is in the list
    await expect(page.locator(`.el-table__body >> text="${testData.code}"`)).toBeVisible()
  })

  test('should update a DataSource', async ({ page }) => {
    // Find the row with our test data and click edit
    const row = page.locator(`.el-table__body tr:has-text("${testData.code}")`)
    await row.getByRole('button', { name: '编辑' }).click()

    // Wait for dialog to appear
    await expect(page.locator('.el-dialog')).toBeVisible()

    // Verify form is pre-filled (code field should be disabled)
    const codeInput = page.locator('.el-form input[placeholder="唯一标识，如 mysteel"]')
    await expect(codeInput).toBeDisabled()

    // Modify name
    const nameInput = page.locator('.el-form input[placeholder="显示名称，如 我的钢铁"]')
    await nameInput.clear()
    await nameInput.fill(`${testData.name}_updated`)

    // Submit
    await page.getByRole('button', { name: '确定' }).last().click()

    // Wait for success message
    await expect(page.locator('.el-message--success')).toBeVisible({ timeout: 10000 })

    // Verify update in table
    await expect(page.locator(`.el-table__body >> text="${testData.name}_updated"`)).toBeVisible()
  })

  test('should delete a DataSource', async ({ page }) => {
    // Find the row with our test data (use updated name)
    const row = page.locator(`.el-table__body tr:has-text("${testData.name}_updated")`)

    // Click delete button
    await row.getByRole('button', { name: '删除' }).click()

    // Wait for confirmation dialog
    await expect(page.locator('.el-message-box')).toBeVisible()

    // Confirm deletion
    await page.getByRole('button', { name: '确定' }).click()

    // Wait for success message
    await expect(page.locator('.el-message--success')).toBeVisible({ timeout: 10000 })

    // Verify DataSource is removed from table
    await expect(page.locator(`.el-table__body >> text="${testData.code}"`)).not.toBeVisible()
  })
})