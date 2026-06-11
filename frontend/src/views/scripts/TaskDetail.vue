<template>
  <div class="task-detail-page">
    <!-- Header -->
    <div class="header">
      <button class="back-btn" @click="goBack">
        <span>←</span>
      </button>
      <div class="header-info">
        <div class="header-title">
          <span class="task-icon">{{ isCreateMode ? '✨' : taskIcon }}</span>
          <span class="task-name">{{ isCreateMode ? '创建任务' : scriptName }}</span>
        </div>
        <div class="header-meta" v-if="!isCreateMode">
          <span class="meta-tag">数据源: {{ sourceName }}</span>
          <span class="meta-tag">触发: {{ triggerTypeText }}</span>
          <span class="meta-tag" v-if="cronExpression">{{ cronExpression }}</span>
          <span class="meta-tag" v-else-if="formTriggerType === 'once' && onceDate">{{ onceDate }} {{ onceHour }}:{{ onceMinute }}:{{ onceSecond }}</span>
          <span class="meta-desc" v-if="description" :title="description">{{ description }}</span>
        </div>
      </div>
      <div class="header-actions" v-if="!isCreateMode">
        <button
          class="status-toggle"
          :class="{ enabled: status === 'enabled', disabled: status !== 'enabled' }"
          @click="handleToggleStatus"
        >
          <span class="toggle-dot"></span>
          <span>{{ status === 'enabled' ? '启用中' : '已禁用' }}</span>
        </button>
        <button class="btn btn-primary" @click="handleExecute" :disabled="executing">
          <span v-if="executing" class="spinner"></span><span v-else>⚡</span> {{ executing ? '执行中...' : '立即执行' }}
        </button>
      </div>
      <div class="header-actions">
        <button class="btn btn-primary" @click="saveScript" :disabled="!isCreateMode && !hasChanges"
          :title="!isCreateMode && !hasChanges ? '当前配置无变更，修改触发参数后自动激活' : (isCreateMode ? '创建新任务' : '保存配置')">
          <span>💾</span> {{ isCreateMode ? '创建任务' : '保存配置' }}
        </button>
      </div>
    </div>

    <!-- Page Container -->
    <div class="page-container">
      <!-- Stats Bar -->
      <div class="stats-bar" v-if="!isCreateMode">
        <div class="stat-chip">
          <div class="stat-chip-icon total">📊</div>
          <div class="stat-chip-info">
            <div class="stat-chip-value">{{ executionCount }}</div>
            <div class="stat-chip-label">执行次数</div>
          </div>
        </div>
        <div class="stat-chip">
          <div class="stat-chip-icon success">✓</div>
          <div class="stat-chip-info">
            <div class="stat-chip-value success">{{ successCount }}</div>
            <div class="stat-chip-label">成功</div>
          </div>
        </div>
        <div class="stat-chip">
          <div class="stat-chip-icon failed">✗</div>
          <div class="stat-chip-info">
            <div class="stat-chip-value failed">{{ failedCount }}</div>
            <div class="stat-chip-label">失败</div>
          </div>
        </div>
        <div class="stat-chip">
          <div class="stat-chip-icon rate">%</div>
          <div class="stat-chip-info">
            <div class="stat-chip-value rate">{{ successRate }}%</div>
            <div class="stat-chip-label">成功率</div>
          </div>
        </div>
        <div class="stat-chip" style="max-width: 180px;">
          <div class="stat-chip-info">
            <div class="stat-chip-label">下次执行</div>
            <div class="stat-chip-value" style="font-size: 14px;">{{ nextExecutionText }}</div>
          </div>
        </div>
      </div>

      <!-- Execution Result Cards -->
      <template v-if="!isCreateMode">
        <div class="result-card success" v-if="lastResult === 'success'" v-show="showResultCard">
          <div class="result-icon">✓</div>
          <div class="result-info">
            <div class="result-title">执行成功</div>
            <div class="result-meta">
              采集时间：<span>{{ lastDuration }}</span> · 采集数量：<span>{{ lastCollectedCount }}</span> 条
            </div>
          </div>
          <div class="result-actions">
            <button class="result-btn" @click="viewLogs">查看日志</button>
            <button class="result-btn" @click="reExecute">重新执行</button>
          </div>
        </div>

        <div class="result-card failed" v-if="lastResult === 'failed'" v-show="showResultCard">
          <div class="result-icon">✗</div>
          <div class="result-info">
            <div class="result-title">执行失败</div>
            <div class="result-meta">错误原因：<span>{{ lastError }}</span></div>
          </div>
          <div class="result-actions">
            <button class="result-btn" @click="viewLogs">查看日志</button>
            <button class="result-btn" @click="reExecute">重试</button>
          </div>
        </div>

        <div class="result-card cancelled" v-if="lastResult === 'cancelled'" v-show="showResultCard">
          <div class="result-icon">⊘</div>
          <div class="result-info">
            <div class="result-title">已取消</div>
            <div class="result-meta">用户主动取消，采集了 {{ lastCollectedCount }} 条后中断</div>
          </div>
          <div class="result-actions">
            <button class="result-btn" @click="viewLogs">查看日志</button>
            <button class="result-btn" @click="reExecute">重新执行</button>
          </div>
        </div>
      </template>

      <!-- Main Content Grid -->
      <div class="content-grid">
        <!-- Left: Sidebar -->
        <div class="sidebar">
          <!-- Task Info Card -->
          <div class="section-card">
            <div class="section-header">
              <div class="section-title">
                <span class="icon">📋</span>
                任务信息
              </div>
            </div>
            <div class="info-form">
              <div class="form-group">
                <label class="form-label">任务名称</label>
                <input type="text" class="form-input" v-model="form.scriptName" placeholder="请输入任务名称">
              </div>
              <div class="form-group">
                <label class="form-label">数据源</label>
                <select class="form-select" v-model="form.source" @change="onDatasourceChange">
                  <option v-for="ds in datasourceList" :key="ds.code" :value="ds.code">{{ ds.name }}</option>
                </select>
              </div>
              <div class="form-group">
                <label class="form-label">任务描述</label>
                <textarea class="form-input auto-height" v-model="form.description" rows="1" placeholder="任务描述（选填）" maxlength="5000"></textarea>
                <div class="char-count">{{ (form.description || '').length }} / 5000</div>
              </div>
              <div class="form-group">
                <label class="form-label">关联分类 <span class="required-asterisk">*</span></label>
                <div class="category-select-row">
                  <button class="btn-category-pick" @click="openCategoryPicker">
                    <span v-if="scriptCategoryName" class="category-name-display">
                      <span class="cat-icon">{{ selectedCategoryIcon }}</span>
                      {{ scriptCategoryName }}
                    </span>
                    <span v-else class="category-placeholder">选择分类（必填）</span>
                    <span v-if="scriptCategoryId" class="btn-clear-cat" @click.stop="clearCategory">×</span>
                  </button>
                </div>
                <span class="sync-hint">选择分类后，采集数据将自动归入该分类，可在 AI 问答中检索</span>
                <label class="collection-only-checkbox">
                  <input type="checkbox" v-model="collectionOnly" />
                  <span>仅采集（不进知识库）</span>
                </label>
              </div>
            </div>
          </div>
        <!-- Trigger Config Section -->
        <div class="section-card trigger-section">
          <div class="section-header">
            <div class="section-title">
              <span class="icon">⏰</span>
              触发配置
            </div>
          </div>

          <!-- Running execution banner -->
          <div class="execution-banner" v-if="hasRunningExecution">
            <span class="banner-icon">⚡</span>
            <span class="banner-text">
              脚本正在执行中，触发配置暂不可修改
            </span>
            <span class="banner-time" v-if="runningExecutionDetail?.startTime">
              开始于 {{ formatTime(runningExecutionDetail.startTime) }}
            </span>
            <span class="banner-hint">等待执行完成后即可修改</span>
          </div>
  
          <!-- Trigger Type Selector -->
          <div class="trigger-type-selector">
            <div class="radio-group">
              <div class="radio-option">
                <input type="radio" name="triggerType" value="once" id="triggerOnce" v-model="formTriggerType" @change="onTriggerChange"
                  :disabled="hasRunningExecution">
                <label class="radio-label" for="triggerOnce" :class="{ disabled: hasRunningExecution }">
                  <span class="radio-dot"></span>
                  单次触发
                </label>
              </div>
              <div class="radio-option">
                <input type="radio" name="triggerType" value="cycle" id="triggerCycle" v-model="formTriggerType" @change="onTriggerChange"
                  :disabled="hasRunningExecution">
                <label class="radio-label" for="triggerCycle" :class="{ disabled: hasRunningExecution }">
                  <span class="radio-dot"></span>
                  周期触发
                </label>
              </div>
              <!-- Cron 已合并到周期面板的高级选项中 -->
            </div>
          </div>
  
          <!-- Trigger Panels -->
          <div class="trigger-config">
            <!-- Once Trigger Panel -->
            <div class="trigger-panel" :class="{ active: formTriggerType === 'once' }" id="oncePanel">
              <div class="datetime-row">
                <div class="date-picker" @click="toggleDatePicker('once')" title="点击选择日期">
                  <span class="picker-icon">
                    <svg viewBox="0 0 20 20" fill="none" width="16" height="16">
                      <rect x="2" y="3" width="16" height="15" rx="2" stroke="currentColor" stroke-width="1.5"/>
                      <line x1="2" y1="8" x2="18" y2="8" stroke="currentColor" stroke-width="1.5"/>
                      <line x1="6" y1="1" x2="6" y2="5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                      <line x1="14" y1="1" x2="14" y2="5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                    </svg>
                  </span>
                  <span class="picker-text" :class="{ placeholder: !onceDate }">{{ onceDate || '请选择日期' }}</span>
                </div>
                <input type="date" ref="onceDateRef" style="position:absolute;opacity:0;pointer-events:none;" :min="todayStr" @change="updateOnceDate">
                <div class="time-picker-inline">
                  <select class="time-select" v-model="onceHour" @change="updateTriggerPreview">
                    <option v-for="h in 24" :key="h-1" :value="String(h-1).padStart(2,'0')">{{ String(h-1).padStart(2,'0') }}</option>
                  </select>
                  <span class="time-sep">:</span>
                  <select class="time-select" v-model="onceMinute" @change="updateTriggerPreview">
                    <option v-for="m in 60" :key="m-1" :value="String(m-1).padStart(2,'0')">{{ String(m-1).padStart(2,'0') }}</option>
                  </select>
                  <span class="time-sep">:</span>
                  <select class="time-select" v-model="onceSecond" @change="updateTriggerPreview">
                    <option v-for="s in 60" :key="s-1" :value="String(s-1).padStart(2,'0')">{{ String(s-1).padStart(2,'0') }}</option>
                  </select>
                </div>
              </div>
              <div class="once-summary" v-if="onceDate">
                将在 <strong>{{ onceDate }} {{ onceHour }}:{{ onceMinute }}:{{ onceSecond }}</strong> 执行一次
              </div>
            </div>
  
            <!-- Cycle Trigger Panel -->
            <div class="trigger-panel" :class="{ active: formTriggerType === 'cycle' }" id="cyclePanel">
              <div class="cycle-options">
                <div
                  class="cycle-option"
                  :class="{ active: cycleType === 'daily' }"
                  @click="cycleType = 'daily'; updateTriggerPreview()"
                >每天</div>
                <div
                  class="cycle-option"
                  :class="{ active: cycleType === 'weekly' }"
                  @click="cycleType = 'weekly'; updateTriggerPreview()"
                >每周</div>
                <div
                  class="cycle-option"
                  :class="{ active: cycleType === 'monthly' }"
                  @click="cycleType = 'monthly'; updateTriggerPreview()"
                >每月</div>
              </div>
  
              <!-- Daily Options -->
              <div class="sub-options" :class="{ hidden: cycleType !== 'daily' }" id="dailyOptions">
                <div class="time-row">
                  <span class="time-label">触发时间</span>
                  <div class="time-picker-inline">
                    <select class="time-select" v-model="dailyHour" @change="updateTriggerPreview">
                      <option v-for="h in 24" :key="h-1" :value="String(h-1).padStart(2,'0')">{{ String(h-1).padStart(2,'0') }}</option>
                    </select>
                    <span class="time-sep">:</span>
                    <select class="time-select" v-model="dailyMinute" @change="updateTriggerPreview">
                      <option v-for="m in 60" :key="m-1" :value="String(m-1).padStart(2,'0')">{{ String(m-1).padStart(2,'0') }}</option>
                    </select>
                    <span class="time-sep">:</span>
                    <select class="time-select" v-model="dailySecond" @change="updateTriggerPreview">
                      <option v-for="s in 60" :key="s-1" :value="String(s-1).padStart(2,'0')">{{ String(s-1).padStart(2,'0') }}</option>
                    </select>
                  </div>
                </div>
              </div>
  
              <!-- Weekly Options -->
              <div class="sub-options" :class="{ hidden: cycleType !== 'weekly' }" id="weeklyOptions">
                <div class="weekdays">
                  <div
                    v-for="(day, index) in ['一','二','三','四','五','六','日']"
                    :key="index"
                    class="weekday"
                    :class="{ active: weeklyDays.includes(index === 6 ? 0 : index + 1) }"
                    @click="toggleWeekday(index === 6 ? 0 : index + 1)"
                  >{{ day }}</div>
                </div>
                <div class="time-row">
                  <span class="time-label">触发时间</span>
                  <div class="time-picker-inline">
                    <select class="time-select" v-model="weeklyHour" @change="updateTriggerPreview">
                      <option v-for="h in 24" :key="h-1" :value="String(h-1).padStart(2,'0')">{{ String(h-1).padStart(2,'0') }}</option>
                    </select>
                    <span class="time-sep">:</span>
                    <select class="time-select" v-model="weeklyMinute" @change="updateTriggerPreview">
                      <option v-for="m in 60" :key="m-1" :value="String(m-1).padStart(2,'0')">{{ String(m-1).padStart(2,'0') }}</option>
                    </select>
                    <span class="time-sep">:</span>
                    <select class="time-select" v-model="weeklySecond" @change="updateTriggerPreview">
                      <option v-for="s in 60" :key="s-1" :value="String(s-1).padStart(2,'0')">{{ String(s-1).padStart(2,'0') }}</option>
                    </select>
                  </div>
                </div>
              </div>
  
              <!-- Monthly Options -->
              <div class="sub-options" :class="{ hidden: cycleType !== 'monthly' }" id="monthlyOptions">
                <div class="time-row">
                  <span class="time-label">每月</span>
                  <select class="time-select small" v-model="monthlyDay" @change="updateTriggerPreview">
                    <option v-for="d in 31" :key="d" :value="String(d).padStart(2,'0')">{{ String(d).padStart(2,'0') }}</option>
                  </select>
                  <span class="time-label">日</span>
                  <span class="time-label" style="margin-left: 16px;">触发时间</span>
                  <div class="time-picker-inline">
                    <select class="time-select" v-model="monthlyHour" @change="updateTriggerPreview">
                      <option v-for="h in 24" :key="h-1" :value="String(h-1).padStart(2,'0')">{{ String(h-1).padStart(2,'0') }}</option>
                    </select>
                    <span class="time-sep">:</span>
                    <select class="time-select" v-model="monthlyMinute" @change="updateTriggerPreview">
                      <option v-for="m in 60" :key="m-1" :value="String(m-1).padStart(2,'0')">{{ String(m-1).padStart(2,'0') }}</option>
                    </select>
                    <span class="time-sep">:</span>
                    <select class="time-select" v-model="monthlySecond" @change="updateTriggerPreview">
                      <option v-for="s in 60" :key="s-1" :value="String(s-1).padStart(2,'0')">{{ String(s-1).padStart(2,'0') }}</option>
                    </select>
                  </div>
                </div>
              </div>
  
              <div class="end-condition">
                <span class="end-label">结束条件</span>
                <div class="end-options">
                  <div
                    class="end-option"
                    :class="{ active: endType === 'never' }"
                    @click="endType = 'never'"
                  >永不结束</div>
                  <div
                    class="end-option"
                    :class="{ active: endType === 'date' }"
                    @click="endType = 'date'"
                  >于指定日结束</div>
                  <div
                    class="end-option"
                    :class="{ active: endType === 'count' }"
                    @click="endType = 'count'"
                  >重复一定次数后结束</div>
                </div>
                <div class="end-sub" :class="{ hidden: endType !== 'date' }" id="endDatePanel">
                  <div class="date-picker" @click="toggleEndDatePicker" title="点击选择日期">
                    <span class="picker-icon">
                      <svg viewBox="0 0 20 20" fill="none" width="16" height="16">
                        <rect x="2" y="3" width="16" height="15" rx="2" stroke="currentColor" stroke-width="1.5"/>
                        <line x1="2" y1="8" x2="18" y2="8" stroke="currentColor" stroke-width="1.5"/>
                        <line x1="6" y1="1" x2="6" y2="5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                        <line x1="14" y1="1" x2="14" y2="5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                      </svg>
                    </span>
                    <span class="picker-text" :class="{ placeholder: !endDate }">{{ endDate || '请选择日期' }}</span>
                  </div>
                  <input type="date" ref="endDateRef" style="position:absolute;opacity:0;pointer-events:none;" :min="todayStr" @change="updateEndDate">
                </div>
                <div class="end-sub" :class="{ hidden: endType !== 'count' }" id="endCountPanel">
                  <input type="number" class="form-input" style="width: 80px;" v-model="endCount" min="1">
                  <span style="color: var(--text-secondary); margin-left: 8px;">次</span>
                </div>
              </div>
              <!-- Advanced Cron (collapsible) inside cycle panel -->
              <div class="advanced-cron-section">
                <div class="advanced-cron-toggle" @click="showAdvancedCron = !showAdvancedCron">
                  <span class="toggle-icon">{{ showAdvancedCron ? '▼' : '▶' }}</span>
                  <span>Cron 表达式 <span class="advanced-badge">高级</span></span>
                  <code class="inline-cron">{{ cronExpression }}</code>
                </div>
                <div class="advanced-cron-body" :class="{ hidden: !showAdvancedCron }">
                  <div class="cron-input-row">
                    <input
                      type="text"
                      class="cron-input"
                      v-model="cronExpression"
                      placeholder="输入 Cron，如: 00 30 14 * * ?"
                      @input="onCronManualEdit"
                    >
                    <button class="cron-calc-btn" @click="calculateCronNext5">验证</button>
                  </div>
                  <div class="cron-next5">
                    <div class="next5-title">未来5次触发时间</div>
                    <div class="next5-list" id="cronNext5List">
                      <div v-for="(time, index) in cronNext5" :key="index" class="next5-item">
                        <span class="num">{{ index + 1 }}</span>
                        <span class="time">{{ time }}</span>
                      </div>
                      <div v-if="cronNext5.length === 0" class="next5-item empty">输入有效 Cron 后显示</div>
                    </div>
                  </div>
                  <div class="cron-help">
                    <div class="help-title">Cron 表达式说明</div>
                    <div class="help-grid">
                      <div class="help-row">
                        <span class="help-field">秒</span>
                        <span class="help-field">分</span>
                        <span class="help-field">时</span>
                        <span class="help-field">日</span>
                        <span class="help-field">月</span>
                        <span class="help-field">周</span>
                      </div>
                      <div class="help-row">
                        <span class="help-val">0</span>
                        <span class="help-val">30</span>
                        <span class="help-val">14</span>
                        <span class="help-val">*</span>
                        <span class="help-val">*</span>
                        <span class="help-val">?</span>
                      </div>
                    </div>
                    <div class="help-notes">
                      <div><code>*</code> 每、<code>?</code> 不指定、<code>,</code> 列举、<code>-</code> 范围、<code>/</code> 步长、<code>L</code> 最后</div>
                      <div>示例：<code>00 30 14 * * ?</code> = 每天 14:30、<code>00 00 9 * * 1-5</code> = 工作日 09:00</div>
                      <div class="help-tip">💡 在周期面板中修改触发时间，Cron 表达式会自动同步</div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

          <!-- Execution Record Card -->
          <div class="section-card" v-if="!isCreateMode">
            <div class="section-header">
              <div class="section-title">
                <span class="icon">📊</span>
                执行记录
              </div>
            </div>
            <div class="execution-list" :class="{ expanded: showingAllExecutions }">
              <div
                v-for="exec in recentExecutions"
                :key="exec.executionId"
                class="execution-item"
                :class="{ running: exec.status === 'running', expanded: expandedExecId === exec.executionId }"
              >
                <div class="execution-header" @click="expandedExecId = expandedExecId === exec.executionId ? null : exec.executionId">
                  <div class="execution-status" :class="exec.status">
                    <span class="status-dot" v-if="exec.status !== 'running'"></span>
                    <span class="pulse-dot" v-else></span>
                    <span>{{ statusText(exec.status) }}</span>
                  </div>
                  <span class="trigger-badge" :class="exec.triggerType">{{ exec.triggerType === 'manual' ? '手动' : '自动' }}</span>
                  <span class="execution-time">{{ formatTime(exec.startTime) }}</span>
                  <span class="execution-counts" v-if="exec.collectedCount != null">{{ exec.collectedCount }}条</span>
                  <span class="execution-duration" v-if="exec.durationMs">{{ (exec.durationMs / 1000).toFixed(1) }}s</span>
                  <span class="expand-icon">{{ expandedExecId === exec.executionId ? '▲' : '▼' }}</span>
                </div>
                <div class="execution-detail" v-if="expandedExecId === exec.executionId">
                  <div class="detail-grid">
                    <div class="detail-item">
                      <span class="detail-label">版本</span>
                      <span class="detail-value">v{{ exec.versionNum ?? exec.versionId ?? '-' }}</span>
                    </div>
                    <div class="detail-item">
                      <span class="detail-label">结束时间</span>
                      <span class="detail-value">{{ exec.endTime ? formatTime(exec.endTime) : '-' }}</span>
                    </div>
                    <div class="detail-item" v-if="exec.dataSizeMb != null">
                      <span class="detail-label">数据量</span>
                      <span class="detail-value">{{ exec.dataSizeMb.toFixed(2) }} MB</span>
                    </div>
                    <div class="detail-item" v-if="exec.collectedCount != null">
                      <span class="detail-label">采集</span>
                      <span class="detail-value">{{ exec.successCount || 0 }} 成功 / {{ exec.errorCount || 0 }} 失败 / {{ exec.skipCount || 0 }} 跳过</span>
                    </div>
                  </div>
                  <div class="phase-bar" v-if="exec.durationMs">
                    <div class="phase-label">各阶段耗时</div>
                    <div class="phase-row">
                      <span class="phase-chip login" :style="{ width: phasePct(exec.phaseLoginMs, exec.durationMs) }" title="登录">登录 {{ fmtMs(exec.phaseLoginMs) }}</span>
                      <span class="phase-chip crawl" :style="{ width: phasePct(exec.phaseCrawlMs, exec.durationMs) }" title="抓取">抓取 {{ fmtMs(exec.phaseCrawlMs) }}</span>
                      <span class="phase-chip parse" :style="{ width: phasePct(exec.phaseParseMs, exec.durationMs) }" title="解析">解析 {{ fmtMs(exec.phaseParseMs) }}</span>
                      <span class="phase-chip report" :style="{ width: phasePct(exec.phaseReportMs, exec.durationMs) }" title="上报">上报 {{ fmtMs(exec.phaseReportMs) }}</span>
                    </div>
                  </div>
                  <div class="detail-error" v-if="exec.errorMessage">
                    <span class="error-label">错误信息</span>
                    <span class="error-text">{{ exec.errorMessage }}</span>
                  </div>
                  <div class="detail-actions">
                    <button class="execution-log-btn" @click="viewExecutionLog(exec.executionId)">查看日志</button>
                  </div>
                </div>
              </div>
              <div v-if="recentExecutions.length === 0" class="empty-executions">
                暂无执行记录
              </div>
            </div>
            <div class="exec-history-link">
              <button class="view-all-btn" @click="goToFullHistory">查看全部执行历史 →</button>
            </div>
          </div>

          <!-- Realtime Log Panel -->
          <div class="section-card" v-if="showRealtimeLog" id="realtimeLogPanel">
            <div class="section-header">
              <div class="section-title">
                <span class="icon">📋</span>
                <span class="text">实时日志</span>
              </div>
              <div class="section-actions">
                <span class="log-status running">
                  <span class="spinner"></span> 执行中
                </span>
              </div>
            </div>
            <div class="log-container" id="logContainer">
              <div v-for="(log, index) in realtimeLogs" :key="index" class="log-item">
                <span class="log-time">{{ log.time }}</span>
                <span class="log-level" :class="log.level.toLowerCase()">{{ log.level }}</span>
                <span class="log-message">{{ log.message }}</span>
              </div>
            </div>
            <div class="log-footer">
              <span class="log-auto-scroll">自动滚动已开启</span>
            </div>
          </div>
        </div>

        <!-- Right: Main Content -->
        <div class="main-content">
          <div class="section-card">
            <div class="section-header">
              <div class="section-title">
                <span class="icon">📄</span>
                脚本内容预览
              </div>
            </div>
            <div class="config-form">
              <div class="script-preview-container">
                <pre class="code-block" v-if="scriptContent"><code class="python" v-html="highlightedCode"></code></pre>
                <div class="script-empty" v-else>
                  <div class="script-empty-icon">📄</div>
                  <div class="script-empty-text">暂无脚本内容</div>
                  <div class="script-empty-hint">请先在左侧选择数据源以加载对应的 Python 采集脚本</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Confirm Dialog -->
    <div class="dialog-overlay" v-if="showConfirmDialog" @click.self="closeConfirmDialog">
      <div class="dialog confirm-dialog">
        <div class="dialog-header">
          <div class="dialog-title">{{ confirmDialogTitle }}</div>
          <button class="dialog-close" @click="closeConfirmDialog">&times;</button>
        </div>
        <div class="dialog-body">
          <div class="confirm-icon" :class="confirmDialogType">
            <span v-if="confirmDialogType === 'success'">&#10003;</span>
            <span v-else-if="confirmDialogType === 'error'">&#10007;</span>
            <span v-else>&#8505;</span>
          </div>
          <div class="confirm-message">{{ confirmDialogMessage }}</div>
        </div>
        <div class="dialog-footer">
          <button class="btn" v-if="confirmDialogCallback" @click="closeConfirmDialog">取 消</button>
          <div></div>
          <button class="btn btn-primary" @click="onDialogConfirm">确 认</button>
        </div>
      </div>
    </div>

    <!-- Toast (transient, for less important messages) -->
    <div class="toast" :class="{ show: showToast }">{{ toastMessage }}</div>

    <!-- Category Picker Dialog -->
    <div class="dialog-overlay" v-if="showCategoryPicker" @click.self="showCategoryPicker = false">
      <div class="dialog category-picker-dialog">
        <div class="dialog-header">
          <h3>选择分类</h3>
          <button class="close-btn" @click="showCategoryPicker = false">×</button>
        </div>
        <div class="dialog-body">
          <div v-if="loadingCategories" class="category-loading">加载中...</div>
          <div v-else class="category-picker-tree">
            <template v-for="item in flatCategories" :key="item.id">
              <div
                class="cat-tree-item"
                :style="{ paddingLeft: (item.depth * 20 + 12) + 'px' }"
                :class="{ selected: scriptCategoryId === item.id }"
                @click="selectCategory(item)"
              >
                <span class="cat-node-icon">{{ item.icon }}</span>
                <span class="cat-node-name">{{ item.name }}</span>
              </div>
            </template>
            <div v-if="flatCategories.length === 0" class="category-empty">暂无分类</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { scriptApi, executionApi } from '@/api'
