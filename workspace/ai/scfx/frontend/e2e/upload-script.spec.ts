import { test, expect } from '@playwright/test'

test.describe.serial('Script Upload Test', () => {
  test('should capture all 404s', async ({ page }) => {
    page.on('response', response => {
      if (response.status() === 404) {
        console.log(`[404] ${response.url()}`)
      }
    })
    
    await page.goto('/system/datasource')
    await page.waitForLoadState('domcontentloaded')
    await page.waitForTimeout(3000)
    
    // Upload to trigger the 404
    const liangxinRow = page.locator('.el-table__body tr:has-text("liangxin")').first()
    await liangxinRow.click()
    await page.waitForTimeout(1500)
    
    const uploadBtn = page.getByRole('button', { name: /上传脚本/u }).first()
    await uploadBtn.click()
    await page.waitForTimeout(1000)
    
    const fileInput = page.locator('input[type="file"]')
    await fileInput.setInputFiles('/Users/hucong/workspace/ai/scfx/python-collector-sdk/collectorsdk/collectors/liangxin.py')
    await page.waitForTimeout(5000)
    
    console.log('Done - check for 404s above')
  })
})
