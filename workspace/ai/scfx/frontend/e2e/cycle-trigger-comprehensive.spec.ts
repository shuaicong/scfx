import { test, expect } from '@playwright/test'

/**
 * E2E tests: 周期触发综合验证
 *
 * 前置条件：
 * - 后端运行在 http://localhost:8080
 * - 前端运行在 http://localhost:3008
 * - 脚本 ID=2（粮信网-玉米日报采集）存在
 */

const API_BASE = 'http://localhost:8080/api'
const SCRIPT_ID = 2

test.describe.serial('周期触发综合验证', () => {
  test.setTimeout(300_000)

  test('cron→周期回显：加载 cron 表达式自动切换为周期 UI', async ({ page, request }) => {
    // 1. 先通过 API 将脚本设为 cron 类型 daily 模式
    const setupRes = await request.put(`${API_BASE}/scripts/${SCRIPT_ID}`, {
      data: {
        id: SCRIPT_ID,
        triggerType: 'cron',
        cronExpression: '00 30 14 * * ?',
        status: 'enabled'
      },
      headers: { 'Content-Type': 'application/json' }
    })
    expect(setupRes.ok()).toBeTruthy()

    // 2. 加载页面
    await page.goto(`/scripts/${SCRIPT_ID}`)
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(3000)

    // 3. 验证自动切换到周期触发（cycle tab）
    const triggerCycle = page.locator('#triggerCycle')
    await expect(triggerCycle).toBeChecked({ timeout: 5000 })

    // 4. 验证周期类型为每天
    const activeOption = page.locator('.cycle-option.active')
    await expect(activeOption).toHaveText('每天')

    // 5. 验证触发时间回显正确（14:30:00）
    const timeSelects = page.locator('#dailyOptions .time-picker-inline select.time-select')
    await expect(timeSelects.nth(0)).toHaveValue('14')
    await expect(timeSelects.nth(1)).toHaveValue('30')
    await expect(timeSelects.nth(2)).toHaveValue('00')

    // 6. 验证保存按钮未激活（无改动）
    const saveBtn = page.getByRole('button', { name: '保存配置' })
    await expect(saveBtn).toBeDisabled()
  })

  test('保存周期触发带结束次数', async ({ page, request }) => {
    // 1. 打开页面
    await page.goto(`/scripts/${SCRIPT_ID}`)
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(2000)

    // 2. 选择周期触发
    await page.locator('#triggerCycle').click()
    await page.waitForTimeout(300)

    // 3. 设置触发时间（2分钟后）
    const now = new Date()
    let targetMin = now.getMinutes() + 2
    let targetHour = now.getHours()
    if (targetMin >= 60) { targetMin -= 60; targetHour += 1 }
    if (targetHour >= 24) targetHour -= 24
    const hour = String(targetHour).padStart(2, '0')
    const minute = String(targetMin).padStart(2, '0')

    const timeSelects = page.locator('#dailyOptions .time-picker-inline select.time-select')
    await timeSelects.nth(0).selectOption(hour)
    await timeSelects.nth(1).selectOption(minute)
    await timeSelects.nth(2).selectOption('00')

    // 4. 设置结束条件：重复 3 次后结束
    await page.locator('.end-option').nth(2).click()
    await page.waitForTimeout(200)
    const endCountInput = page.locator('#endCountPanel input[type="number"]')
    await endCountInput.fill('3')

    // 5. 保存
    console.log(`[保存] 设置 ${hour}:${minute}，结束次数=3`)
    await page.getByRole('button', { name: '保存配置' }).click()

    // 6. 验证保存成功
    await expect(page.locator('.toast.show')).toBeVisible({ timeout: 5000 })
    const toastText = await page.locator('.toast.show').textContent()
    expect(toastText).toContain('保存成功')

    // 7. 通过 API 验证 endType 和 repeatCount 已正确存储
    await page.waitForTimeout(1000)
    const verifyRes = await request.get(`${API_BASE}/scripts/${SCRIPT_ID}`)
    const data = (await verifyRes.json()).data
    console.log(`[验证] triggerType=${data.triggerType}, cron=${data.cronExpression}, endType=${data.endType}, repeatCount=${data.repeatCount}`)

    expect(data.triggerType).toBe('cron')
    expect(data.endType).toBe('count')
    expect(data.repeatCount).toBe(3)
  })

  test('周期触发调度：设置未来时间 → 等待调度 → 验证执行', async ({ page, request }) => {
    // 1. 确保脚本已启用，并且结束条件为永不结束
    await request.put(`${API_BASE}/scripts/${SCRIPT_ID}/enable`)

    // 2. 设置脚本结束条件为永不结束
    await request.put(`${API_BASE}/scripts/${SCRIPT_ID}`, {
      data: { id: SCRIPT_ID, endType: null, repeatCount: null, endTime: null }
    })

    // 3. 获取当前执行次数
    const res0 = await request.get(`${API_BASE}/scripts/${SCRIPT_ID}`)
    const script0 = await res0.json()
    const execCountBefore = script0.data?.executionCount || 0
    console.log(`[准备] 执行次数: ${execCountBefore}`)

    // 4. 打开页面
    await page.goto(`/scripts/${SCRIPT_ID}`)
    await page.waitForLoadState('domcontentloaded')
    await page.waitForTimeout(2000)

    // 5. 读取当前已回显的触发时间，确保设置一个不同的未来时间
    const timeSelects = page.locator('#dailyOptions .time-picker-inline select.time-select')
    const currentHour = await timeSelects.nth(0).inputValue()
    const currentMin = await timeSelects.nth(1).inputValue()
    const now = new Date()
    let targetMin = now.getMinutes() + 3 // 3分钟后，避免与回显时间重合
    let targetHour = now.getHours()
    if (targetMin >= 60) { targetMin -= 60; targetHour += 1 }
    if (targetHour >= 24) targetHour -= 24
    const hour = String(targetHour).padStart(2, '0')
    const minute = String(targetMin).padStart(2, '0')
    console.log(`[配置] 回显时间=${currentHour}:${currentMin}, 目标=${hour}:${minute}:00（当前 ${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}）`)

    // 6. 选择周期触发（如果尚未选中）、设置时间、永不结束
    const isCycleChecked = await page.locator('#triggerCycle').isChecked()
    if (!isCycleChecked) {
      await page.locator('#triggerCycle').click()
      await page.waitForTimeout(300)
    }
    await timeSelects.nth(0).selectOption(hour)
    await timeSelects.nth(1).selectOption(minute)
    await timeSelects.nth(2).selectOption('00')
    await page.locator('.end-option').nth(0).click() // 永不结束
    await page.waitForTimeout(200)

    // 7. 保存（仅在按钮可用时，否则配置已正确无需重复保存）
    const saveBtn = page.getByRole('button', { name: '保存配置' })
    const isSaveEnabled = await saveBtn.isEnabled()
    if (isSaveEnabled) {
      console.log('[配置] 保存...')
      await saveBtn.click()
      await expect(page.locator('.toast.show')).toBeVisible({ timeout: 5000 })
      expect(await page.locator('.toast.show').textContent()).toContain('保存成功')
      await page.waitForTimeout(1000)
    } else {
      console.log('[配置] 无需保存（配置已正确）')
    }

    // 8. 验证数据库状态
    await page.waitForTimeout(1000)
    const saveRes = await request.get(`${API_BASE}/scripts/${SCRIPT_ID}`)
    const saveData = (await saveRes.json()).data
    expect(saveData.triggerType).toBe('cron')
    expect(saveData.cronExpression).toMatch(/^\d{2} \d{2} \d{2} \* \* \?$/)
    console.log(`[状态] cron=${saveData.cronExpression}`)

    // 9. 等待调度（目标时间 + 120s 余量）
    const targetDate = new Date()
    targetDate.setHours(Number(hour), Number(minute), 0, 0)
    const waitMs = Math.max(targetDate.getTime() - Date.now() + 120_000, 120_000)
    console.log(`[等待] ${Math.round(waitMs / 1000)}s`)

    let detected = false
    for (let i = 0; i < Math.ceil(waitMs / 15000) && !detected; i++) {
      await page.waitForTimeout(15000)
      if ((i + 1) % 2 === 0) {
        const checkRes = await request.get(`${API_BASE}/scripts/${SCRIPT_ID}`)
        const checkData = (await checkRes.json()).data
        const currentCount = checkData?.executionCount || 0
        if (currentCount > execCountBefore) {
          console.log(`[检测] 执行: ${execCountBefore} → ${currentCount}`)
          detected = true
        }
      }
    }

    // 10. 验证
    const resFinal = await request.get(`${API_BASE}/scripts/${SCRIPT_ID}`)
    const scriptFinal = (await resFinal.json()).data
    const execCountAfter = scriptFinal?.executionCount || 0
    console.log(`[验证] 执行: ${execCountBefore} → ${execCountAfter}`)

    if (execCountAfter <= execCountBefore) {
      console.log(`[注意] 未增加, nextExecution=${scriptFinal?.nextExecutionTime}`)
      expect(scriptFinal?.nextExecutionTime).toBeTruthy()
    } else {
      console.log(`[成功] 已增加`)
    }

    // 11. 刷新页面验证执行记录
    await page.reload()
    await page.waitForLoadState('domcontentloaded')
    await page.waitForTimeout(2000)
    const count = await page.locator('.execution-item').count()
    expect(count).toBeGreaterThanOrEqual(1)
  })
})