import { datasourceApi } from '@/api/datasource'
import { categoryApi, type Category } from '@/api/category'
import type { CollectionScript } from '@/api'
import hljs from 'highlight.js/lib/core'
import python from 'highlight.js/lib/languages/python'
import 'highlight.js/styles/github-dark.css'

hljs.registerLanguage('python', python)

const route = useRoute()
const router = useRouter()

// Task ID
const scriptId = computed(() => Number(route.params.id))
const isCreateMode = computed(() => route.path.includes('/create'))

// Script data
const script = ref<CollectionScript | null>(null)
const form = reactive({
  scriptName: '',
  source: '',
  description: ''
})
const collectionOnly = ref(false)
const scriptCategoryId = ref<number | null | undefined>(undefined)
const scriptCategoryName = ref('')
const categoryTree = ref<Category[]>([])
const showCategoryPicker = ref(false)
const loadingCategories = ref(false)

const flatCategories = computed(() => {
  const result: Array<Category & { depth: number }> = []
  const walk = (cats: Category[], depth: number) => {
    for (const cat of cats) {
      result.push({ ...cat, depth })
      if (cat.children?.length) {
        walk(cat.children, depth + 1)
      }
    }
  }
  walk(categoryTree.value, 0)
  return result
})

const selectedCategoryIcon = computed(() => {
  if (!scriptCategoryId.value) return ''
  const found = flatCategories.value.find(c => c.id === scriptCategoryId.value)
  return found?.icon || ''
})

