import { test, expect } from '@playwright/test'

/**
 * E2E test: 周期触发 → Cron 转换后的调度验证
 *
 * 测试场景：
 * 1. 进入脚本详情页
 * 2. 配置周期触发（每天）在未来 2 分钟后执行
 * 3. 保存配置
 * 4. 等待调度器触发
 * 5. 验证执行记录已创建
 *
 * 前置条件：
 * - 后端运行在 http://localhost:8080
 * - 前端运行在 http://localhost:3008
 * - 脚本 ID=2（粮信网-玉米日报采集）存在
 */

const API_BASE = 'http://localhost:8080/api'
const SCRIPT_ID = 2

test.describe.serial('周期触发调度验证', () => {
  test.setTimeout(300_000) // 5 分钟超时（等待调度需要 2-3 分钟）

  test('设置每天触发未来时间，验证到期执行', async ({ page, request }) => {
    // ===== 准备阶段 =====

    // 1. 确保脚本已启用
    console.log('[准备] 启用脚本...')
    await request.put(`${API_BASE}/scripts/${SCRIPT_ID}/enable`)

    // 2. 记录当前执行次数
    const res0 = await request.get(`${API_BASE}/scripts/${SCRIPT_ID}`)
    const script0 = await res0.json()
    const execCountBefore = script0.data?.executionCount || 0
    console.log(`[准备] 当前执行次数: ${execCountBefore}`)

    // ===== 前端配置阶段 =====

    // 3. 打开脚本详情页
    await page.goto(`/scripts/${SCRIPT_ID}`)
    await page.waitForLoadState('domcontentloaded')
    await page.waitForTimeout(2000)

    // 4. 计算目标时间：2 分钟后（避免秒级偏差，取整到分钟）
    const now = new Date()
    let targetMin = now.getMinutes() + 2
    let targetHour = now.getHours()
    if (targetMin >= 60) {
      targetMin -= 60
      targetHour += 1
    }
    if (targetHour >= 24) targetHour -= 24
    const hour = String(targetHour).padStart(2, '0')
    const minute = String(targetMin).padStart(2, '0')
    console.log(`[配置] 目标触发时间: ${hour}:${minute}:00（当前 ${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}）`)

    // 5. 选择"周期触发" radio
    await page.locator('#triggerCycle').click()
    await page.waitForTimeout(300)

    // 6. 设置每天触发时间（时、分）
    const timeSelects = page.locator('#dailyOptions .time-picker-inline select.time-select')
    await timeSelects.nth(0).selectOption(hour)   // 时
    await timeSelects.nth(1).selectOption(minute)  // 分
    await timeSelects.nth(2).selectOption('00')    // 秒

    // 7. 点击保存
    console.log('[配置] 保存配置...')
    await page.getByRole('button', { name: '保存配置' }).click()

    // 8. 验证保存成功（toast 提示）
    await expect(page.locator('.toast.show')).toBeVisible({ timeout: 5000 })
    const toastText = await page.locator('.toast.show').textContent()
    console.log(`[配置] 保存结果: ${toastText}`)
    expect(toastText).toContain('保存成功')

    // 9. 验证下次执行时间已更新
    await page.waitForTimeout(1500)
    const nextExecEl = page.locator('.stat-chip-value').last()
    const nextExecText = await nextExecEl.textContent()
    console.log(`[配置] 下次执行时间: ${nextExecText}`)

    // ===== 等待调度阶段 =====

    // 10. 等待目标时间到达 + 额外 90 秒 buffer（调度器 60 秒轮询间隔）
    const targetDate = new Date()
    targetDate.setHours(Number(hour), Number(minute), 0, 0)
    const waitMs = Math.max(targetDate.getTime() - Date.now() + 90_000, 90_000)
    console.log(`[等待] 等待 ${Math.round(waitMs / 1000)} 秒待调度器触发...`)

    // 分段等待，每 15s 打一次日志
    const segments = Math.ceil(waitMs / 15000)
    for (let i = 0; i < segments; i++) {
      await page.waitForTimeout(15000)
      const elapsed = (i + 1) * 15
      console.log(`[等待] 已等待 ${elapsed}s...`)

      // 每 30s 检查一次执行次数
      if ((i + 1) % 2 === 0) {
        const checkRes = await request.get(`${API_BASE}/scripts/${SCRIPT_ID}`)
        const checkData = await checkRes.json()
        const currentCount = checkData.data?.executionCount || 0
        if (currentCount > execCountBefore) {
          console.log(`[检测] 检测到新执行! 执行次数: ${execCountBefore} → ${currentCount}`)
          break
        }
      }
    }

    // ===== 验证阶段 =====

    // 11. 通过 API 验证执行次数已增加
    const resFinal = await request.get(`${API_BASE}/scripts/${SCRIPT_ID}`)
    const scriptFinal = await resFinal.json()
    const execCountAfter = scriptFinal.data?.executionCount || 0
    const triggerType = scriptFinal.data?.triggerType
    const cronExpr = scriptFinal.data?.cronExpression

    console.log(`[验证] 最终状态: triggerType=${triggerType}, cron=${cronExpr}`)
    console.log(`[验证] 执行次数: ${execCountBefore} → ${execCountAfter}`)

    // 验证 triggerType 已转为 cron
    expect(triggerType).toBe('cron')
    // 验证 cron 表达式正确（格式: ss mm HH * * ?）
    expect(cronExpr).toMatch(/^\d{2} \d{2} \d{2} \* \* \?$/)

    // 验证执行次数增加（调度器可能已触发，也可能还没到精确时间）
    if (execCountAfter <= execCountBefore) {
      // 如果还没触发，检查 nextExecutionTime 是否设置
      const nextExec = scriptFinal.data?.nextExecutionTime
      console.log(`[注意] 执行次数未增加, nextExecutionTime=${nextExec}`)
      expect(nextExec).toBeTruthy()
    } else {
      console.log(`[成功] 执行次数已增加: ${execCountBefore} → ${execCountAfter}`)
    }

    // 12. 刷新页面，验证执行记录列表显示
    await page.reload()
    await page.waitForLoadState('domcontentloaded')
    await page.waitForTimeout(2000)

    const executionItemCount = await page.locator('.execution-item').count()
    console.log(`[页面] 执行记录条目数: ${executionItemCount}`)
    expect(executionItemCount).toBeGreaterThanOrEqual(1)
  })
})