async function loadCategoryTree() {
  loadingCategories.value = true
  try {
    const res: any = await categoryApi.tree()
    categoryTree.value = res.data?.data || []
  } catch (e) {
    console.error('加载分类树失败', e)
  } finally {
    loadingCategories.value = false
  }
}

function openCategoryPicker() {
  if (categoryTree.value.length === 0) {
    loadCategoryTree()
  }
  showCategoryPicker.value = true
}

function selectCategory(cat: Category) {
  scriptCategoryId.value = cat.id
  scriptCategoryName.value = cat.name
  showCategoryPicker.value = false
}

function clearCategory() {
  scriptCategoryId.value = undefined
  scriptCategoryName.value = ''
}

// Datasource list
const datasourceList = ref<{ code: string; name: string }[]>([])
const formTriggerType = ref('once')
const triggerTypeText = computed(() => {
  const map: Record<string, string> = {
    once: '单次触发',
    cycle: '周期触发',
    cron: 'Cron 表达式'
  }
  return map[formTriggerType.value] || '未知'
})

// Once trigger
const onceDate = ref('')
const onceHour = ref('08')
const onceMinute = ref('00')
const onceSecond = ref('00')

// Cycle trigger
const cycleType = ref('daily')
const dailyHour = ref('08')
const dailyMinute = ref('00')
const dailySecond = ref('00')
const weeklyDays = ref<number[]>([1])
const weeklyHour = ref('08')
const weeklyMinute = ref('00')
const weeklySecond = ref('00')
const monthlyDay = ref('01')
const monthlyHour = ref('08')
const monthlyMinute = ref('00')
const monthlySecond = ref('00')
const endType = ref('never')
const endDate = ref('')
const endCount = ref(10)

// Cron trigger
const cronExpression = ref('')
const cronNext5 = ref<string[]>([])
const showAdvancedCron = ref(false)
let updatingCron = false // 防止 cycle↔cron 双向同步死循环

// Stats
const stats = reactive({
  executionCount: 0,
  successCount: 0,
  failedCount: 0
})

const executionCount = computed(() => stats.executionCount)
const successCount = computed(() => stats.successCount)
const failedCount = computed(() => stats.failedCount)
const successRate = computed(() => {
  if (stats.executionCount === 0) return 0
  return Math.round((stats.successCount / stats.executionCount) * 100)
})

// Status
const status = ref('enabled')
const executing = ref(false)

// 是否有正在执行的任务
const hasRunningExecution = computed(() => {
  return recentExecutions.value.some((e: any) => e.status === 'running' || e.status === 'pending')
})
const runningExecutionDetail = computed(() => {
  return recentExecutions.value.find((e: any) => e.status === 'running' || e.status === 'pending') || null
})

// Execution results
const showResultCard = ref(false)
const lastResult = ref<'success' | 'failed' | 'cancelled' | ''>('')
const lastDuration = ref('')
const lastCollectedCount = ref(0)
const lastError = ref('')

// Recent executions
const recentExecutions = ref<any[]>([])
const expandedExecId = ref<string | null>(null)

// Realtime log
const showRealtimeLog = ref(false)
const realtimeLogs = ref<{ time: string; level: string; message: string }[]>([])
let logPollingInterval: number | null = null
let autoRefreshInterval: number | null = null

// Script content
const scriptContent = ref('')
const hasChanges = ref(false)
let initialized = false // 防止初始加载时触发 hasChanges
const highlightedCode = computed(() => {
  if (!scriptContent.value) return ''
  return hljs.highlight(scriptContent.value, { language: 'python' }).value
})


// Script dialog
const showScriptDialog = ref(false)

// Toast
const showToast = ref(false)
const toastMessage = ref('')
const showConfirmDialog = ref(false)
const togglingStatus = ref(false)
const confirmDialogTitle = ref('')
const confirmDialogMessage = ref('')
const confirmDialogType = ref<'success' | 'info' | 'error'>('info')
const confirmDialogCallback = ref<(() => void) | null>(null)

function closeConfirmDialog() {
  showConfirmDialog.value = false
  confirmDialogCallback.value = null
  togglingStatus.value = false
}

async function onDialogConfirm() {
  if (confirmDialogCallback.value) {
    const cb = confirmDialogCallback.value
    closeConfirmDialog()
    await cb()
  } else {
    closeConfirmDialog()
  }
}

function showConfirmMessage(title: string, message: string, type: 'success' | 'info' | 'error' = 'info') {
  confirmDialogTitle.value = title
  confirmDialogMessage.value = message
  confirmDialogType.value = type
  confirmDialogCallback.value = null // 没有回调 → 只显示确认按钮
  showConfirmDialog.value = true
}

function showConfirmAction(title: string, message: string, onConfirm: () => void) {
  confirmDialogTitle.value = title
  confirmDialogMessage.value = message
  confirmDialogType.value = 'info'
  confirmDialogCallback.value = onConfirm
  showConfirmDialog.value = true
}

// Task icon and names
const taskIcon = computed(() => '📰')
const sourceName = computed(() => {
  const ds = datasourceList.value.find(d => d.code === form.source)
  return ds?.name || form.source || '-'
})
const nextExecutionText = computed(() => {
  if (!script.value?.nextExecutionTime) return '-'
  const date = new Date(script.value.nextExecutionTime)
  const now = new Date()
  if (date.toDateString() === now.toDateString()) {
    return `今天 ${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`
  }
  return `${date.getMonth() + 1}/${date.getDate()} ${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`
})

const scriptName = computed(() => form.scriptName)
const scriptPath = computed(() => script.value?.scriptPath || '')
const description = computed(() => form.description || '')
const todayStr = computed(() => new Date().toISOString().split('T')[0])

// Refs
const onceDateRef = ref<HTMLInputElement | null>(null)
const endDateRef = ref<HTMLInputElement | null>(null)

// Watch for changes
watch(form, () => {
  if (!initialized) return
  hasChanges.value = true
}, { deep: true })

// Watch trigger config changes to enable save button
watch([
  formTriggerType, onceDate, onceHour, onceMinute, onceSecond,
  cycleType, dailyHour, dailyMinute, dailySecond,
  weeklyDays, weeklyHour, weeklyMinute, weeklySecond,
  monthlyDay, monthlyHour, monthlyMinute, monthlySecond,
  endType, endDate, endCount, cronExpression,
  collectionOnly, scriptCategoryId
], () => {
  if (!initialized) return
  hasChanges.value = true
})


// Resolve category name when ID or tree changes
watch([scriptCategoryId, flatCategories], () => {
  if (scriptCategoryId.value && flatCategories.value.length > 0) {
    const found = flatCategories.value.find(c => c.id === scriptCategoryId.value)
    if (found) scriptCategoryName.value = found.name
  }
})
// Methods
function goBack() {
  router.push('/scripts')
}

function statusText(s: string) {
  const map: Record<string, string> = {
    success: '成功',
    failed: '失败',
    running: '运行中',
    pending: '等待'
  }
  return map[s] || s
}

/** 各阶段耗时在总耗时中的百分比 */
function phasePct(ms: number, totalMs: number): string {
  if (!ms || !totalMs) return '0%'
  return Math.max(6, (ms / totalMs) * 100) + '%'
}

/** 毫秒格式化为可读字符串 */
function fmtMs(ms: number): string {
  if (!ms) return '-'
  if (ms < 1000) return ms + 'ms'
  return (ms / 1000).toFixed(1) + 's'
}

function formatTime(time: string) {
  if (!time) return '-'
  const date = new Date(time)
  return `${date.getMonth() + 1}/${date.getDate()} ${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`
}

async function loadScript() {
  if (isCreateMode.value) {
    // 创建模式：重置表单为默认值
    form.scriptName = ''
    form.source = ''
    form.description = ''
    formTriggerType.value = 'once'
    onceDate.value = ''
    onceHour.value = '08'
    onceMinute.value = '00'
    onceSecond.value = '00'
    cronExpression.value = ''
    status.value = 'enabled'
    collectionOnly.value = false
    hasChanges.value = false
    return
  }
  try {
    const res: any = await scriptApi.getById(scriptId.value)
    script.value = res.data
    form.scriptName = res.data.scriptName || ''
    form.source = res.data.source || ''
    form.description = res.data.description || ''
    collectionOnly.value = !(res.data.syncToKnowledgeBase ?? true)
    scriptCategoryId.value = res.data.categoryId ?? undefined

    stats.executionCount = res.data.executionCount || 0
    stats.successCount = res.data.successCount || 0
    stats.failedCount = res.data.failedCount || 0

    status.value = res.data.status || 'enabled'

    // Parse trigger config
    parseTriggerConfig(res.data)

    // Load script content from datasource (not from task's script_path)
    if (form.source) {
      const versionRes: any = await datasourceApi.getVersions(form.source)
      const versions = versionRes.data || []
      const currentVersion = versions.find((v: any) => v.isCurrent === 1)
      if (currentVersion) {
        const contentRes: any = await datasourceApi.getScriptContent(form.source, currentVersion.version)
        scriptContent.value = contentRes.data || ''
      }
    }
  } catch (e) {
    console.error('加载任务失败', e)
    ElMessage.error('加载任务失败')
  }
}

function parseTriggerConfig(data: any) {
  if (data.triggerType === 'cron' && data.cronExpression) {
    // 尝试将 cron 表达式解析为周期显示（周期触发已在保存时转换为 cron）
    const parsed = parseCronToCycle(data.cronExpression)
    if (parsed) {
      formTriggerType.value = 'cycle'
      cycleType.value = parsed.type as 'daily' | 'weekly' | 'monthly'
      if (parsed.type === 'daily') {
        dailyHour.value = parsed.hour
        dailyMinute.value = parsed.minute
        dailySecond.value = parsed.second
      } else if (parsed.type === 'weekly') {
        weeklyDays.value = parsed.days || [1]
        weeklyHour.value = parsed.hour
        weeklyMinute.value = parsed.minute
        weeklySecond.value = parsed.second
      } else if (parsed.type === 'monthly') {
        monthlyDay.value = parsed.day || '01'
        monthlyHour.value = parsed.hour
        monthlyMinute.value = parsed.minute
        monthlySecond.value = parsed.second
      }
      cronExpression.value = data.cronExpression
    } else {
      // 无法解析为周期 → 折叠在高级 Cron 中显示
      formTriggerType.value = 'cycle'
      showAdvancedCron.value = true
      cronExpression.value = data.cronExpression
    }
    calculateCronNext5()
  } else if (data.triggerType === 'repeat') {
    formTriggerType.value = 'cycle'
    if (data.repeatType === 'daily') {
      cycleType.value = 'daily'
      const time = data.repeatTime?.split(':') || ['08', '00', '00']
      dailyHour.value = time[0] || '08'
      dailyMinute.value = time[1] || '00'
      dailySecond.value = time[2] || '00'
    } else if (data.repeatType === 'weekly') {
      cycleType.value = 'weekly'
      weeklyDays.value = data.weeklyDays?.split(',').map(Number) || [1]
      const time = data.repeatTime?.split(':') || ['08', '00', '00']
      weeklyHour.value = time[0] || '08'
      weeklyMinute.value = time[1] || '00'
      weeklySecond.value = time[2] || '00'
    } else if (data.repeatType === 'monthly') {
      cycleType.value = 'monthly'
      monthlyDay.value = String(data.monthlyDay || 1).padStart(2, '0')
      const time = data.repeatTime?.split(':') || ['08', '00', '00']
      monthlyHour.value = time[0] || '08'
      monthlyMinute.value = time[1] || '00'
      monthlySecond.value = time[2] || '00'
    }
  } else {
    formTriggerType.value = 'once'
    if (data.startTime) {
      const date = new Date(data.startTime)
      onceDate.value = date.toISOString().split('T')[0]
      onceHour.value = String(date.getHours()).padStart(2, '0')
      onceMinute.value = String(date.getMinutes()).padStart(2, '0')
      onceSecond.value = String(date.getSeconds()).padStart(2, '0')
    } else {
      onceDate.value = new Date().toISOString().split('T')[0]
    }
  }
}

async function loadDatasources() {
  try {
    const res: any = await datasourceApi.list()
    datasourceList.value = res.data || []
  } catch (e) {
    console.error('加载数据源失败', e)
  }
}

async function loadRecentExecutions() {
  if (!scriptId.value || isNaN(scriptId.value)) return
  try {
    const res: any = await executionApi.list(scriptId.value, { page: 1, size: 5 })
    recentExecutions.value = res.data?.records || []
  } catch (e) {
    console.error('加载执行记录失败', e)
  }
}

function onTriggerChange() {
  updateTriggerPreview()
}

/** 轻量刷新：只更新统计数据，不重置表单（供 auto-refresh 使用） */
async function refreshScriptStats() {
  if (!scriptId.value || isNaN(scriptId.value)) return
  try {
    const res: any = await scriptApi.getById(scriptId.value)
    stats.executionCount = res.data.executionCount || 0
    stats.successCount = res.data.successCount || 0
    stats.failedCount = res.data.failedCount || 0
    status.value = res.data.status || 'enabled'
    script.value = res.data
  } catch (e) {
    // ignore
  }
}

/** 将周期 UI 参数转为 6 段 Cron 表达式 */
function cycleToCron(): string {
  const pad = (n: string) => n.padStart(2, '0')
  const h = cycleType.value === 'daily' ? dailyHour.value : cycleType.value === 'weekly' ? weeklyHour.value : monthlyHour.value
  const m = cycleType.value === 'daily' ? dailyMinute.value : cycleType.value === 'weekly' ? weeklyMinute.value : monthlyMinute.value
  const s = cycleType.value === 'daily' ? dailySecond.value : cycleType.value === 'weekly' ? weeklySecond.value : monthlySecond.value
  const ss = pad(s), mm = pad(m), hh = pad(h)

  if (cycleType.value === 'daily') return `${ss} ${mm} ${hh} * * ?`
  if (cycleType.value === 'weekly') {
    const days = weeklyDays.value.length ? weeklyDays.value.sort().join(',') : '*'
    return `${ss} ${mm} ${hh} * * ${days}`
  }
  if (cycleType.value === 'monthly') {
    const dd = monthlyDay.value ? pad(monthlyDay.value) : '*'
    return `${ss} ${mm} ${hh} ${dd} * ?`
  }
  return `${ss} ${mm} ${hh} * * ?`
}

function updateTriggerPreview() {
  if (formTriggerType.value === 'cycle') {
    updatingCron = true
    if (cycleType.value === 'weekly' && weeklyDays.value.length === 0) {
      weeklyDays.value = [1, 3, 5] // 默认周一三五
    }
    cronExpression.value = cycleToCron()
    calculateCronNext5()
    updatingCron = false
  }
}

/** 手动编辑 Cron 表达式时的回调 */
function onCronManualEdit() {
  if (updatingCron) return
  calculateCronNext5()
  const parsed = parseCronToCycle(cronExpression.value)
  if (parsed) {
    updatingCron = true
    cycleType.value = parsed.type as any
    if (parsed.type === 'daily') {
      dailyHour.value = parsed.hour; dailyMinute.value = parsed.minute; dailySecond.value = parsed.second
    } else if (parsed.type === 'weekly') {
      weeklyHour.value = parsed.hour; weeklyMinute.value = parsed.minute; weeklySecond.value = parsed.second
      weeklyDays.value = parsed.days || []
    } else if (parsed.type === 'monthly') {
      monthlyHour.value = parsed.hour; monthlyMinute.value = parsed.minute; monthlySecond.value = parsed.second
      monthlyDay.value = parsed.day || '01'
    }
    updatingCron = false
  }
}

function toggleDatePicker(type: string) {
  if (type === 'once' && onceDateRef.value) {
    onceDateRef.value.showPicker?.()
  }
}

function toggleEndDatePicker() {
  endDateRef.value?.showPicker?.()
}

function updateEndDate(e: Event) {
  const target = e.target as HTMLInputElement
  endDate.value = target.value
}

function updateOnceDate(e: Event) {
  const target = e.target as HTMLInputElement
  onceDate.value = target.value
}

/**
 * 将 Cron 表达式解析为周期显示参数
 * 若匹配 daily/weekly/monthly 模式，返回对应参数；否则返回 null
 */
function parseCronToCycle(cron: string): { type: string; hour: string; minute: string; second: string; days?: number[]; day?: string } | null {
  const parts = cron.trim().split(/\s+/)
  if (parts.length < 6) return null

  const second = parts[0]
  const minute = parts[1]
  const hour = parts[2]
  const dayOfMonth = parts[3]
  const month = parts[4]
  const dayOfWeek = parts[5]

  // Daily: 0 MM HH * * ?
  if (dayOfMonth === '*' && month === '*' && (dayOfWeek === '?' || dayOfWeek === '*')) {
    return { type: 'daily', hour: hour.padStart(2, '0'), minute: minute.padStart(2, '0'), second: second.padStart(2, '0') }
  }

  // Weekly: 0 MM HH * * 1,3,5
  if (dayOfMonth === '*' && month === '*' && dayOfWeek !== '?' && dayOfWeek !== '*') {
    const days = dayOfWeek.split(',').map(Number).filter(n => !isNaN(n))
    if (days.length > 0) {
      return { type: 'weekly', hour: hour.padStart(2, '0'), minute: minute.padStart(2, '0'), second: second.padStart(2, '0'), days }
    }
  }

  // Monthly: 0 MM HH DD * ?
  if (dayOfMonth !== '*' && dayOfMonth !== 'L' && month === '*' && (dayOfWeek === '?' || dayOfWeek === '*')) {
    return { type: 'monthly', hour: hour.padStart(2, '0'), minute: minute.padStart(2, '0'), second: second.padStart(2, '0'), day: dayOfMonth.padStart(2, '0') }
  }

  // Monthly last day: 0 MM HH L * ?
  if (dayOfMonth === 'L' && month === '*' && (dayOfWeek === '?' || dayOfWeek === '*')) {
    return { type: 'monthly', hour: hour.padStart(2, '0'), minute: minute.padStart(2, '0'), second: second.padStart(2, '0'), day: 'L' }
  }

  return null
}

function toggleWeekday(day: number) {
  const index = weeklyDays.value.indexOf(day)
  if (index > -1) {
    weeklyDays.value.splice(index, 1)
  } else {
    weeklyDays.value.push(day)
  }
  weeklyDays.value.sort((a, b) => a - b)
  updateTriggerPreview()
}

async function onDatasourceChange() {
  if (!form.source) {
    scriptContent.value = ''
    return
  }
  try {
    const versionRes: any = await datasourceApi.getVersions(form.source)
    const versions = versionRes.data || []
    const currentVersion = versions.find((v: any) => v.isCurrent === 1)
    if (currentVersion) {
      const contentRes: any = await datasourceApi.getScriptContent(form.source, currentVersion.version)
      scriptContent.value = contentRes.data || ''
    }
  } catch (e) {
    console.error('加载脚本内容失败', e)
  }
}

async function calculateCronNext5() {
  if (!cronExpression.value) {
    cronNext5.value = []
    return
  }

  try {
    const res: any = await scriptApi.validateCron(cronExpression.value)
    if (res.data?.valid && res.data?.nextExecutions) {
      const next5: string[] = res.data.nextExecutions
      // Truncate ISO format for display: "2026-05-21T14:30:00+08:00" → "2026-05-21 14:30:00"
      cronNext5.value = next5.map((t: string) => {
        const clean = t.replace('T', ' ').replace('Z', '')
        const idx = clean.indexOf('+')
        return idx > 0 ? clean.substring(0, idx) : clean
      })
    } else {
      cronNext5.value = [res.data?.description || '无效的 Cron 表达式']
    }
  } catch (e) {
    cronNext5.value = ['无法计算']
  }
}

async function doExecute() {
  executing.value = true
  showRealtimeLog.value = true
  showResultCard.value = false
  realtimeLogs.value = []

  try {
    await scriptApi.execute(scriptId.value)
    // Python SDK 会通过 report_start() 创建执行记录
    // 2秒后轮询最新的执行记录
    setTimeout(() => {
      loadRecentExecutions().then(() => {
        if (recentExecutions.value.length > 0) {
          startLogPolling(recentExecutions.value[0].executionId)
        }
      })
    }, 2000)
    showConfirmMessage('执行成功', '脚本已触发执行，可在执行记录中查看实时日志', 'success')
  } catch (e: any) {
    console.error('执行失败', e)
    showConfirmMessage('执行失败', e.message || '执行失败，请检查脚本配置', 'error')
    executing.value = false
  }
}

async function handleExecute() {
  if (executing.value) return
  showConfirmAction('确认执行', '确认立即执行此脚本？', doExecute)
}

function stopLogPolling() {
  if (logPollingInterval) {
    clearInterval(logPollingInterval)
    logPollingInterval = null
  }
}

function startLogPolling(executionId: string) {
  logPollingInterval = window.setInterval(async () => {
    try {
      const [execRes, logsRes] = await Promise.all([
        executionApi.getById(executionId),
        executionApi.logs(executionId)
      ])

      if (logsRes.data) {
        realtimeLogs.value = logsRes.data.map((log: any) => ({
          time: log.timestamp?.substring(11, 19) || '',
          level: log.level || 'INFO',
          message: log.message || ''
        }))
      }

      // Execution completed — refresh stats and stop polling
      const execStatus = execRes.data?.status
      if (execStatus === 'success' || execStatus === 'failed') {
        stopLogPolling()
        executing.value = false
        showRealtimeLog.value = false
        await Promise.all([loadScript(), loadRecentExecutions()])
        ElMessage.success(execStatus === 'success' ? '执行完成' : '执行失败')
        return
      }
    } catch (e) {
      // ignore
    }
  }, 2000)

  // Safety net: stop after 2 minutes
  setTimeout(() => {
    if (!logPollingInterval) return
    stopLogPolling()
    executing.value = false
    showRealtimeLog.value = false
    loadScript()
    loadRecentExecutions()
  }, 120000)
}

async function handleToggleStatus() {
  if (togglingStatus.value) return
  togglingStatus.value = true
  try {
    const newStatus = status.value === 'enabled' ? 'disabled' : 'enabled'
    const msg = newStatus === 'disabled' && hasRunningExecution.value
      ? '有执行中的任务，禁用后正在执行的脚本仍会继续运行，但调度器将不再触发新执行。确认禁用？'
      : newStatus === 'enabled'
        ? '确认启用此脚本？'
        : '确认禁用此脚本？'

    showConfirmAction('确认' + (newStatus === 'enabled' ? '启用' : '禁用'), msg, async () => {
      if (newStatus === 'enabled') {
        await scriptApi.enable(scriptId.value)
      } else {
        await scriptApi.disable(scriptId.value)
      }
      status.value = newStatus
      togglingStatus.value = false
      showConfirmMessage('状态已变更', status.value === 'enabled' ? '脚本已启用，调度器将按触发配置自动执行' : '脚本已禁用，不再触发新执行', 'success')
    })
  } catch (e) {
    console.error('切换状态失败', e)
    ElMessage.error('操作失败')
    togglingStatus.value = false
  }
}

async function saveScript() {
  // Basic validation
  if (!form.scriptName.trim()) {
    ElMessage.error('请输入任务名称')
    return
  }
  if (!form.source) {
    ElMessage.error('请选择数据源')
    return
  }
  if (!collectionOnly.value && !scriptCategoryId.value) {
    ElMessage.error('请选择关联分类')
    return
  }

  // Build trigger config
  let triggerConfig: any = {}
  let triggerType = formTriggerType.value

  if (formTriggerType.value === 'once') {
    if (!onceDate.value) {
      ElMessage.error('请选择触发日期')
      return
    }
    triggerConfig = {
      startTime: `${onceDate.value}T${onceHour.value}:${onceMinute.value}:${onceSecond.value}`
    }
  } else if (formTriggerType.value === 'cycle') {
    triggerConfig = {
      cronExpression: cycleToCron(),
      endType: endType.value !== 'never' ? endType.value : null,
      repeatCount: endType.value === 'count' ? endCount.value : null
    }
    if (endType.value === 'date' && endDate.value) {
      triggerConfig.endTime = endDate.value + 'T23:59:59'
    }
    triggerType = 'cron'
  }

  const payload = {
    scriptName: form.scriptName,
    source: form.source,
    description: form.description ?? undefined,
    syncToKnowledgeBase: !collectionOnly.value,
    categoryId: scriptCategoryId.value ?? undefined,
    triggerType,
    ...triggerConfig
  }

  if (isCreateMode.value) {
    try {
      const res: any = await scriptApi.create(payload)
      ElMessage.success('创建成功')
      // 跳转到新创建的任务详情页
      router.replace(`/scripts/${res.data.id}`)
    } catch (e: any) {
      console.error('创建失败', e)
      ElMessage.error(e.message || '创建失败')
    }
    return
  }

  try {
    await scriptApi.update(scriptId.value, payload)
    // 重新加载脚本数据，刷新 nextExecutionTime 等字段
    await loadScript()
    const msg = triggerType === 'once' ? '保存成功 · 到达设定时间后自动执行' : '保存成功 · 调度器将在下次触发时间自动执行'
    showConfirmMessage('保存成功', msg, 'success')
    hasChanges.value = false
  } catch (e: any) {
    console.error('保存失败', e)
    ElMessage.error(e.message || '保存失败')
  }
}

function saveScriptConfig() {
  saveScript()
}

function viewScript() {
  showScriptDialog.value = true
}

function closeScriptView() {
  showScriptDialog.value = false
}

function viewLogs() {
  router.push(`/scripts/${scriptId.value}/executions/${recentExecutions.value[0]?.executionId}`)
}

function viewExecutionLog(executionId: string) {
  router.push(`/scripts/${scriptId.value}/executions/${executionId}`)
}

function reExecute() {
  showResultCard.value = false
  handleExecute()
}

const showingAllExecutions = ref(false)

async function openExecutionHistory() {
  showingAllExecutions.value = !showingAllExecutions.value
  if (showingAllExecutions.value) {
    try {
      const res: any = await executionApi.list(scriptId.value, { page: 1, size: 100 })
      recentExecutions.value = res.data?.records || []
    } catch (e) {
      console.error('加载全部执行记录失败', e)
    }
  } else {
    await loadRecentExecutions()
  }
}

function goToFullHistory() {
  router.push(`/scripts/${scriptId.value}/executions`)
}

function showToastMessage(msg: string) {
  toastMessage.value = msg
  showToast.value = true
  setTimeout(() => {
    showToast.value = false
  }, 3000)
}

// Lifecycle
onMounted(async () => {
  await loadDatasources()
  await loadCategoryTree()
  await loadScript()
  initialized = true // 初始加载完成，允许 hasChanges 跟踪

  if (isCreateMode.value) return

  await loadRecentExecutions()

  // If there's already a running execution, show realtime log
  const running = recentExecutions.value.find((e: any) => e.status === 'running' || e.status === 'pending')
  if (running) {
    showRealtimeLog.value = true
    executing.value = true
    startLogPolling(running.executionId)
  }

  // Auto-refresh every 15s to detect scheduled executions
  // 只刷新统计数据，不重置表单（不调用 loadScript）
  autoRefreshInterval = window.setInterval(async () => {
    if (document.hidden || executing.value || isCreateMode.value) return
    await loadRecentExecutions()
    await refreshScriptStats()
  }, 15000)
})

onUnmounted(() => {
  stopLogPolling()
  if (autoRefreshInterval) {
    clearInterval(autoRefreshInterval)
  }
})
</script>

<style scoped>
/* CSS Variables - Dark Theme */
.task-detail-page {
  --bg-primary: #0d1117;
  --bg-secondary: #161b22;
  --bg-card: #21262d;
  --bg-hover: #30363d;
  --bg-input: #0d1117;
  --border-color: #30363d;
  --text-primary: #e6edf3;
  --text-secondary: #8b949e;
  --text-muted: #6e7681;
  --accent-blue: #58a6ff;
  --accent-green: #3fb950;
  --accent-orange: #f0883e;
  --accent-purple: #a371f7;
  --accent-red: #f85149;
  --accent-yellow: #d29922;

  font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
  background: var(--bg-primary);
  color: var(--text-primary);
  min-height: 100vh;
  padding-bottom: 40px;
}

/* Header */
.header {
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border-color);
  padding: 8px 24px;
  display: flex;
  align-items: center;
  gap: 10px;
  position: sticky;
  top: 0;
  z-index: 100;
  flex-shrink: 0;
}

.back-btn {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  border: 1px solid var(--border-color);
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  transition: all 0.2s ease;
}

.back-btn:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
  border-color: var(--accent-blue);
}

.header-info {
  flex: 1;
}

.header-title {
  font-size: 14px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
}

.task-icon {
  font-size: 15px;
}

.task-name {
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.header-meta {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 2px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.meta-tag {
  background: var(--bg-card);
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 10px;
}

.meta-desc {
  font-size: 11px;
  color: var(--text-secondary);
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.header-actions {
  display: flex;
  gap: 6px;
}

.btn {
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  gap: 4px;
  border: none;
}

.btn-primary {
  background: linear-gradient(135deg, var(--accent-blue), var(--accent-purple));
  color: #fff;
}

.btn-primary:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(88, 166, 255, 0.3);
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
}

.btn-secondary {
  background: var(--bg-card);
  color: var(--text-primary);
  border: 1px solid var(--border-color);
}

.btn-secondary:hover {
  background: var(--bg-hover);
  border-color: var(--accent-blue);
}

.status-toggle {
  position: relative;
  padding: 5px 14px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  gap: 6px;
  border: 2px solid transparent;
}

.status-toggle.enabled {
  background: rgba(63, 185, 80, 0.15);
  color: var(--accent-green);
  border-color: rgba(63, 185, 80, 0.3);
}

.status-toggle.enabled:hover {
  background: rgba(63, 185, 80, 0.25);
  border-color: var(--accent-green);
  box-shadow: 0 0 20px rgba(63, 185, 80, 0.3);
}

.status-toggle.disabled {
  background: rgba(110, 118, 129, 0.15);
  color: var(--text-muted);
  border-color: rgba(110, 118, 129, 0.3);
}

.status-toggle .toggle-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
  animation: pulse 2s ease-in-out infinite;
}

.status-toggle.disabled .toggle-dot {
  animation: none;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

/* Page Container */
.page-container {
  padding: 8px 24px 0;
  max-width: 100%;
}

/* Stats Bar */
.stats-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 6px;
  flex-wrap: wrap;
}

.stat-chip {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 8px 14px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 120px;
}

.stat-chip-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
}

.stat-chip-icon.total { background: linear-gradient(135deg, var(--accent-blue), var(--accent-purple)); }
.stat-chip-icon.success { background: linear-gradient(135deg, var(--accent-green), #2d9a5a); }
.stat-chip-icon.failed { background: linear-gradient(135deg, var(--accent-red), #c53030); }
.stat-chip-icon.rate { background: linear-gradient(135deg, var(--accent-orange), #d97706); }

.stat-chip-info {
  display: flex;
  flex-direction: column;
}

.stat-chip-value {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
}

.stat-chip-value.success { color: var(--accent-green); }
.stat-chip-value.failed { color: var(--accent-red); }
.stat-chip-value.rate { color: var(--accent-orange); }

.stat-chip-label {
  font-size: 12px;
  color: var(--text-muted);
}

/* Result Card */
.result-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 8px;
}

.result-card.success {
  background: rgba(63, 185, 80, 0.1);
  border: 1px solid rgba(63, 185, 80, 0.3);
}

.result-card.failed {
  background: rgba(248, 81, 73, 0.1);
  border: 1px solid rgba(248, 81, 73, 0.3);
}

.result-card.cancelled {
  background: rgba(240, 136, 62, 0.1);
  border: 1px solid rgba(240, 136, 62, 0.3);
}

.result-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: #fff;
}

.result-card.success .result-icon { background: linear-gradient(135deg, var(--accent-green), #2d9a5a); }
.result-card.failed .result-icon { background: linear-gradient(135deg, var(--accent-red), #c53030); }
.result-card.cancelled .result-icon { background: linear-gradient(135deg, var(--accent-orange), #d97706); }

.result-info {
  flex: 1;
}

.result-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.result-meta {
  font-size: 13px;
  color: var(--text-secondary);
  margin-top: 4px;
}

.result-actions {
  display: flex;
  gap: 8px;
}

.result-btn {
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  border: none;
  background: var(--bg-card);
  color: var(--text-primary);
  transition: all 0.2s ease;
}

.result-btn:hover {
  background: var(--bg-hover);
}

/* Content Grid */
.content-grid {
  display: grid;
  grid-template-columns: minmax(340px, 420px) 1fr;
  gap: 8px;
  align-items: start;
}

.sidebar {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.main-content {
  display: flex;
  flex-direction: column;
}

.main-content .section-card {
  display: flex;
  flex-direction: column;
}

.main-content .section-card .config-form {
  display: flex;
  flex-direction: column;
}

/* Section Card */
.section-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  overflow: hidden;
}

/* Trigger config inside sidebar is naturally constrained by the column */
.sidebar .section-card.trigger-section {
  overflow: visible;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 14px;
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-secondary);
}

.section-title {
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
}

.section-title .icon {
  font-size: 13px;
}

.section-actions {
  display: flex;
  gap: 8px;
}

/* Trigger Config (in sidebar) */
.trigger-type-selector {
  padding: 6px 12px;
  border-bottom: 1px solid var(--border-color);
}

.radio-group {
  display: flex;
  gap: 6px;
}

.radio-option {
  flex: 1;
  position: relative;
}

.radio-option input {
  position: absolute;
  opacity: 0;
  cursor: pointer;
}

.radio-label {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
  padding: 5px 6px;
  background: var(--bg-primary);
  border: 2px solid var(--border-color);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 11px;
  font-weight: 500;
}

.radio-label:hover {
  border-color: var(--text-secondary);
}

.radio-label.disabled {
  opacity: 0.4;
  cursor: not-allowed;
  pointer-events: none;
}

.radio-option input:checked + .radio-label {
  border-color: var(--accent-blue);
  background: rgba(88, 166, 255, 0.1);
  color: var(--accent-blue);
}

.radio-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  border: 2px solid var(--border-color);
  position: relative;
  transition: all 0.2s ease;
  flex-shrink: 0;
}

.radio-option input:checked + .radio-label .radio-dot {
  border-color: var(--accent-blue);
}

.radio-option input:checked + .radio-label .radio-dot::after {
  content: '';
  position: absolute;
  top: 2px;
  left: 2px;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--accent-blue);
}

/* Execution running banner */
.execution-banner {
  margin: 8px 12px;
  padding: 8px 12px;
  background: rgba(240, 136, 62, 0.1);
  border: 1px solid var(--accent-orange);
  border-radius: 6px;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  font-size: 11px;
}
.execution-banner .banner-icon {
  font-size: 13px;
  animation: pulse 1.5s ease-in-out infinite;
}
.execution-banner .banner-text {
  color: var(--accent-orange);
  font-weight: 600;
}
.execution-banner .banner-time {
  color: var(--text-secondary);
}
.execution-banner .banner-hint {
  color: var(--text-muted);
  font-size: 10px;
  width: 100%;
  padding-left: 19px;
}
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.trigger-config {
  margin-top: 6px;
  padding: 0 12px 4px;
}

.trigger-panel {
  display: none;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  padding: 6px;
}

.trigger-panel.active {
  display: block;
}

.hidden {
  display: none !important;
}

/* Date/Time Picker */
.datetime-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.date-picker {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  min-width: 140px;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s ease;
  user-select: none;
}

.date-picker:hover {
  border-color: var(--accent-blue);
  background: #f0f7ff;
}

.date-picker:active {
  border-color: var(--accent-blue);
  background: #e0f0ff;
}

.picker-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--accent-blue);
  flex-shrink: 0;
}

.picker-text {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  flex: 1;
}

.picker-text.placeholder {
  color: var(--text-muted);
  font-weight: 400;
}

.time-picker-inline {
  display: flex;
  align-items: center;
  gap: 4px;
}

.time-select {
  padding: 4px 6px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-primary);
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  cursor: pointer;
}

.time-select.small {
  width: 70px;
}

.time-sep {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-secondary);
}

/* Cycle Options */
.once-summary {
  margin-top: 10px;
  padding: 8px 12px;
  background: rgba(88, 166, 255, 0.08);
  border: 1px solid rgba(88, 166, 255, 0.2);
  border-radius: 6px;
  font-size: 12px;
  color: var(--text-secondary);
}
.once-summary strong {
  color: var(--accent-blue);
  font-family: 'SF Mono', monospace;
}

.cycle-options {
  display: flex;
  gap: 6px;
  margin-bottom: 8px;
}

.cycle-option {
  padding: 5px 10px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.cycle-option:hover {
  border-color: var(--text-secondary);
}

.cycle-option.active {
  background: rgba(88, 166, 255, 0.1);
  border-color: var(--accent-blue);
  color: var(--accent-blue);
}

.sub-options {
  margin-top: 10px;
  padding-left: 10px;
}

.time-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.time-label {
  font-size: 13px;
  color: var(--text-secondary);
}

.weekdays {
  display: flex;
  gap: 6px;
  margin-bottom: 10px;
}

.weekday {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.weekday:hover {
  border-color: var(--text-secondary);
}

.weekday.active {
  background: rgba(88, 166, 255, 0.1);
  border-color: var(--accent-blue);
  color: var(--accent-blue);
}

/* End Condition */
.end-condition {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.end-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.end-options {
  display: flex;
  gap: 6px;
}

.end-option {
  padding: 4px 8px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.end-option:hover {
  border-color: var(--text-secondary);
}

.end-option.active {
  background: rgba(88, 166, 255, 0.1);
  border-color: var(--accent-blue);
  color: var(--accent-blue);
}

.end-sub {
  margin-left: 8px;
}

.end-sub.hidden {
  display: none;
}

/* Cron Input */
.cron-input-row {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.cron-input {
  flex: 1;
  padding: 8px 12px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-primary);
  font-family: 'JetBrains Mono', monospace;
  font-size: 13px;
  letter-spacing: 1px;
}

.cron-input:focus {
  outline: none;
  border-color: var(--accent-blue);
}

.cron-calc-btn {
  padding: 8px 16px;
  background: var(--accent-green);
  border: none;
  border-radius: 6px;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.cron-calc-btn:hover {
  background: #4cc764;
}

.cron-result {
  margin-bottom: 10px;
}

.cron-expr-display {
  font-family: 'JetBrains Mono', monospace;
  font-size: 15px;
  font-weight: 600;
  color: var(--accent-blue);
  letter-spacing: 2px;
}

.cron-next5 {
  background: var(--bg-secondary);
  border-radius: 8px;
  padding: 10px;
}

.next5-title {
  font-size: 11px;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.next5-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.next5-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  background: var(--bg-primary);
  border-radius: 6px;
  font-size: 12px;
}

.next5-item .num {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--accent-blue);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}

.next5-item .time {
  color: var(--text-primary);
  font-family: 'JetBrains Mono', monospace;
}

/* Advanced Cron (collapsible inside cycle panel) */
.advanced-cron-section {
  margin: 8px 12px 4px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  overflow: hidden;
}
.advanced-cron-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  background: var(--bg-secondary);
  cursor: pointer;
  font-size: 11px;
  color: var(--text-secondary);
  user-select: none;
  transition: background 0.2s;
}
.advanced-cron-toggle:hover {
  background: var(--bg-hover);
}
.advanced-cron-toggle .toggle-icon {
  font-size: 9px;
  width: 12px;
  text-align: center;
}
.advanced-badge {
  display: inline-block;
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--accent-purple);
  color: #fff;
  font-size: 9px;
  font-weight: 600;
  vertical-align: middle;
}
.inline-cron {
  margin-left: auto;
  font-family: 'JetBrains Mono', monospace;
  font-size: 10px;
  color: var(--text-muted);
  opacity: 0.7;
}
.advanced-cron-body {
  padding: 10px;
  border-top: 1px solid var(--border-color);
}
.advanced-cron-body.hidden {
  display: none;
}
/* Cron help */
.cron-help {
  margin-top: 10px;
  padding: 8px 10px;
  background: var(--bg-primary);
  border-radius: 6px;
}
.help-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: 6px;
}
.help-grid {
  margin-bottom: 6px;
}
.help-row {
  display: flex;
  gap: 4px;
  margin-bottom: 3px;
}
.help-field, .help-val {
  flex: 1;
  text-align: center;
  font-size: 10px;
  padding: 2px 0;
  border-radius: 3px;
}
.help-field {
  color: var(--text-muted);
  font-weight: 500;
}
.help-val {
  font-family: 'JetBrains Mono', monospace;
  color: var(--accent-blue);
  background: rgba(88, 166, 255, 0.08);
}
.help-notes {
  font-size: 10px;
  color: var(--text-muted);
  line-height: 1.8;
}
.help-notes code {
  background: var(--bg-card);
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 10px;
}
.help-tip {
  margin-top: 4px;
  color: var(--accent-orange);
  font-size: 10px;
}

/* Info Form */
.info-form {
  padding: 6px 12px 0;
}


.form-group {
  margin-bottom: 10px;
}

.form-group:last-child {
  margin-bottom: 0;
}

.info-form .form-group {
  margin-bottom: 6px;
}

.info-form .form-label {
  margin-bottom: 4px;
}

.form-label {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 6px;
  display: block;
}

.sync-toggle-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.sync-hint {
  font-size: 12px;
  color: var(--text-muted);
}

.required-asterisk {
  color: #e74c3c;
  margin-left: 2px;
}

.collection-only-checkbox {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-secondary);
  cursor: pointer;
  user-select: none;
}

.collection-only-checkbox input[type="checkbox"] {
  margin: 0;
}

.category-select-row {
  display: flex;
  align-items: center;
}

.btn-category-pick {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  color: var(--text-primary);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s ease;
  max-width: 100%;
}

.btn-category-pick:hover {
  border-color: var(--accent-blue);
  background: var(--bg-hover);
}

.category-name-display {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.cat-icon {
  font-size: 16px;
  line-height: 1;
}

.category-placeholder {
  color: var(--text-muted);
}

.btn-clear-cat {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: none;
  background: var(--bg-hover);
  color: var(--text-secondary);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s ease;
  line-height: 1;
  margin-left: 4px;
}

.btn-clear-cat:hover {
  background: var(--accent-red);
  color: white;
}

/* Category Picker Dialog */
.category-picker-dialog {
  width: 380px;
  max-width: 90vw;
  max-height: 60vh;
  display: flex;
  flex-direction: column;
}

.category-picker-dialog .dialog-body {
  overflow-y: auto;
  padding: 8px;
}

.category-loading {
  text-align: center;
  padding: 40px 20px;
  color: var(--text-muted);
  font-size: 14px;
}

.category-picker-tree {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.cat-tree-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s ease;
  user-select: none;
}

.cat-tree-item:hover {
  background: var(--bg-hover);
}

.cat-tree-item.selected {
  background: var(--accent-blue);
  color: white;
}

.cat-tree-item.selected .cat-node-name {
  color: white;
}

.cat-node-icon {
  font-size: 16px;
  line-height: 1;
  flex-shrink: 0;
}

.cat-node-name {
  font-size: 13px;
  color: var(--text-primary);
}

.category-empty {
  text-align: center;
  padding: 40px 20px;
  color: var(--text-muted);
  font-size: 14px;
}

.form-input,
.form-select {
  width: 100%;
  padding: 8px 10px;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-primary);
  font-size: 13px;
  transition: all 0.2s ease;
}

.form-input:focus,
.form-select:focus {
  outline: none;
  border-color: var(--accent-blue);
}

.form-input[readonly] {
  color: var(--text-muted);
  cursor: not-allowed;
}

/* Auto-height textarea */
textarea.auto-height {
  field-sizing: content;
  min-height: 32px;
  max-height: 150px;
}

.form-hint {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 4px;
}

/* Execution List */
.execution-list {
  padding: 6px 10px;
}

.execution-item {
  background: var(--bg-primary);
  border-radius: 6px;
  margin-bottom: 6px;
  transition: all 0.2s ease;
  overflow: hidden;
}

.execution-item:last-child {
  margin-bottom: 0;
}

.execution-item:hover {
  background: var(--bg-hover);
}

.execution-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  cursor: pointer;
  user-select: none;
}

.execution-status {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  font-weight: 600;
  min-width: 44px;
}

.execution-status.success { color: var(--accent-green); }
.execution-status.failed { color: var(--accent-red); }
.execution-status.running { color: var(--accent-orange); }

.execution-status .status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
  box-shadow: 0 0 6px currentColor;
}

.execution-status .pulse-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--accent-orange);
  animation: pulse 1.5s ease-in-out infinite;
}

.execution-time {
  font-size: 12px;
  color: var(--text-primary);
  min-width: 110px;
}

.execution-counts {
  font-size: 12px;
  color: var(--text-secondary);
  min-width: 50px;
}

.execution-duration {
  font-size: 12px;
  color: var(--text-muted);
  min-width: 50px;
}

.expand-icon {
  margin-left: auto;
  font-size: 10px;
  color: var(--text-muted);
}

.trigger-badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 3px;
  white-space: nowrap;
}
.trigger-badge.schedule {
  background: rgba(63, 185, 80, 0.15);
  color: var(--accent-green);
}
.trigger-badge.manual {
  background: rgba(88, 166, 255, 0.15);
  color: var(--accent-blue);
}

/* Detail panel */
.execution-detail {
  padding: 0 10px 10px;
  border-top: 1px solid var(--border-color);
  margin: 0 10px;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  padding: 10px 0;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.detail-label {
  font-size: 11px;
  color: var(--text-muted);
}

.detail-value {
  font-size: 12px;
  color: var(--text-primary);
}

/* Phase bar */
.phase-bar {
  padding: 6px 0;
}

.phase-label {
  font-size: 11px;
  color: var(--text-muted);
  margin-bottom: 6px;
}

.phase-row {
  display: flex;
  height: 22px;
  border-radius: 4px;
  overflow: hidden;
  gap: 2px;
}

.phase-chip {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  color: #fff;
  border-radius: 3px;
  min-width: 40px;
  white-space: nowrap;
  padding: 0 4px;
}
.phase-chip.login  { background: #58a6ff; }
.phase-chip.crawl  { background: #3fb950; }
.phase-chip.parse  { background: #a371f7; }
.phase-chip.report { background: #f0883e; }

/* Error */
.detail-error {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 0;
}

.error-label {
  font-size: 11px;
  color: var(--accent-red);
  font-weight: 600;
}

.error-text {
  font-size: 12px;
  color: var(--accent-red);
  word-break: break-all;
}

/* Detail actions */
.detail-actions {
  padding-top: 8px;
  display: flex;
  gap: 8px;
}

.execution-version {
  font-size: 11px;
  color: var(--text-muted);
  background: var(--bg-card);
  padding: 2px 6px;
  border-radius: 4px;
}

.execution-log-btn {
  padding: 4px 8px;
  border-radius: 4px;
  border: none;
  background: var(--bg-card);
  color: var(--text-secondary);
  font-size: 11px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.execution-log-btn:hover {
  background: var(--bg-hover);
  color: var(--accent-blue);
}

.empty-executions {
  padding: 20px;
  text-align: center;
  color: var(--text-muted);
  font-size: 13px;
}

.view-all-link {
  padding: 10px 12px;
  text-align: center;
  border-top: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.view-all-link a {
  color: var(--accent-blue);
  font-size: 12px;
  text-decoration: none;
  cursor: pointer;
}

.view-all-link a:hover {
  text-decoration: underline;
}

.view-all-link .exec-count {
  font-size: 11px;
  color: var(--text-muted);
}

.exec-history-link {
  padding: 4px 12px 10px;
  text-align: center;
}

.exec-history-link .view-all-btn {
  background: none;
  border: none;
  color: var(--accent-blue);
  font-size: 12px;
  cursor: pointer;
  padding: 4px 12px;
  border-radius: 4px;
  transition: all 0.15s ease;
}

.exec-history-link .view-all-btn:hover {
  background: rgba(88, 166, 255, 0.1);
}

.execution-list.expanded {
  max-height: 400px;
  overflow-y: auto;
}

/* Realtime Log */
.log-container {
  padding: 12px 16px;
  max-height: 300px;
  overflow-y: auto;
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  background: var(--bg-primary);
}

.log-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 6px 0;
  border-bottom: 1px solid var(--border-color);
}

.log-item:last-child {
  border-bottom: none;
}

.log-time {
  color: var(--text-muted);
  min-width: 70px;
}

.log-level {
  min-width: 40px;
  font-weight: 600;
}

.log-level.info { color: var(--text-primary); }
.log-level.warn { color: var(--accent-yellow); }
.log-level.error { color: var(--accent-red); }

.log-message {
  color: var(--text-secondary);
  flex: 1;
}

.log-footer {
  padding: 8px 16px;
  border-top: 1px solid var(--border-color);
  background: var(--bg-secondary);
}

.log-auto-scroll {
  font-size: 11px;
  color: var(--text-muted);
}

.log-status {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

.log-status.running {
  background: rgba(240, 136, 62, 0.15);
  color: var(--accent-orange);
}

.spinner {
  width: 12px;
  height: 12px;
  border: 2px solid var(--accent-orange);
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* Config Form */
.config-form {
  padding: 12px;
}

/* Script preview (highlight.js) */
.script-preview-container {
  height: calc(100vh - 280px);
  min-height: 300px;
  overflow: auto;
}

.code-block {
  margin: 0;
  padding: 16px;
  background: #0d1117;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre;
}

.code-block code {
  background: transparent;
  padding: 0;
  font-size: inherit;
  color: inherit;
}

/* Override highlight.js github-dark to match our dark theme */
.code-block :deep(.hljs) {
  background: transparent;
  padding: 0;
}

/* Script empty state */
.script-empty {
  min-height: 300px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: #0d1117;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  color: var(--text-muted);
}

.script-empty-icon {
  font-size: 32px;
  opacity: 0.5;
}

.script-empty-text {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-secondary);
}

.script-empty-hint {
  font-size: 12px;
  color: var(--text-muted);
  max-width: 280px;
  text-align: center;
}

/* Dialog */
.dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.7);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.dialog {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 16px;
  padding: 0;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
}

.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-color);
}

.dialog-title {
  font-size: 16px;
  font-weight: 600;
}

.dialog-close {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: none;
  background: transparent;
  color: var(--text-secondary);
  font-size: 24px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}

.dialog-close:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.dialog-body {
  padding: 20px;
}

.dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-top: 1px solid var(--border-color);
}

.script-view-dialog {
  width: 700px;
  max-width: 90vw;
}

.code-textarea {
  width: 100%;
  padding: 16px;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  color: var(--text-primary);
  font-family: 'JetBrains Mono', monospace;
  font-size: 13px;
  resize: vertical;
  min-height: 400px;
}

/* Toast */
.toast {
  position: fixed;
  bottom: 30px;
  left: 50%;
  transform: translateX(-50%) translateY(100px);
  background: var(--bg-card);
  border: 1px solid var(--accent-green);
  color: var(--accent-green);
  padding: 14px 28px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 500;
  box-shadow: 0 10px 40px rgba(63, 185, 80, 0.3);
  opacity: 0;
  transition: all 0.3s ease;
  z-index: 2000;
}

.toast.show {
  transform: translateX(-50%) translateY(0);
  opacity: 1;
}

/* Responsive */
@media (max-width: 1100px) {
  .content-grid {
    grid-template-columns: 1fr;
    height: auto;
  }

  .sidebar {
    flex-direction: row;
    flex-wrap: wrap;
  }

  .sidebar .section-card {
    flex: 1;
    min-width: 280px;
  }

  .main-content .section-card {
    min-height: 400px;
  }

/* Confirm dialog */
.confirm-dialog {
  width: 400px;
  text-align: center;
}
.confirm-icon {
  font-size: 40px;
  margin-bottom: 12px;
  line-height: 1;
}
.confirm-icon.success { color: var(--accent-green); }
.confirm-icon.error { color: var(--accent-red); }
.confirm-icon.info { color: var(--accent-blue); }
.confirm-message {
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.6;
}
.confirm-dialog .dialog-footer {
  justify-content: center;
}
}
</style>