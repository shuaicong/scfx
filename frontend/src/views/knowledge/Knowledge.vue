<template>
  <div class="knowledge-wrapper">
    <div class="knowledge-container">
      <!-- Left Sidebar -->
      <aside class="sidebar" id="sidebar" :class="{ collapsed: sidebarCollapsed }" :style="sidebarCollapsed ? {} : { width: sidebarWidth + 'px' }">
      <div class="sidebar-resize-handle" @mousedown="startResize" v-if="!sidebarCollapsed"></div>

      <div class="sidebar-tabs">
        <span class="sidebar-tab active">分类</span>
        <button class="sidebar-collapse-btn" @click="toggleSidebar" title="折叠侧边栏">◀</button>
      </div>

      <div class="sidebar-content" id="sidebarTree">
        <!-- CategoryTree Component -->
        <div class="category-tree-section">
          <CategoryTree
            ref="categoryTreeRef"
            :selectedId="selectedCategoryId"
            @select="onCategorySelect"
            @update="onCategoriesUpdate"
          />
        </div>

      </div>

      <div class="sidebar-footer">
        <button class="btn btn-secondary" @click="goToVisualization">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="22,12 18,12 15,21 9,3 6,12 2,12"/>
          </svg>
          可视化
        </button>
      </div>
    </aside>

    <!-- List Area -->
    <section class="list-area" id="listArea" :class="{ 'list-collapsed': listCollapsed }">
      <!-- Expand button (shown when sidebar is collapsed) -->
      <button v-if="sidebarCollapsed" class="sidebar-expand-btn" @click="toggleSidebar" title="展开侧边栏">
        <span class="expand-icon">▶</span>
        <span class="expand-text">展开侧边栏</span>
      </button>
      <button class="list-collapse-btn" @click="toggleList" title="折叠列表">◀</button>

      <!-- Breadcrumb Navigation -->
      <nav class="breadcrumb-nav" v-if="breadcrumbPath.length > 0">
        <span class="breadcrumb-item" @click="clearBreadcrumb">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
            <polyline points="9,22 9,12 15,12 15,22"/>
          </svg>
          全部
        </span>
        <template v-for="(cat, index) in breadcrumbPath" :key="cat.id">
          <span class="breadcrumb-separator">/</span>
          <span
            class="breadcrumb-item"
            :class="{ active: index === breadcrumbPath.length - 1 }"
            @click="navigateToBreadcrumb(cat)"
          >
            <span class="breadcrumb-icon">{{ cat.icon }}</span>
            {{ cat.name }}
          </span>
        </template>
      </nav>

      <!-- Mock Mode Warning Banner -->
      <div v-if="!vectorConfig.enabled" class="mock-warning-banner">
        ⚠️ 向量化服务未配置（Mock 模式），向量数据为模拟数据
      </div>

      <!-- Vectorization Stats -->
      <div class="vector-stats" v-loading="vectorLoading">
        <div class="stat-card stat-card-purple">
          <div class="stat-value">{{ vectorStats.pending }}</div>
          <div class="stat-label">待处理</div>
        </div>
        <div class="stat-card stat-card-blue">
          <div class="stat-value">{{ vectorStats.processing }}</div>
          <div class="stat-label">处理中</div>
        </div>
        <div class="stat-card stat-card-green">
          <div class="stat-value">{{ vectorStats.vectorized }}</div>
          <div class="stat-label">已完成</div>
        </div>
        <div class="stat-card stat-card-red">
          <div class="stat-value">{{ vectorStats.failed }}</div>
          <div class="stat-label">失败</div>
        </div>
        <div class="stats-actions">
          <button class="btn btn-secondary" @click="showTaskDialog = true">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
              <line x1="9" y1="9" x2="15" y2="9"/><line x1="9" y1="13" x2="15" y2="13"/><line x1="9" y1="17" x2="13" y2="17"/>
            </svg>
            任务记录
          </button>
        </div>
      </div>

      <div class="list-header">
        <div class="list-header-left">
          <h2 class="list-title">{{ currentListTitle }}</h2>
          <span class="list-count">{{ pagination.total }} 条</span>
        </div>
        <div class="list-header-right">
          <div class="view-toggle">
            <button :class="{ active: viewMode === 'card' }" @click="switchView('card')">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/>
                <rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/>
              </svg>
              卡片
            </button>
            <button :class="{ active: viewMode === 'table' }" @click="switchView('table')">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="8" y1="6" x2="21" y2="6"/><line x1="8" y1="12" x2="21" y2="12"/>
                <line x1="8" y1="18" x2="21" y2="18"/><line x1="3" y1="6" x2="3.01" y2="6"/>
                <line x1="3" y1="12" x2="3.01" y2="12"/><line x1="3" y1="18" x2="3.01" y2="18"/>
              </svg>
              表格
            </button>
          </div>
          <button class="btn btn-icon btn-secondary" @click="loadData" title="刷新">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="23,4 23,10 17,10"/>
              <polyline points="1,20 1,14 7,14"/>
              <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
            </svg>
          </button>
          <button class="btn btn-primary" @click="showAddDialog = true">+ 新建</button>
        </div>
      </div>

      <!-- Filters Bar - Tag Style -->
      <div class="filters-bar">
        <div class="filter-group search-group">
          <span class="filter-label">搜索:</span>
          <input
            type="text"
            class="search-input"
            v-model="filters.search"
            placeholder="搜索标题/内容..."
            @input="onSearchInput"
            @keydown="onSearchKeydown"
          />
          <el-tooltip
            placement="top"
            :width="260"
            :content="semanticMode
              ? '语义模式已开启，输入关键词后按 Enter 进行语义检索'
              : '根据语义含义匹配内容，而非简单关键词匹配。适合记不清原文精确措辞时的搜索。开启后输入关键词按 Enter 触发'"
            :show-after="500"
          >
            <button
              class="btn btn-sm btn-outline"
              style="margin-left:6px;white-space:nowrap"
              :class="{ active: semanticMode }"
              @click="toggleSemanticSearch"
            >🧠 语义</button>
          </el-tooltip>
        </div>
        <div class="filter-group">
          <span class="filter-label">来源:</span>
          <div class="filter-tags">
            <button
              v-for="source in sources"
              :key="source.key"
              class="filter-tag"
              :class="{ active: filters.sourceType === (source.key === 'all' ? '' : source.key) }"
              @click="selectSourceFilter(source.key, source.name)"
              :title="source.name"
            >
              {{ source.abbr }}
            </button>
          </div>
        </div>
        <div class="filter-group">
          <span class="filter-label">状态:</span>
          <div class="filter-tags">
            <button
              class="filter-tag"
              :class="{ active: !filters.vectorStatus }"
              @click="selectStatusFilter('')"
            >全部</button>
            <button
              class="filter-tag status-vectorized"
              :class="{ active: filters.vectorStatus === 'vectorized' }"
              @click="selectStatusFilter('vectorized')"
            >已向量化</button>
            <button
              class="filter-tag status-pending"
              :class="{ active: filters.vectorStatus === 'pending' }"
              @click="selectStatusFilter('pending')"
            >未向量化</button>
            <button
              class="filter-tag status-processing"
              :class="{ active: filters.vectorStatus === 'processing' }"
              @click="selectStatusFilter('processing')"
            >处理中</button>
            <button
              class="filter-tag status-failed"
              :class="{ active: filters.vectorStatus === 'failed' }"
              @click="selectStatusFilter('failed')"
            >失败</button>
          </div>
        </div>
        <div class="filter-spacer"></div>
        <div class="filter-tag execution-id-filter" v-if="filters.executionId">
          执行: {{ filters.executionId.substring(0, 8) }}...
        </div>
        <button class="filter-clear" :class="{ show: hasFilters }" @click="clearFilters">
          清除筛选
        </button>
      </div>

      <!-- Batch Bar -->
      <div class="batch-bar" v-if="selectedItems.length > 0">
        <div class="batch-bar-left">
          <span>已选择 <strong>{{ selectedItems.length }}</strong> 项</span>
        </div>
        <div style="display: flex; gap: 8px;">
          <button class="btn" @click="batchVectorize">🔄 重向量化</button>
          <button class="btn" @click="batchDelete">🗑️ 删除</button>
          <button class="btn" style="background: transparent; border: none;" @click="clearSelection">✕</button>
        </div>
      </div>

      <!-- Card Grid -->
      <!-- Semantic Search Results -->
      <div class="card-grid" id="cardGrid" v-loading="loading || semanticLoading" v-if="viewMode === 'card' && !previewVisible && !semanticMode">
        <div
          v-for="item in filteredList"
          :key="item.id"
          class="card"
          :class="{ selected: selectedItems.includes(item.id!) }"
          @click="handleCardClick($event, item)"
        >
          <div class="card-checkbox" :class="{ checked: selectedItems.includes(item.id!) }" @click.stop="toggleSelect(item.id!)">
            <span v-if="selectedItems.includes(item.id!)">✓</span>
          </div>
          <div class="card-icon">{{ item.sourceIcon || '📄' }}</div>
          <h3 class="card-title">{{ item.title }}</h3>
          <div class="card-meta">
            <span>{{ getSourceDisplayName(item) }}</span>
            <span>发布于 {{ formatTime(item.publishTime || item.createdAt) }}</span>
          </div>
        </div>
        <div v-if="filteredList.length === 0 && !loading" class="empty-state">
          <span class="empty-icon">📭</span>
          <span class="empty-text">暂无数据</span>
        </div>
      </div>

      <!-- Semantic Search Results -->
      <div class="card-grid" v-loading="semanticLoading" v-if="viewMode === 'card' && !previewVisible && semanticMode">
        <div
          v-for="item in semanticResults"
          :key="item.id"
          class="card semantic-result-card"
          @click="viewSemanticDetail(item)"
        >
          <div class="card-icon">{{ getSourceIcon(item.sourceType) || '📄' }}</div>
          <h3 class="card-title">
            {{ item.title }}
            <span class="score-badge" :class="scoreColor(item.score)">{{ (item.score * 100).toFixed(1) }}%</span>
            <span v-if="item.summaryMatch" class="summary-badge">摘要</span>
          </h3>
          <div class="card-meta">
            <span>{{ item.sourceType }} · {{ item.chunkCount }} 切片</span>
          </div>
          <div class="semantic-snippet" v-html="highlightMatch(item.content, item.matchedStartOffset, item.matchedEndOffset)"></div>
        </div>
        <div v-if="semanticResults.length === 0 && !semanticLoading && semanticSearched" class="empty-state">
          <span class="empty-icon">🔍</span>
          <span class="empty-text">未找到匹配结果</span>
        </div>
        <div v-if="!semanticSearched && !semanticLoading" class="empty-state">
          <span class="empty-icon">🧠</span>
          <span class="empty-text">输入查询词后点击「语义搜索」或按 Enter</span>
        </div>
      </div>

      <!-- Sidebar Mode Compact List -->
      <div class="sidebar-mode-list" v-show="previewVisible">
        <div
          v-for="item in filteredList"
          :key="item.id"
          class="sidebar-mode-item"
          :class="{ active: currentPreview?.id === item.id }"
          @click="viewDetail(item)"
        >
          <div class="item-icon">{{ item.sourceIcon || '📄' }}</div>
          <div class="item-info">
            <div class="item-title">{{ item.title }}</div>
            <div class="item-meta">
              <span class="item-status" :class="item.vectorStatus"></span>
              <span>{{ getSourceDisplayName(item) }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Table View -->
      <div class="table-view" id="tableView" v-if="viewMode === 'table' && !previewVisible">
        <table class="data-table">
          <thead>
            <tr>
              <th style="width: 40px;">
                <div class="checkbox" :class="{ checked: isAllSelected }" @click="toggleSelectAll"></div>
              </th>
              <th>标题</th>
              <th>来源</th>
              <th>状态</th>
              <th>发布日期</th>
			  <th>采集时间</th>
              <th style="width: 120px;">操作</th>
            </tr>
          </thead>
          <tbody id="tableBody">
            <tr v-for="item in filteredList" :key="item.id" @click="handleRowClick(item)">
              <td>
                <div class="checkbox" :class="{ checked: selectedItems.includes(item.id!) }" @click.stop="toggleSelect(item.id!)"></div>
              </td>
              <td>
                <div class="title-cell">
                  <span class="icon">{{ item.sourceIcon || '📄' }}</span>
                  {{ item.title }}
                </div>
              </td>
              <td>{{ getSourceDisplayName(item) }}</td>
              <td>
                <span class="status-badge" :class="item.vectorStatus">
                  {{ getStatusText(item.vectorStatus) }}
                </span>
              </td>
              <td>{{ formatTime(item.publishTime || item.createdAt) }}</td>
			  <td>{{ formatTime(item.createdAt) }}</td>
              <td>
                <div class="actions">
                  <button class="action-btn" @click.stop="viewDetail(item)" title="查看">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                      <circle cx="12" cy="12" r="3"/>
                    </svg>
                  </button>
                  <button class="action-btn" @click.stop="handleRevectorize(item)" title="重向量化">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <polyline points="23,4 23,10 17,10"/>
                      <polyline points="1,20 1,14 7,14"/>
                      <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
                    </svg>
                  </button>
                  <button class="action-btn danger" @click.stop="handleDelete(item)" title="删除">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <polyline points="3,6 5,6 21,6"/>
                      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                    </svg>
                  </button>
                </div>
              </td>
            </tr>
            <tr v-if="filteredList.length === 0 && !loading">
              <td colspan="7" class="empty-cell">暂无数据</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination -->
      <div class="pagination-wrapper">
        <div class="pagination-info">
          显示 {{ pagination.start }}-{{ pagination.end }} 条，共 {{ pagination.total }} 条
        </div>
        <div class="pagination">
          <button :disabled="pagination.page <= 1" @click="pagination.page--; loadData()">◀</button>
          <button
            v-for="p in visiblePages"
            :key="p"
            :class="{ active: p === pagination.page }"
            @click="pagination.page = p; loadData()"
          >{{ p }}</button>
          <button :disabled="pagination.page >= totalPages" @click="pagination.page++; loadData()">▶</button>
        </div>
        <select class="page-size-select" v-model="pagination.size" @change="loadData">
          <option value="20">20 条/页</option>
          <option value="50">50 条/页</option>
          <option value="100">100 条/页</option>
        </select>
      </div>
    </section>

    <!-- Preview Panel -->
    <aside class="preview-panel" id="previewPanel" :class="{ hidden: !previewVisible, fullscreen: previewFullscreen }">
      <div class="preview-header">
        <div class="preview-title">
          <span class="preview-title-icon">{{ currentPreview?.sourceIcon || getSourceIcon(currentPreview?.sourceType) }}</span>
          <span id="previewTitleText">{{ currentPreview?.title || '知识详情' }}</span>
        </div>
        <div class="preview-header-actions">
          <button title="隐藏列表" @click="togglePreviewList">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="3" width="18" height="18" rx="2"/>
              <line x1="9" y1="3" x2="9" y2="21"/>
            </svg>
          </button>
          <button title="新窗口打开" @click="openInNewWindow">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/>
              <polyline points="15,3 21,3 21,9"/>
              <line x1="10" y1="14" x2="21" y2="3"/>
            </svg>
          </button>
          <button title="全屏" @click="toggleFullscreen">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="15,3 21,3 21,9"/>
              <polyline points="9,21 3,21 3,15"/>
              <line x1="21" y1="3" x2="14" y2="10"/>
              <line x1="3" y1="21" x2="10" y2="14"/>
            </svg>
          </button>
          <button title="关闭" @click="closePreview">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/>
              <line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
      </div>

      <div class="preview-meta-grid" v-if="currentPreview">
        <div class="preview-meta-item">
          <div class="label">来源</div>
          <div class="value">{{ currentPreview.sourceIcon }} {{ currentPreview.sourceName || currentPreview.sourceType }}</div>
        </div>
        <div class="preview-meta-item">
          <div class="label">发布日期</div>
          <div class="value">{{ formatTime(currentPreview.publishTime || currentPreview.createdAt) }}</div>
        </div>
        <div class="preview-meta-item">
          <div class="label">文本块</div>
          <div class="value">
            {{ currentPreview.chunkCount || 0 }}
            <el-button text type="primary" size="small" @click="viewChunks(currentPreview.id!, currentPreview.title)" style="margin-left: 4px;">查看</el-button>
          </div>
        </div>
        <div class="preview-meta-item">
          <div class="label">来源类型</div>
          <div class="value">{{ getSourceTypeText(currentPreview.sourceType) }}</div>
        </div>
        <div class="preview-meta-item" v-if="currentPreview.executionId">
          <div class="label">执行 ID</div>
          <div class="value mono">{{ currentPreview.executionId.substring(0, 12) }}...</div>
        </div>
      </div>

      <div class="preview-status-bar" v-if="currentPreview">
        <div
          v-for="status in statusOptions"
          :key="status.value"
          class="status-option"
          :class="{ active: currentPreview.vectorStatus === status.value }"
          @click="changeStatus(status.value)"
        >
          <span class="dot" :style="{ background: status.color }"></span>
          {{ status.label }}
        </div>
      </div>


      <!-- Preview Content: Read-only mode -->
      <div class="preview-content" v-if="currentPreview && !editing" style="display:none">
        <div class="preview-section">
          <div class="preview-section-title">内容摘要</div>
          <!-- docx-preview container -->
          <div v-if="isDocxUpload" class="content-block docx-preview-container">
            <div ref="docxPreviewRef" class="docx-render-area"></div>
          </div>
          <div v-else-if="currentPreview.content || currentPreview.contentHtml" class="content-block">
            <h2>{{ currentPreview.title }}</h2>
            <div v-if="currentPreview.contentHtml" class="content-html" v-html="currentPreview.contentHtml"></div>
            <p v-else>{{ currentPreview.content }}</p>
          </div>
          <div v-else class="content-block">
            <p style="color: var(--text-muted);">暂无内容摘要</p>
          </div>
        </div>

        <div class="preview-section">
          <div class="preview-section-title">关联知识</div>
          <div class="related-list" v-if="relatedItems.length > 0">
            <div
              v-for="item in relatedItems"
              :key="item.id"
              class="related-item"
              @click="viewDetail(item)"
            >
              <span class="icon">{{ item.sourceIcon || '📄' }}</span>
              <div class="info">
                <div class="title">{{ item.title }}</div>
                <div class="meta">相关度 {{ item.score || 90 }}%</div>
              </div>
              <span class="score">{{ item.score || 90 }}%</span>
            </div>
          </div>
          <div v-else class="content-block">
            <p style="color: var(--text-muted);">暂无关联知识</p>
          </div>
        </div>
      </div>

      <!-- Preview Content: Edit mode -->
      <div class="preview-content preview-edit" v-if="currentPreview && editing">
        <div class="preview-section">
          <div class="preview-section-title">编辑知识</div>
          <div class="edit-field">
            <label>标题</label>
            <input type="text" v-model="editForm.title" class="edit-input" />
          </div>
          <div class="edit-field">
            <label>来源类型</label>
            <input type="text" v-model="editForm.sourceType" class="edit-input" />
          </div>
          <div class="edit-field">
            <label>作者</label>
            <input type="text" v-model="editForm.author" class="edit-input" />
          </div>
          <div class="edit-field">
            <label>内容</label>
            <textarea v-model="editForm.content" class="edit-textarea" rows="12"></textarea>
          </div>
        </div>
      </div>

      <div class="preview-footer" v-if="currentPreview">
        <template v-if="editing">
          <button class="btn btn-secondary" @click="cancelEditing">取消</button>
          <button class="btn btn-primary" @click="handleSave" :disabled="saving">{{ saving ? '保存中...' : '保存' }}</button>
        </template>
        <template v-else>
          <button class="btn btn-secondary" @click="startEditing">编辑</button>
          <button class="btn btn-primary" @click="handleRevectorize(currentPreview)" :disabled="currentPreview.revectorizing">
            {{ currentPreview.revectorizing ? '处理中...' : '向量化' }}
          </button>
        </template>
      </div>
    </aside>
  </div>

  <!-- Add Dialog -->
  <div class="modal-overlay" v-if="showAddDialog" @click.self="showAddDialog = false">
    <div class="modal">
      <div class="modal-header">
        <h3>添加知识</h3>
        <span class="modal-category-badge" v-if="selectedCategoryName">当前分类：{{ selectedCategoryName }}</span>
        <button class="modal-close" @click="showAddDialog = false">✕</button>
      </div>
      <div class="modal-tabs">
        <button class="modal-tab" :class="{ active: addTab === 'upload' }" @click="addTab = 'upload'">上传文档</button>
        <button class="modal-tab" :class="{ active: addTab === 'manual' }" @click="addTab = 'manual'">人工录入</button>
      </div>
      <div class="modal-body">
        <div v-if="addTab === 'upload'">
          <div class="form-item">
            <label>所属分类</label>
            <select class="form-select" v-model="dialogCategoryId">
              <option value="">请选择分类</option>
              <option v-for="cat in flatCategories" :key="cat.id" :value="cat.id">{{ cat.name }}</option>
            </select>
          </div>
          <div class="form-item">
            <label>标题</label>
            <input type="text" v-model="uploadForm.title" placeholder="请输入文档标题" />
          </div>
          <div class="form-item">
            <label>来源</label>
            <input type="text" v-model="uploadForm.source" placeholder="如：粮信网" />
          </div>
          <div class="form-item">
            <label>作者</label>
            <input type="text" v-model="uploadForm.author" placeholder="可选" />
          </div>
          <div class="form-item">
            <label>选择文件</label>
            <div class="file-upload" @click="triggerFileInput">
              <input type="file" ref="fileInput" @change="handleFileChange" accept=".pdf,.doc,.docx,.txt,.md" style="opacity: 0; position: absolute; width: 0; height: 0;" />
              <div class="file-upload-icon">📁</div>
              <div class="file-upload-text" v-if="!uploadForm.file">将文件拖到此处，或<em>点击上传</em></div>
              <div class="file-upload-selected" v-else>
                <span class="file-name">{{ uploadForm.file.name }}</span>
                <span class="file-size">({{ (uploadForm.file.size / 1024).toFixed(1) }} KB)</span>
              </div>
              <div class="file-upload-hint">支持 PDF、Word、TXT、Markdown 格式</div>
            </div>
          </div>
        </div>
        <div v-if="addTab === 'manual'">
          <div class="form-item">
            <label>所属分类</label>
            <select class="form-select" v-model="dialogCategoryId">
              <option value="">请选择分类</option>
              <option v-for="cat in flatCategories" :key="cat.id" :value="cat.id">{{ cat.name }}</option>
            </select>
          </div>
          <div class="form-item">
            <label>标题</label>
            <input type="text" v-model="manualForm.title" placeholder="请输入知识标题" />
          </div>
          <div class="form-item">
            <label>内容</label>
            <textarea v-model="manualForm.content" placeholder="请输入知识内容" rows="8"></textarea>
          </div>
          <div class="form-item">
            <label>来源</label>
            <input type="text" v-model="manualForm.source" placeholder="可选，如：内部文档" />
          </div>
          <div class="form-item">
            <label>作者</label>
            <input type="text" v-model="manualForm.author" placeholder="可选" />
          </div>
        </div>
      </div>
      <div class="modal-footer">
        <button class="btn btn-secondary" @click="showAddDialog = false">取消</button>
        <button class="btn btn-primary" @click="addTab === 'upload' ? handleUpload() : handleManualAdd()" :disabled="uploading || submitting">
          {{ uploading || submitting ? '处理中...' : '提交' }}
        </button>
      </div>
    </div>
  </div>

  <!-- Vectorization Task Dialog -->
  <div v-if="showTaskDialog" class="modal-overlay" @click.self="showTaskDialog = false">
    <div class="modal-panel task-dialog">
      <div class="modal-header">
        <h3>向量化任务记录</h3>
        <button class="modal-close" @click="showTaskDialog = false">✕</button>
      </div>
      <div class="modal-body">
        <div class="task-list" v-loading="taskLoading">
          <table class="task-table" v-if="tasks.length > 0">
            <thead>
              <tr>
                <th style="width:30px"></th>
                <th>触发时间</th>
                <th>触发方式</th>
                <th>分类</th>
                <th>进度</th>
                <th>失败</th>
                <th>耗时</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              <template v-for="task in tasks" :key="task.id">
                <tr class="task-row" @click="toggleTaskLogs(task.id)">
                  <td class="task-expand">
                    <span class="expand-arrow" :class="{ expanded: expandedTaskId === task.id }">▶</span>
                  </td>
                  <td class="task-time">{{ formatTime(task.createdAt) }}</td>
                  <td>
                    <span class="task-trigger" :class="'trigger-' + (task.triggerType || 'manual')">
                      {{ triggerLabel(task.triggerType) }}
                    </span>
                  </td>
                  <td>{{ categoryName(task.categoryId) }}</td>
                  <td>{{ task.processedCount ?? 0 }} / {{ task.totalCount ?? 0 }}</td>
                  <td>
                    <span v-if="(task.failedCount ?? 0) > 0" class="task-failed">{{ task.failedCount }}</span>
                    <span v-else class="task-none">-</span>
                  </td>
                  <td class="task-time">{{ duration(task.createdAt, task.completedAt) }}</td>
                  <td>
                    <span class="task-status" :class="'status-' + task.status">
                      {{ statusLabel(task.status) }}
                    </span>
                  </td>
                </tr>
                <tr v-if="expandedTaskId === task.id" class="task-detail-row">
                  <td colspan="8">
                    <div class="task-detail" v-loading="loadingTaskLogs">
                      <table class="task-sub-table" v-if="taskLogsMap[task.id]?.length">
                        <thead>
                          <tr>
                            <th style="width:60px">知识ID</th>
                            <th>标题</th>
                            <th style="width:80px">状态</th>
                            <th style="width:80px">耗时</th>
                            <th>错误信息</th>
                          </tr>
                        </thead>
                        <tbody>
                          <tr v-for="log in taskLogsMap[task.id]" :key="log.knowledgeId">
                            <td>{{ log.knowledgeId }}</td>
                            <td class="task-log-title">{{ log.title || '-' }}</td>
                            <td>
                              <span class="task-status" :class="'status-' + (log.status === 'success' ? 'completed' : log.status)">
                                {{ log.status === 'success' ? '成功' : log.status === 'failed' ? '失败' : log.status }}
                              </span>
                            </td>
                            <td class="task-time">{{ log.processTimeMs != null ? log.processTimeMs + 'ms' : '-' }}</td>
                            <td class="task-error">{{ log.errorMessage || '-' }}</td>
                          </tr>
                        </tbody>
                      </table>
                      <div v-else class="task-empty">暂无详细记录</div>
                    </div>
                  </td>
                </tr>
              </template>
            </tbody>
          </table>
          <div v-else-if="!taskLoading" class="task-empty">暂无任务记录</div>
        </div>
      </div>
      <div class="modal-footer">
        <el-pagination
          v-if="taskTotal > taskPageSize"
          v-model:current-page="taskPage"
          :page-size="taskPageSize"
          :total="taskTotal"
          layout="prev, pager, next"
          small
          @current-change="loadTasks"
        />
        <span v-else style="font-size: 12px; color: #999;">共 {{ taskTotal }} 条</span>
      </div>
    </div>
  </div>
  <!-- 切片查看抽屉 - 时间轴设计 -->
  <el-drawer v-model="chunkDrawerVisible" :title="chunkDrawerTitle" size="520px" destroy-on-close class="chunk-drawer">
    <template #header>
      <div class="chunk-drawer-header">
        <div class="chunk-drawer-title-row">
          <span class="chunk-drawer-icon">◧</span>
          <span class="chunk-drawer-title">{{ chunkDrawerTitle }}</span>
        </div>
        <div class="chunk-drawer-meta" v-if="chunks.length > 0">
          <span class="chunk-meta-item">
            <span class="chunk-meta-dot"></span>
            {{ vectorizedCount }}/{{ chunks.length }} 已向量化
          </span>
          <span class="chunk-meta-divider"></span>
          <span class="chunk-meta-item">{{ totalTokens.toLocaleString() }} tokens</span>
        </div>
      </div>
    </template>

    <div v-if="chunks.length === 0" class="chunk-empty">
      <div class="chunk-empty-icon">▣</div>
      <p class="chunk-empty-text">暂无切片数据</p>
      <p class="chunk-empty-hint">该文档内容较短，无需切片</p>
    </div>
    <div v-else class="chunk-timeline">
      <div
        v-for="(chunk, i) in chunks"
        :key="chunk.id"
        class="chunk-timeline-node"
        :style="{ animationDelay: i * 40 + 'ms' }"
      >
        <!-- 时间轴：圆点 + 连接线 -->
        <div class="chunk-timeline-dot"
          :class="{
            'dot-summary': chunk.isSummary,
            'dot-failed': chunk.vectorStatus === 'failed',
            'dot-vectorized': chunk.vectorStatus === 'vectorized' && !chunk.isSummary
          }"
        ></div>
        <div class="chunk-timeline-line" v-if="i < chunks.length - 1"></div>

        <!-- 卡片 -->
        <div class="chunk-card-custom"
          :class="{
            'card-failed': chunk.vectorStatus === 'failed',
            'card-summary': chunk.isSummary
          }"
        >
          <!-- 顶部：编号徽章 + 标题行 -->
          <div class="chunk-card-top">
            <div class="chunk-badge"
              :class="{
                'badge-failed': chunk.vectorStatus === 'failed',
                'badge-summary': chunk.isSummary
              }"
            >
              <span class="chunk-badge-num">{{ i + 1 }}</span>
              <span v-if="chunk.isSummary" class="chunk-badge-star">★</span>
            </div>
            <div class="chunk-card-info">
              <div class="chunk-card-title-row">
                <span class="chunk-card-label">
                  {{ chunk.isSummary ? '摘要切片' : '内容切片' }}
                </span>
                <span class="chunk-status-chip"
                  :class="'chip-' + chunk.vectorStatus"
                >
                  <span class="chip-icon">
                    {{ chunk.vectorStatus === 'vectorized' ? '✓' : chunk.vectorStatus === 'failed' ? '✗' : '◎' }}
                  </span>
                  {{ chunk.vectorStatus === 'vectorized' ? '已向量化' : chunk.vectorStatus === 'failed' ? '失败' : '待处理' }}
                </span>
              </div>
              <div class="chunk-card-meta">
                <span class="chunk-meta-tokens">{{ chunk.tokenCount ? '~' + chunk.tokenCount + ' tokens' : '--' }}</span>
                <span class="chunk-meta-length" v-if="chunk.content">· {{ chunk.content.length }} 字符</span>
              </div>
            </div>
          </div>

          <!-- 中间：文本内容 -->
          <div class="chunk-card-body">
            <div class="chunk-body-scroll">{{ chunk.content }}</div>
          </div>

          <!-- 错误信息 -->
          <div v-if="chunk.errorMessage" class="chunk-error-bar">
            <span class="chunk-error-icon">⚠</span>
            <span class="chunk-error-text">{{ chunk.errorMessage }}</span>
          </div>
        </div>
      </div>
    </div>
  </el-drawer>
</div>
</template>
<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { knowledgeApi } from '@/api/knowledge'
import { categoryApi, type Category } from '@/api/category'
import { vectorizationApi } from '@/api/vectorization'
import CategoryTree from '@/components/CategoryTree.vue'
import { renderAsync } from 'docx-preview'

const route = useRoute()
const router = useRouter()
interface KnowledgeItem {
  id?: number
  title: string
  sourceName?: string
  sourceType?: string
  sourceIcon?: string
  chunkCount?: number
  vectorStatus?: string
  author?: string
  publishTime?: string
  createdAt?: string
  content?: string
  contentHtml?: string
  fileType?: string
  revectorizing?: boolean
  score?: number
  executionId?: string
  scriptId?: number
}

// Sources
const sources = ref([
  { key: 'all', name: '全部', abbr: 'A', count: 0 },
  { key: 'liangxin', name: '粮信网', abbr: 'L', count: 0 },
  { key: 'mysteel', name: '我的钢铁', abbr: 'M', count: 0 },
  { key: 'chinagrain', name: '中华粮网', abbr: 'C', count: 0 },
  { key: 'usda', name: 'USDA', abbr: 'U', count: 0 },
  { key: 'manual', name: '人工录入', abbr: 'R', count: 0 }
])

// Category selection handlers
const onCategorySelect = (category: any) => {
  selectedCategoryId.value = category.id
  loadKnowledgeByCategory(category.id)
}

const onCategoriesUpdate = (cats: any[]) => {
  // Category tree update callback - can be used to persist changes or sync with backend
  categories.value = cats
}

async function loadKnowledgeByCategory(categoryId: number) {
  loading.value = true
  try {
    const res: any = await knowledgeApi.list({
      page: pagination.page,
      size: pagination.size,
      categoryId: categoryId,
      sourceType: filters.sourceType || undefined,
      vectorStatus: filters.vectorStatus || undefined,
      executionId: filters.executionId || undefined
    })
    const pageData = res.data
    list.value = pageData.records || []
    pagination.total = pageData.total || 0
    pagination.start = (pagination.page - 1) * pagination.size + 1
    pagination.end = Math.min(pagination.page * pagination.size, pagination.total)
  } catch (e) {
    console.error('加载分类知识列表失败', e)
    ElMessage.error('加载知识列表失败')
  } finally {
    loading.value = false
  }
}

// UI State
const sidebarCollapsed = ref(false)
const sidebarWidth = ref(280)
const listCollapsed = ref(false)
const viewMode = ref('table')
const previewVisible = ref(false)
const editing = ref(false)
const previewFullscreen = ref(false)
const sourceDropdownOpen = ref(false)
const statusDropdownOpen = ref(false)

// Sidebar resize
let isResizing = false
let startX = 0
let startWidth = 0

const startResize = (e: MouseEvent) => {
  isResizing = true
  startX = e.clientX
  startWidth = sidebarWidth.value
  document.addEventListener('mousemove', doResize)
  document.addEventListener('mouseup', stopResize)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
}

const doResize = (e: MouseEvent) => {
  if (!isResizing) return
  const diff = e.clientX - startX
  const newWidth = Math.max(200, Math.min(500, startWidth + diff))
  sidebarWidth.value = newWidth
}

const stopResize = () => {
  isResizing = false
  document.removeEventListener('mousemove', doResize)
  document.removeEventListener('mouseup', stopResize)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
}

// Filters
const selectedSource = ref('all')

const filters = reactive({
  sourceType: '',
  sourceName: '',
  vectorStatus: '',
  search: '',
  executionId: ''
})

const hasFilters = computed(() => filters.sourceType || filters.vectorStatus || filters.search || filters.executionId)

// List data
const list = ref<KnowledgeItem[]>([])
const loading = ref(false)
const selectedItems = ref<number[]>([])

// Vectorization stats
const vectorStats = reactive({
  pending: 0,
  processing: 0,
  vectorized: 0,
  failed: 0,
  total: 0
})
const vectorLoading = ref(false)

// Vectorization task list
const tasks = ref<any[]>([])
const taskLoading = ref(false)
const taskPage = ref(1)
const taskPageSize = ref(20)
const taskTotal = ref(0)
const categoryNameMap = ref<Record<number, string>>({})
const expandedTaskId = ref<number | null>(null)
const taskLogsMap = ref<Record<number, any[]>>({})
const loadingTaskLogs = ref(false)

// Vectorization config (mock mode detection)
const vectorConfig = reactive({
  enabled: true,
  mode: 'real'
})

// Stats polling
const statsTimer = ref<ReturnType<typeof setInterval> | null>(null)

// Pagination
const pagination = reactive({
  page: 1,
  size: 20,
  total: 0,
  start: 0,
  end: 0
})

const totalPages = computed(() => Math.ceil(pagination.total / pagination.size) || 1)

const visiblePages = computed(() => {
  const pages: number[] = []
  const total = totalPages.value
  const current = pagination.page
  if (total <= 5) {
    for (let i = 1; i <= total; i++) pages.push(i)
  } else {
    if (current <= 3) {
      pages.push(1, 2, 3, 4, 5)
    } else if (current >= total - 2) {
      pages.push(total - 4, total - 3, total - 2, total - 1, total)
    } else {
      pages.push(current - 2, current - 1, current, current + 1, current + 2)
    }
  }
  return pages
})

const selectedCategoryName = computed(() =>
  selectedCategoryId.value ? categoryName(selectedCategoryId.value) : '')

const isAllSelected = computed(() =>
  list.value.length > 0 && list.value.every(item => selectedItems.value.includes(item.id!))
)

// Current preview
const currentPreview = ref<KnowledgeItem | null>(null)
const relatedItems = ref<KnowledgeItem[]>([])

// docx-preview
const docxPreviewRef = ref<HTMLDivElement>()
const isDocxUpload = computed(() => currentPreview.value?.fileType === 'docx')

async function renderDocxPreview(id: number) {
  await nextTick()
  if (!docxPreviewRef.value) {
    console.warn('docxPreviewRef not found after nextTick')
    return
  }
  try {
    const resp = await fetch(knowledgeApi.getDownloadUrl(id))
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
    const blob = await resp.blob()
    await renderAsync(blob, docxPreviewRef.value)
  } catch (e) {
    console.error('docx preview failed', e)
    ElMessage.error('文档预览加载失败')
  }
}

// Dialogs
const showAddDialog = ref(false)
const showTaskDialog = ref(false)
const addTab = ref('upload')
const fileInput = ref<HTMLInputElement>()

const triggerFileInput = () => {
  fileInput.value?.click()
}

const categoryTreeRef = ref<InstanceType<typeof CategoryTree>>()
const selectedCategoryId = ref<number | undefined>()
const categories = ref<Category[]>([])

// Build breadcrumb path from root to selected category
const flattenCategories = (cats: Category[]): Category[] => {
  return cats.flatMap(c => [c, ...flattenCategories(c.children || [])])
}

const getCategoryPath = (targetId: number | undefined): Category[] => {
  if (!targetId) return []
  const all = flattenCategories(categories.value)
  const path: Category[] = []
  let current = all.find(c => c.id === targetId)
  while (current) {
    path.unshift(current)
    current = current.parentId ? all.find(c => c.id === current!.parentId) : undefined
  }
  return path
}

const breadcrumbPath = computed(() => {
  return getCategoryPath(selectedCategoryId.value)
})

const navigateToBreadcrumb = (category: Category) => {
  selectedCategoryId.value = category.id
  loadKnowledgeByCategory(category.id)
}

const clearBreadcrumb = () => {
  selectedCategoryId.value = undefined
  loadData()
}

const dialogCategoryId = ref<number | ''>('')
const flatCategories = computed(() => {
  const walk = (cats: Category[]): Category[] => cats.flatMap(c => [c, ...walk(c.children || [])])
  return walk(categories.value)
})

// Sync dialogCategoryId from sidebar selection
watch(showAddDialog, (val) => {
  if (val && selectedCategoryId.value) {
    dialogCategoryId.value = selectedCategoryId.value
  } else if (!val) {
    dialogCategoryId.value = ''
  }
})

const uploadForm = reactive({
  title: '',
  source: '',
  author: '',
  file: null as File | null,
})

const manualForm = reactive({
  title: '',
  content: '',
  source: '',
  author: ''
})

const uploading = ref(false)
const submitting = ref(false)
const uploadProgress = ref(0)
const saving = ref(false)

const editForm = reactive({
  title: '',
  content: '',
  sourceType: '',
  author: ''
})

const statusOptions = [
  { value: 'vectorized', label: '已向量化', color: '#3fb950' },
  { value: 'pending', label: '未向量化', color: '#d29922' },
  { value: 'processing', label: '处理中', color: '#58a6ff' },
  { value: 'failed', label: '失败', color: '#f85149' }
]

const currentListTitle = computed(() => {
  if (selectedSource.value !== 'all') {
    return sources.value.find(s => s.key === selectedSource.value)?.name || '知识列表'
  }
  return '知识列表'
})

let searchTimer: ReturnType<typeof setTimeout> | null = null
function onSearchInput() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    loadData()
  }, 300)
}

/** 搜索框按 Enter 触发语义搜索（如果在语义模式下） */
function onSearchKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && semanticMode.value && filters.search.trim()) {
    doSemanticSearch(filters.search.trim())
  }
}

const filteredList = computed(() => {
  const q = filters.search.toLowerCase().trim()
  if (!q) return list.value
  return list.value.filter(item =>
    (item.title && item.title.toLowerCase().includes(q)) ||
    (item.content && item.content.toLowerCase().includes(q))
  )
})

// ======================== 语义搜索 ========================
const semanticMode = ref(false)
const semanticLoading = ref(false)
const semanticSearched = ref(false)
const semanticQuery = ref('')
const semanticResults = ref<import('@/api/knowledge').SearchResult[]>([])

function toggleSemanticSearch() {
  semanticMode.value = !semanticMode.value
  if (!semanticMode.value) {
    // 退出语义搜索模式，回到普通列表
    semanticResults.value = []
    semanticSearched.value = false
  } else if (filters.search.trim()) {
    // 有查询词时立即搜索
    doSemanticSearch(filters.search.trim())
  }
}

async function doSemanticSearch(query: string) {
  if (!query || !selectedCategoryId.value) return
  semanticLoading.value = true
  semanticSearched.value = true
  semanticQuery.value = query
  try {
    const res = await knowledgeApi.search({
      query,
      categoryId: selectedCategoryId.value,
      topK: 20
    })
    semanticResults.value = res.data || []
  } catch (e: any) {
    console.error('语义搜索失败:', e)
    ElMessage.error('语义搜索失败')
    semanticResults.value = []
  } finally {
    semanticLoading.value = false
  }
}

/** 搜索命中文段高亮（利用 matchedStartOffset/matchedEndOffset） */
function highlightMatch(content: string, startOff: number | null, endOff: number | null): string {
  if (!content || startOff == null || endOff == null || startOff >= endOff) {
    // 没有偏移信息或非切片文档，显示前 200 字
    return escapeHtml((content || '').substring(0, 200))
  }
  const before = escapeHtml(content.substring(0, startOff))
  const matched = escapeHtml(content.substring(startOff, endOff))
  const after = escapeHtml(content.substring(endOff, endOff + 200))
  return `${before}<mark class="search-highlight">${matched}</mark>${after}`
}

/** 查看语义搜索结果详情 */
function viewSemanticDetail(item: import('@/api/knowledge').SearchResult) {
  if (!item.id) return
  // 选中该项，触发预览面板展示
  const kbItem: KnowledgeItem = {
    id: item.id,
    title: item.title,
    content: item.content,
    sourceType: item.sourceType,
    score: item.score,
  }
  currentPreview.value = kbItem
  previewVisible.value = true
  updatePreviewRoute(item.id)
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

function scoreColor(score: number): string {
  if (score >= 0.7) return 'score-high'
  if (score >= 0.4) return 'score-mid'
  return 'score-low'
}

// Close dropdowns on click outside
function clickHandler(e: MouseEvent) {
  if (!(e.target as HTMLElement).closest('.filter-dropdown')) {
    sourceDropdownOpen.value = false
    statusDropdownOpen.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', clickHandler)
  // Check for executionId query param (navigate from execution detail)
  const execId = route.query.executionId as string
  if (execId) {
    filters.executionId = execId
  }
  loadData()
  loadVectorStats()
  loadVectorConfig()
  loadCategories()
  // Poll vectorization stats every 10 seconds
  statsTimer.value = setInterval(() => {
    loadVectorStats()
  }, 10000)
})

onUnmounted(() => {
  document.removeEventListener('click', clickHandler)
  if (statsTimer.value) clearInterval(statsTimer.value)
})

// Load categories for name mapping
async function loadCategories() {
  try {
    const res: any = await categoryApi.tree()
    const cats = res.data?.data || []
    initCategoryNameMap(cats)
  } catch { /* ignore */ }
}

// Open task dialog → load tasks
watch(showTaskDialog, (val) => {
  if (val) loadTasks()
})

// Methods
function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

function toggleList() {
  listCollapsed.value = !listCollapsed.value
}

function goToVisualization() {
  router.push('/knowledge/visualization')
}

function switchView(mode: string) {
  viewMode.value = mode
  if (previewVisible.value) {
    closePreview()
  }
}

function selectSource(key: string) {
  selectedSource.value = key
  if (key === 'all') {
    filters.sourceType = ''
  } else {
    filters.sourceType = key
  }
  pagination.page = 1
  loadData()
}

function selectSourceFilter(key: string, name: string) {
  filters.sourceType = key === 'all' ? '' : key
  filters.sourceName = name
  sourceDropdownOpen.value = false
  pagination.page = 1
  loadData()
}

function selectStatusFilter(status: string) {
  filters.vectorStatus = status
  statusDropdownOpen.value = false
  pagination.page = 1
  loadData()
}

function clearFilters() {
  filters.sourceType = ''
  filters.sourceName = ''
  filters.vectorStatus = ''
  filters.search = ''
  filters.executionId = ''
  selectedSource.value = 'all'
  pagination.page = 1
  loadData()
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await knowledgeApi.list({
      page: pagination.page,
      size: pagination.size,
      sourceType: filters.sourceType || undefined,
      vectorStatus: filters.vectorStatus || undefined,
      executionId: filters.executionId || undefined,
      categoryId: selectedCategoryId.value || undefined
    })
    const pageData = res.data
    list.value = pageData.records || []
    pagination.total = pageData.total || 0
    pagination.start = (pagination.page - 1) * pagination.size + 1
    pagination.end = Math.min(pagination.page * pagination.size, pagination.total)

    // Update source counts
    const sourceCounts: Record<string, number> = {}
    list.value.forEach(item => {
      const type = item.sourceType || 'other'
      sourceCounts[type] = (sourceCounts[type] || 0) + 1
    })
    sources.value.forEach(s => {
      if (s.key !== 'all') {
        s.count = sourceCounts[s.key] || 0
      }
    })
  } catch (e) {
    console.error('加载列表失败', e)
    ElMessage.error('加载列表失败')
  } finally {
    loading.value = false
  }
}

async function loadVectorStats() {
  try {
    const res: any = await vectorizationApi.getStats()
    if (res.code === 200 && res.data) {
      vectorStats.pending = res.data.pending || 0
      vectorStats.processing = res.data.processing || 0
      vectorStats.vectorized = res.data.vectorized || 0
      vectorStats.failed = res.data.failed || 0
      vectorStats.total = res.data.total || 0
    }
  } catch (e) {
    console.error('加载向量化统计失败', e)
  }
}

async function loadVectorConfig() {
  try {
    const res: any = await vectorizationApi.getConfig()
    if (res.code === 200 && res.data) {
      vectorConfig.enabled = res.data.enabled
      vectorConfig.mode = res.data.mode
    }
  } catch (e) {
    console.error('加载向量化配置失败', e)
  }
}

// Vectorization task list
async function loadTasks() {
  taskLoading.value = true
  try {
    const res: any = await vectorizationApi.getTasks(taskPage.value, taskPageSize.value)
    if (res.code === 200) {
      tasks.value = res.data?.records || res.data || []
      taskTotal.value = res.data?.total || res.data?.length || 0
    }
  } catch (e) {
    console.error('加载任务记录失败', e)
  } finally {
    taskLoading.value = false
  }
}

async function toggleTaskLogs(taskId: number) {
  if (expandedTaskId.value === taskId) {
    expandedTaskId.value = null
    return
  }
  expandedTaskId.value = taskId
  if (taskLogsMap.value[taskId]) return  // already loaded
  loadingTaskLogs.value = true
  try {
    const res: any = await vectorizationApi.taskLogs(taskId)
    if (res.code === 200) {
      taskLogsMap.value[taskId] = res.data || []
    }
  } catch (e) {
    console.error('加载任务详情失败', e)
  } finally {
    loadingTaskLogs.value = false
  }
}

function triggerLabel(type: string | undefined | null): string {
  const map: Record<string, string> = { manual: '手动', auto: '自动', auto_dirty: '单条触发', cron: '定时' }
  return map[type || ''] || type || '手动'
}

function statusLabel(status: string | undefined | null): string {
  const map: Record<string, string> = { pending: '等待中', processing: '处理中', completed: '已完成' }
  return map[status || ''] || status || '-'
}

function categoryName(id: number | undefined | null): string {
  if (id == null) return '-'
  return categoryNameMap.value[id] || `#${id}`
}

function formatTime(t: string | undefined | null): string {
  if (!t) return '-'
  try {
    const d = new Date(t)
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  } catch { return t }
}

function duration(start: string | undefined | null, end: string | undefined | null): string {
  if (!start || !end) return '-'
  try {
    const ms = new Date(end).getTime() - new Date(start).getTime()
    if (ms < 1000) return `${ms}ms`
    if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
    return `${Math.floor(ms / 60000)}m ${Math.floor((ms % 60000) / 1000)}s`
  } catch { return '-' }
}

function initCategoryNameMap(cats: any[]) {
  const map: Record<number, string> = {}
  const walk = (list: any[]) => {
    for (const c of list) {
      if (c.id != null) map[c.id] = c.name || `#${c.id}`
      if (c.children) walk(c.children)
    }
  }
  walk(cats)
  categoryNameMap.value = map
}

function handleCardClick(event: Event, item: KnowledgeItem) {
  if ((event.target as HTMLElement).closest('.card-checkbox') ||
      (event.target as HTMLElement).closest('.actions')) {
    return
  }
  viewDetail(item)
}

function handleRowClick(row: KnowledgeItem) {
  viewDetail(row)
}

function toggleSelect(id: number) {
  const idx = selectedItems.value.indexOf(id)
  if (idx >= 0) {
    selectedItems.value.splice(idx, 1)
  } else {
    selectedItems.value.push(id)
  }
}

function toggleSelectAll() {
  if (isAllSelected.value) {
    selectedItems.value = []
  } else {
    selectedItems.value = list.value.map(item => item.id!).filter(Boolean)
  }
}

function clearSelection() {
  selectedItems.value = []
}

function viewDetail(item: KnowledgeItem) {
  if (item.id) {
    router.push({ name: 'KnowledgeDetail', params: { id: item.id } })
  }
}

async function handleSave() {
  if (!currentPreview.value?.id) return
  saving.value = true
  try {
    await knowledgeApi.update(currentPreview.value.id, {
      title: editForm.title,
      content: editForm.content,
      sourceType: editForm.sourceType,
      author: editForm.author
    })
    ElMessage.success('保存成功')
    if (currentPreview.value) {
      currentPreview.value.title = editForm.title
      currentPreview.value.content = editForm.content
      currentPreview.value.sourceType = editForm.sourceType
      currentPreview.value.author = editForm.author
    }
    editing.value = false
    loadData()
  } catch (e) {
    console.error('保存失败', e)
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

function startEditing() {
  editForm.title = currentPreview.value?.title || ''
  editForm.content = currentPreview.value?.content || ''
  editForm.sourceType = currentPreview.value?.sourceType || ''
  editForm.author = currentPreview.value?.author || ''
  editing.value = true
}

function cancelEditing() {
  editing.value = false
}

function closePreview() {
  previewVisible.value = false
  editing.value = false
  currentPreview.value = null
}

// 轮询等待向量化完成
function pollVectorizationStatus(id: number): Promise<void> {
  return new Promise((resolve, reject) => {
    const maxAttempts = 30
    let attempts = 0

    const timer = setInterval(async () => {
      attempts++
      try {
        const res: any = await knowledgeApi.getById(id)
        const status = res.data?.vectorStatus

        if (status === 'vectorized') {
          clearInterval(timer)
          resolve()
        } else if (status === 'failed') {
          clearInterval(timer)
          reject(new Error('向量化失败'))
        } else if (attempts >= maxAttempts) {
          clearInterval(timer)
          reject(new Error('向量化超时'))
        }
      } catch {
        clearInterval(timer)
        reject(new Error('向量化状态查询失败'))
      }
    }, 2000)
  })
}

function togglePreviewList() {
  listCollapsed.value = !listCollapsed.value
}

function toggleFullscreen() {
  previewFullscreen.value = !previewFullscreen.value
}

function openInNewWindow() {
  ElMessage.info('新窗口打开功能开发中')
}

async function changeStatus(status: string) {
  if (!currentPreview.value?.id) return
  if (status !== 'pending' && status !== 'failed') {
    ElMessage.info('状态不能手动设置')
    return
  }
  try {
    const id = currentPreview.value.id
    await ElMessageBox.confirm(`确定要${status === 'pending' ? '触发向量化' : '重试向量化'}吗？`, '确认', { type: 'info' })
    currentPreview.value.vectorStatus = 'processing'
    await knowledgeApi.revectorize(id)
    ElMessage.info('正在向量化，请稍候...')
    await pollVectorizationStatus(id)
    ElMessage.success('向量化完成')
    loadData()
  } catch (e: any) {
    if (e !== 'cancel') {
      console.error('向量化失败', e)
      ElMessage.error(e.message || '向量化失败')
    }
  }
}

function getStatusText(status?: string) {
  const map: Record<string, string> = {
    vectorized: '已向量化',
    pending: '未向量化',
    processing: '处理中',
    failed: '失败'
  }
  return map[status || ''] || status || '-'
}

function getSourceTypeText(type?: string) {
  const map: Record<string, string> = {
    liangxin: '粮信网',
    mysteel: '我的钢铁',
    chinagrain: '中华粮网',
    usda: 'USDA',
    manual: '人工录入',
    collection: '采集'
  }
  return map[type || ''] || type || '-'
}

function getSourceIcon(type?: string): string {
  const map: Record<string, string> = {
    liangxin: '🌐',
    mysteel: '📺',
    chinagrain: '🌾',
    usda: '🦃',
    manual: '✏️',
    collection: '📡'
  }
  return map[type || ''] || '📄'
}

function handleFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files && input.files[0]) {
    const file = input.files[0]
    uploadForm.file = file
    if (!uploadForm.title && file.name) {
      uploadForm.title = file.name.replace(/\.(pdf|doc|docx|txt|md)$/i, '')
    }
  }
}

async function handleUpload() {
  if (!uploadForm.title) {
    ElMessage.warning('请输入文档标题')
    return
  }
  if (!uploadForm.file) {
    ElMessage.warning('请选择文件')
    return
  }
  if (!dialogCategoryId.value) {
    ElMessage.warning('请选择分类')
    return
  }

  uploading.value = true
  uploadProgress.value = 0
  try {
    const idempotentKey = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
    await knowledgeApi.uploadDocument(
      uploadForm.file,
      dialogCategoryId.value as number,
      uploadForm.title,
      idempotentKey,
      (pct: number) => { uploadProgress.value = pct }
    )
    ElMessage.success('上传成功')
    showAddDialog.value = false
    loadData()
  } catch (e) {
    console.error('上传失败', e)
    ElMessage.error('上传失败')
  } finally {
    uploading.value = false
  }
}

async function handleManualAdd() {
  if (!manualForm.title) {
    ElMessage.warning('请输入标题')
    return
  }
  if (!manualForm.content) {
    ElMessage.warning('请输入内容')
    return
  }

  submitting.value = true
  try {
    await knowledgeApi.manualAdd({
      title: manualForm.title,
      content: manualForm.content,
      source: manualForm.source || undefined,
      author: manualForm.author || undefined,
      categoryId: dialogCategoryId.value || undefined
    })
    ElMessage.success('添加成功')
    showAddDialog.value = false
    loadData()
  } catch (e) {
    console.error('添加失败', e)
    ElMessage.error('添加失败')
  } finally {
    submitting.value = false
  }
}

async function handleRevectorize(item: KnowledgeItem) {
  if (!item.id) return
  try {
    await ElMessageBox.confirm('确定要重新向量化该知识吗？', '确认', { type: 'info' })
    item.revectorizing = true
    await knowledgeApi.revectorize(item.id)
    ElMessage.info('正在向量化，请稍候...')
    await pollVectorizationStatus(item.id)
    ElMessage.success('向量化完成')
    loadData()
  } catch (e: any) {
    if (e !== 'cancel') {
      console.error('重向量化失败', e)
      ElMessage.error(e.message || '重向量化失败')
    }
  } finally {
    item.revectorizing = false
  }
}

async function handleDelete(item: KnowledgeItem) {
  if (!item.id) return
  try {
    await ElMessageBox.confirm('确定删除该知识吗？删除后无法恢复！', '警告', { type: 'warning' })
    await knowledgeApi.delete(item.id)
    ElMessage.success('删除成功')
    if (currentPreview.value?.id === item.id) {
      closePreview()
    }
    loadData()
    categoryTreeRef.value?.loadTree()
  } catch (e: any) {
    if (e !== 'cancel') {
      console.error('删除失败', e)
      ElMessage.error('删除失败')
    }
  }
}

async function batchVectorize() {
  if (selectedItems.value.length === 0) return
  try {
    await ElMessageBox.confirm(`确定要重向量化选中的 ${selectedItems.value.length} 项知识吗？`, '确认', { type: 'info' })
    for (const id of selectedItems.value) {
      await knowledgeApi.revectorize(id)
    }
    ElMessage.info('正在批量向量化，请稍候...')
    await Promise.all(selectedItems.value.map(id => pollVectorizationStatus(id)))
    ElMessage.success('批量向量化完成')
    clearSelection()
    loadData()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error('批量向量化未全部完成，请重试失败项')
    }
  }
}

async function batchDelete() {
  if (selectedItems.value.length === 0) return
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${selectedItems.value.length} 项知识吗？删除后无法恢复！`, '警告', { type: 'warning' })
    await Promise.all(selectedItems.value.map(id => knowledgeApi.delete(id)))
    ElMessage.success('删除成功')
    clearSelection()
    loadData()
    categoryTreeRef.value?.loadTree()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// ======================== 切片抽屉 ========================

const chunkDrawerVisible = ref(false)
const chunkDrawerTitle = ref('')
const chunks = ref<any[]>([])

async function viewChunks(knowledgeId: number, title: string) {
  chunkDrawerTitle.value = `切片查看 - ${title}`
  chunkDrawerVisible.value = true
  chunks.value = []
  try {
    const res = await knowledgeApi.getChunks(knowledgeId)
    chunks.value = (res as any).data || []
  } catch {
    ElMessage.error('加载切片失败')
    chunks.value = []
  }
}

// ======================== 切片抽屉 Computed ========================

const vectorizedCount = computed(() => chunks.value.filter(c => c.vectorStatus === 'vectorized').length)
const totalTokens = computed(() => chunks.value.reduce((sum, c) => sum + (c.tokenCount || 0), 0))

// Mock data for testing

</script>

<style scoped>
.knowledge-wrapper {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.knowledge-container {
  display: flex;
  height: 100%;
  background: var(--bg-primary);
}

/* Sidebar */
.sidebar {
  width: 260px;
  background: var(--bg-secondary);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  flex-shrink: 0;
  position: relative;
  transition: width 0.3s ease;
}

.sidebar.collapsed {
  width: 0;
  min-width: 0;
  border-right: none;
  overflow: hidden;
}

.sidebar.collapsed > * {
  display: none !important;
}

.sidebar-tabs {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--border-color);
}

.sidebar-tab {
  padding: 6px 12px;
  background: transparent;
  border: none;
  border-radius: 4px;
  color: var(--text-muted);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}

.sidebar-tab:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.sidebar-tab.active {
  background: var(--accent-bg);
  color: var(--accent);
}

.sidebar-collapse-btn {
  margin-left: auto;
  padding: 6px 10px;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  cursor: pointer;
  color: var(--text-muted);
  font-size: 12px;
  transition: all 0.15s;
}

.sidebar-collapse-btn:hover {
  background: var(--accent-bg);
  color: var(--accent);
  border-color: var(--accent);
}

.sidebar-expand-btn {
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-left: none;
  border-radius: 0 8px 8px 0;
  cursor: pointer;
  color: var(--text-muted);
  font-size: 13px;
  box-shadow: 2px 0 8px rgba(0,0,0,0.1);
  transition: all 0.2s;
  z-index: 10;
}

.sidebar-expand-btn:hover {
  background: var(--accent-bg);
  color: var(--accent);
  border-color: var(--accent);
}

.expand-icon {
  font-size: 12px;
}

.expand-text {
  white-space: nowrap;
}

.sidebar-expand-btn:hover {
  background: var(--accent-bg);
  color: var(--accent);
  border-color: var(--accent);
}

.sidebar-resize-handle {
  position: absolute;
  top: 0;
  right: 0;
  width: 4px;
  height: 100%;
  cursor: col-resize;
  background: transparent;
  transition: background 0.2s;
  z-index: 10;
}

.sidebar-resize-handle:hover {
  background: var(--accent);
}

.sidebar-content {
  flex: 1;
  overflow-y: auto;
  padding: 12px 8px;
}

.sidebar-section {
  margin-bottom: 16px;
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px;
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.sidebar-header-btn {
  padding: 2px 6px;
  background: transparent;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  font-size: 14px;
  border-radius: 4px;
}

.sidebar-header-btn:hover {
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

.sidebar-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
  font-size: 13px;
  color: var(--text-secondary);
}

.sidebar-item:hover {
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

.sidebar-item.active {
  background: var(--accent-bg);
  color: var(--accent);
}

.sidebar-item .icon {
  font-size: 15px;
  width: 20px;
  text-align: center;
}

.sidebar-item .name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sidebar-item .count {
  font-size: 11px;
  color: var(--text-muted);
  background: var(--bg-tertiary);
  padding: 2px 6px;
  border-radius: 10px;
}

.tag-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.sidebar-footer {
  padding: 12px;
  border-top: 1px solid var(--border-color);
  display: flex;
  gap: 8px;
}

.sidebar-footer .btn {
  flex: 1;
  justify-content: center;
  font-size: 12px;
  padding: 6px 10px;
}

/* List Area */
.list-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--bg-primary);
  transition: width 0.3s ease;
  position: relative;
  min-width: 0;
}

.list-area.list-collapsed {
  width: 0;
  min-width: 0;
  flex: none;
  overflow: visible;
}

.list-area.list-collapsed > * {
  display: none !important;
}

.list-area.list-collapsed .list-collapse-btn {
  display: flex !important;
  left: -28px;
}

.list-collapse-btn {
  display: none;
  position: absolute;
  top: 50%;
  left: -28px;
  transform: translateY(-50%);
  width: 28px;
  height: 28px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 50%;
  cursor: pointer;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--text-muted);
  z-index: 100;
  box-shadow: 0 2px 8px rgba(0,0,0,0.3);
}

.list-collapse-btn:hover {
  background: var(--accent);
  color: #1a1f2e;
  border-color: var(--accent);
}

.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
}

.list-header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.list-title {
  font-size: 15px;
  font-weight: 600;
}

.list-count {
  font-size: 13px;
  color: var(--text-muted);
}

.list-header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.view-toggle {
  display: flex;
  background: var(--bg-tertiary);
  border-radius: 8px;
  padding: 3px;
}

.view-toggle button {
  padding: 6px 12px;
  border: none;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  font-size: 13px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  gap: 4px;
  transition: all 0.15s;
}

.view-toggle button.active {
  background: var(--accent);
  color: #1a1f2e;
}

.view-toggle button:hover:not(.active) {
  color: var(--text-primary);
}

/* Filters - Tag Style */
.filters-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 8px 20px;
  background: var(--bg-primary);
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
  flex-wrap: wrap;
}

.filter-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-label {
  font-size: 12px;
  color: var(--text-muted);
  white-space: nowrap;
}

.filter-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.search-input {
  padding: 6px 12px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-primary);
  font-size: 13px;
  outline: none;
  width: 180px;
  transition: border-color 0.15s;
}

.search-input:focus {
  border-color: var(--accent);
}

.filter-tag {
  padding: 6px 12px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
  font-weight: 500;
}

.filter-tag:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.filter-tag.active {
  background: var(--accent-bg);
  border-color: var(--accent);
  color: var(--accent);
}

.filter-tag.status-vectorized.active {
  background: rgba(63, 185, 80, 0.15);
  border-color: var(--green);
  color: var(--green);
}

.filter-tag.status-pending.active {
  background: rgba(210, 153, 34, 0.15);
  border-color: var(--yellow);
  color: var(--yellow);
}

.filter-tag.status-processing.active {
  background: rgba(88, 166, 255, 0.15);
  border-color: var(--blue);
  color: var(--blue);
}

.filter-tag.status-failed.active {
  background: rgba(248, 81, 73, 0.15);
  border-color: var(--red);
  color: var(--red);
}

.filter-spacer {
  flex: 1;
}

.filter-clear {
  padding: 4px 10px;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 16px;
  color: var(--text-muted);
  font-size: 12px;
  cursor: pointer;
  opacity: 0;
  transition: all 0.15s;
}

.filter-clear.show {
  opacity: 1;
}

.filter-clear:hover {
  color: var(--accent);
  border-color: var(--accent);
}

.execution-id-filter {
  background: rgba(88, 166, 255, 0.15) !important;
  border-color: var(--blue) !important;
  color: var(--blue) !important;
  font-size: 11px !important;
  cursor: default !important;
}

/* Batch Bar */
.batch-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 20px;
  background: var(--accent-bg);
  border-bottom: 1px solid var(--border-color);
}

.batch-bar-left {
  font-size: 13px;
  color: var(--text-secondary);
}

.batch-bar-left strong {
  color: var(--accent);
}

/* Card Grid */
.card-grid {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 20px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
  align-content: start;
}

.card {
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 16px;
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
  overflow: hidden;
}

.card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--accent), var(--accent-light, #f5c87a));
  opacity: 0;
  transition: opacity 0.2s;
}

.card:hover {
  border-color: var(--accent);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(217, 119, 6, 0.15);
}

.card:hover::before {
  opacity: 1;
}

.card.selected {
  border-color: var(--accent);
  background: var(--accent-bg);
}

.card.selected::before {
  opacity: 1;
}

.card-checkbox {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 20px;
  height: 20px;
  border: 2px solid var(--border-color);
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.15s;
  font-size: 12px;
  color: transparent;
}

.card-checkbox:hover {
  border-color: var(--accent);
}

.card-checkbox.checked {
  background: var(--accent);
  border-color: var(--accent);
  color: #1a1f2e;
}

.card-icon {
  font-size: 28px;
  margin-bottom: 12px;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 8px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.card-meta {
  font-size: 12px;
  color: var(--text-muted);
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}





.empty-state {
  grid-column: 1 / -1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: var(--text-muted);
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 12px;
}

.empty-text {
  font-size: 14px;
}

/* Sidebar Mode List */
.sidebar-mode-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.sidebar-mode-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s;
  border-bottom: 1px solid var(--border-color);
}

.sidebar-mode-item:last-child {
  border-bottom: none;
}

.sidebar-mode-item:hover {
  background: var(--bg-tertiary);
}

.sidebar-mode-item.active {
  background: var(--accent-bg);
}

.sidebar-mode-item .item-icon {
  font-size: 16px;
}

.sidebar-mode-item .item-info {
  flex: 1;
  min-width: 0;
}

.sidebar-mode-item .item-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sidebar-mode-item .item-meta {
  font-size: 11px;
  color: var(--text-muted);
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 2px;
}

.sidebar-mode-item .item-status {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--green);
}

.sidebar-mode-item .item-status.pending {
  background: var(--yellow);
}

.sidebar-mode-item .item-status.processing {
  background: var(--blue);
}

.sidebar-mode-item .item-status.failed {
  background: var(--red);
}

.sidebar-mode-item .item-status.vectorized {
  background: var(--green);
}

/* Table View */
.table-view {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 0 20px 20px;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
}

.data-table th,
.data-table td {
  padding: 8px 12px;
  text-align: left;
  border-bottom: 1px solid var(--border-color);
}

.data-table th {
  background: var(--bg-secondary);
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
}

.data-table td {
  font-size: 13px;
  color: var(--text-secondary);
  height: 44px;
}

.data-table tbody tr {
  cursor: pointer;
  transition: background 0.15s;
}

.data-table tbody tr:hover {
  background: var(--bg-secondary);
}

.empty-cell {
  text-align: center;
  padding: 40px 16px !important;
  color: var(--text-muted);
  font-size: 14px;
}

.title-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.title-cell .icon {
  font-size: 16px;
}

.checkbox {
  width: 18px;
  height: 18px;
  border: 2px solid var(--border-color);
  border-radius: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: transparent;
  transition: all 0.15s;
}

.checkbox:hover {
  border-color: var(--accent);
}

.checkbox.checked {
  background: var(--accent);
  border-color: var(--accent);
  color: #1a1f2e;
}

.status-badge {
  display: inline-block;
  padding: 3px 8px;
  border-radius: 4px;
  font-size: 11px;
}

.status-badge.vectorized {
  background: rgba(63, 185, 80, 0.15);
  color: var(--green);
}

.status-badge.pending {
  background: rgba(210, 153, 34, 0.15);
  color: var(--yellow);
}

.status-badge.processing {
  background: rgba(88, 166, 255, 0.15);
  color: var(--blue);
}

.status-badge.failed {
  background: rgba(248, 81, 73, 0.15);
  color: var(--red);
}


.actions {
  display: flex;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.15s;
}

.data-table tbody tr:hover .actions {
  opacity: 1;
}

.actions button {
  padding: 4px 6px;
  background: transparent;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  border-radius: 4px;
  font-size: 12px;
}

.actions button:hover {
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 4px;
  color: var(--text-muted);
  cursor: pointer;
  transition: all 0.15s;
}

.action-btn:hover {
  background: var(--bg-tertiary);
  border-color: var(--border-color);
  color: var(--accent);
}

.action-btn.danger:hover {
  background: rgba(248, 81, 73, 0.15);
  border-color: var(--red);
  color: var(--red);
}

/* Pagination */
.pagination-wrapper {
  padding: 16px 20px;
  background: var(--bg-secondary);
  border-top: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}

.pagination-info {
  font-size: 13px;
  color: var(--text-muted);
}

.pagination {
  display: flex;
  align-items: center;
  gap: 4px;
}

.pagination button {
  min-width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-secondary);
  cursor: pointer;
  font-size: 13px;
  transition: all 0.15s;
}

.pagination button:hover:not(:disabled) {
  border-color: var(--accent);
  color: var(--accent);
}

.pagination button.active {
  background: var(--accent);
  border-color: var(--accent);
  color: #1a1f2e;
}

.pagination button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-size-select {
  padding: 6px 10px;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-secondary);
  font-size: 13px;
  cursor: pointer;
}

/* Preview Panel */
.preview-panel {
  flex: 1;
  min-width: 360px;
  background: var(--bg-secondary);
  border-left: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: all 0.3s ease;
}

.preview-panel.hidden {
  display: none;
}

.preview-panel.fullscreen {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 1000;
  min-width: 100%;
}

.preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-secondary);
  flex-shrink: 0;
}

.preview-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 600;
  overflow: hidden;
}

.preview-title-icon {
  font-size: 20px;
}

.preview-title span:last-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-header-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.preview-header-actions button {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.15s;
}

.preview-header-actions button:hover {
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

.preview-meta-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  background: var(--border-color);
  flex-shrink: 0;
}

.preview-meta-item {
  background: var(--bg-primary);
  padding: 12px;
  text-align: center;
}

.preview-meta-item .label {
  font-size: 10px;
  color: var(--text-muted);
  text-transform: uppercase;
  margin-bottom: 4px;
}

.preview-meta-item .value {
  font-size: 13px;
  font-weight: 500;
}

.preview-status-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 16px;
  background: var(--bg-primary);
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
}

.status-option {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-muted);
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  transition: all 0.15s;
}

.status-option:hover {
  background: var(--bg-tertiary);
}

.status-option.active {
  color: var(--green);
}

.status-option .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--text-muted);
}

.status-option.active .dot {
  background: var(--green);
  box-shadow: 0 0 6px var(--green);
}

.preview-tags-section {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--border-color);
  flex-wrap: wrap;
  flex-shrink: 0;
}

.preview-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  background: var(--bg-tertiary);
  border-radius: 4px;
  font-size: 11px;
  color: var(--text-secondary);
}

.preview-tag.price {
  background: var(--accent-bg);
  color: var(--accent);
}

.add-tag-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border: 1px dashed var(--border-color);
  border-radius: 4px;
  font-size: 11px;
  color: var(--text-muted);
  cursor: pointer;
  transition: all 0.15s;
}

.add-tag-btn:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.preview-content {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.preview-edit .edit-field {
  margin-bottom: 16px;
}

.preview-edit .edit-field label {
  display: block;
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 6px;
}

.preview-edit .edit-field input,
.preview-edit .edit-field textarea {
  width: 100%;
  padding: 10px 12px;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-primary);
  font-size: 13px;
  transition: border-color 0.15s;
  box-sizing: border-box;
}

.preview-edit .edit-field input:focus,
.preview-edit .edit-field textarea:focus {
  outline: none;
  border-color: var(--accent);
}

.preview-edit .edit-field textarea {
  resize: vertical;
  min-height: 200px;
  font-family: inherit;
  line-height: 1.6;
}

.preview-section {
  margin-bottom: 24px;
}

.preview-section-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.preview-section-title::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--border-color);
}

.content-block {
  background: var(--bg-primary);
  border-radius: 8px;
  padding: 16px;
}

.content-block h2 {
  font-size: 18px;
  font-weight: 600;
  color: var(--accent);
  margin-bottom: 12px;
}

.content-block p {
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-secondary);
  margin-bottom: 10px;
}

.content-html {
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-secondary);
}

.content-html img {
  max-width: 100%;
  height: auto;
  border-radius: 4px;
  margin: 8px 0;
}

.content-html p {
  margin-bottom: 10px;
}

.content-html table {
  border-collapse: collapse;
  width: 100%;
  margin: 12px 0;
  font-size: 13px;
}

.content-html table td,
.content-html table th {
  border: 1px solid var(--border-color, #e5e7eb);
  padding: 8px 10px;
  text-align: left;
}

.content-html table th {
  background: var(--bg-tertiary, #f3f4f6);
  font-weight: 600;
}

.content-html table tr:nth-child(even) {
  background: var(--bg-primary, #fafafa);
}

/* docx-preview */
.docx-preview-container {
  padding: 0;
  overflow: auto;
}
.docx-render-area {
  min-height: 300px;
}
.docx-render-area :deep(.docx-wrapper) {
  padding: 20px;
}
.docx-render-area :deep(.docx-wrapper p) {
  margin-bottom: 8px;
}

.related-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.related-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: var(--bg-primary);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s;
}

.related-item:hover {
  background: var(--bg-tertiary);
}

.related-item .icon {
  font-size: 16px;
}

.related-item .info {
  flex: 1;
  min-width: 0;
}

.related-item .title {
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.related-item .meta {
  font-size: 11px;
  color: var(--text-muted);
}

.related-item .score {
  font-size: 11px;
  color: var(--green);
  background: rgba(63, 185, 80, 0.15);
  padding: 2px 6px;
  border-radius: 4px;
}

.preview-footer {
  padding: 14px 16px;
  border-top: 1px solid var(--border-color);
  display: flex;
  gap: 8px;
  flex-shrink: 0;
  background: var(--bg-secondary);
}

.preview-footer .btn {
  flex: 1;
  justify-content: center;
}

/* Modal */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
}

.modal {
  width: 500px;
  max-width: 90%;
  background: var(--bg-secondary);
  border-radius: 12px;
  overflow: hidden;
}

.modal.modal-lg {
  width: 700px;
}

.modal-panel {
  background: var(--bg-secondary);
  border-radius: 12px;
  overflow: hidden;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-color);
}

.modal-header h3 {
  font-size: 16px;
  font-weight: 600;
}

.modal-category-badge {
  font-size: 12px;
  color: var(--accent);
  background: color-mix(in srgb, var(--accent) 15%, transparent);
  padding: 2px 10px;
  border-radius: 4px;
}

.modal-close {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  border-radius: 6px;
  font-size: 14px;
}

.modal-close:hover {
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

.modal-tabs {
  display: flex;
  border-bottom: 1px solid var(--border-color);
}

.modal-tab {
  flex: 1;
  padding: 12px;
  background: transparent;
  border: none;
  color: var(--text-muted);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}

.modal-tab:hover {
  color: var(--text-secondary);
}

.modal-tab.active {
  color: var(--accent);
  border-bottom: 2px solid var(--accent);
}

.modal-body {
  padding: 20px;
  max-height: 60vh;
  overflow-y: auto;
}

.modal-footer {
  display: flex;
  gap: 8px;
  padding: 16px 20px;
  border-top: 1px solid var(--border-color);
}

.modal-footer .btn {
  flex: 1;
  justify-content: center;
}

/* Form Items */
.form-item {
  margin-bottom: 16px;
}

.form-item label {
  display: block;
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 6px;
}

.form-item input,
.form-item select,
.form-item textarea {
  width: 100%;
  padding: 10px 12px;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-primary);
  font-size: 13px;
  transition: border-color 0.15s;
}

.form-item select {
  cursor: pointer;
  appearance: auto;
}

.form-item input:focus,
.form-item textarea:focus {
  outline: none;
  border-color: var(--accent);
}

.form-item textarea {
  resize: vertical;
  min-height: 100px;
}

.file-upload {
  border: 2px dashed var(--border-color);
  border-radius: 8px;
  padding: 30px;
  text-align: center;
  cursor: pointer;
  transition: all 0.15s;
}

.file-upload:hover {
  border-color: var(--accent);
  background: var(--bg-tertiary);
}

.file-upload-icon {
  font-size: 36px;
  margin-bottom: 10px;
}

.file-upload-text {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.file-upload-text em {
  color: var(--accent);
  font-style: normal;
}

.file-upload-selected {
  font-size: 14px;
  margin-bottom: 6px;
}
.file-upload-selected .file-name {
  color: var(--text-primary);
  font-weight: 500;
}
.file-upload-selected .file-size {
  color: var(--text-secondary);
  font-size: 12px;
  margin-left: 6px;
}

.file-upload-hint {
  font-size: 11px;
  color: var(--text-muted);
}

/* Detail Grid */
.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1px;
  background: var(--border-color);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  overflow: hidden;
}

.detail-item {
  background: var(--bg-primary);
  padding: 12px 16px;
}

.detail-label {
  font-size: 11px;
  color: var(--text-muted);
  margin-bottom: 4px;
}

.detail-value {
  font-size: 13px;
  color: var(--text-primary);
}

.detail-item-full {
  grid-column: 1 / -1;
  background: var(--bg-primary);
  padding: 12px 16px;
}

.edit-input {
  width: 100%;
  padding: 6px 10px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  color: var(--text-primary);
  font-size: 13px;
  outline: none;
  transition: border-color 0.15s;
}

.edit-input:focus {
  border-color: var(--accent);
}

.edit-textarea {
  width: 100%;
  padding: 6px 10px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  color: var(--text-primary);
  font-size: 13px;
  outline: none;
  resize: vertical;
  min-height: 100px;
  font-family: inherit;
  transition: border-color 0.15s;
}

.edit-textarea:focus {
  border-color: var(--accent);
}

.mono {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
}

/* Btn styles matching prototype */
.btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}

.btn-primary {
  background: linear-gradient(135deg, var(--accent), var(--accent-hover));
  color: #1a1f2e;
}

.btn-primary:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(245, 200, 122, 0.35);
}

.btn-secondary {
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  color: var(--text-primary);
}

.btn-secondary:hover {
  background: var(--bg-hover);
  border-color: var(--text-muted);
}

.btn-icon {
  padding: 8px;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* Category Tree Section */
.category-tree-section {
  padding: 8px;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}


/* Breadcrumb Navigation */
.breadcrumb-nav {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 16px;
  background: var(--bg-tertiary, #f8f9fa);
  border-bottom: 1px solid var(--border-color, #e5e7eb);
  font-size: 13px;
}

.breadcrumb-item {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--text-secondary, #6b7280);
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  transition: all 0.2s;
}

.breadcrumb-item:hover {
  background: var(--bg-hover, #f3f4f6);
  color: var(--accent, #d97706);
}

.breadcrumb-item.active {
  color: var(--accent, #d97706);
  font-weight: 500;
  cursor: default;
}

.breadcrumb-item.active:hover {
  background: transparent;
}

.breadcrumb-icon {
  font-size: 12px;
}

.breadcrumb-separator {
  color: var(--text-muted, #9ca3af);
  user-select: none;
}

/* Mock Warning Banner */
.mock-warning-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  margin: 8px 20px 0;
  background: rgba(210, 153, 34, 0.15);
  border: 1px solid var(--yellow, #d29922);
  border-radius: 6px;
  color: var(--yellow, #d29922);
  font-size: 13px;
}

/* Vectorization Stats */
.vector-stats {
  display: flex;
  gap: 16px;
  padding: 16px 20px;
  background: var(--bg-primary);
  border-bottom: 1px solid var(--border-color);
}

.vector-stats .stat-card {
  flex: 1;
  padding: 16px;
  border-radius: 8px;
  color: #fff;
  text-align: center;
}

.stat-card-purple {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.stat-card-blue {
  background: linear-gradient(135deg, #4299e1 0%, #3182ce 100%);
}

.stat-card-green {
  background: linear-gradient(135deg, #48bb78 0%, #38a169 100%);
}

.stat-card-red {
  background: linear-gradient(135deg, #e53e3e 0%, #c53030 100%);
}

.vector-stats .stat-value {
  font-size: 28px;
  font-weight: bold;
  margin-bottom: 4px;
}

.vector-stats .stat-label {
  font-size: 13px;
  opacity: 0.9;
}
.stats-actions {
  display: flex;
  align-items: center;
  padding-left: 12px;
}

/* Task dialog */
.task-dialog {
  max-width: 800px;
}
.task-list {
  min-height: 100px;
}
.task-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.task-table th {
  text-align: left;
  padding: 10px 12px;
  border-bottom: 2px solid var(--border-color);
  font-weight: 600;
  color: var(--text-secondary);
  white-space: nowrap;
}
.task-table td {
  padding: 10px 12px;
  border-bottom: 1px solid var(--border-color);
}
.task-table tbody tr:hover {
  background: var(--bg-hover);
}
.task-time {
  font-family: monospace;
  font-size: 12px;
  color: var(--text-secondary);
  white-space: nowrap;
}
.task-trigger {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}
.trigger-manual { background: #e8f5e9; color: #2e7d32; }
.trigger-auto { background: #e3f2fd; color: #1565c0; }
.trigger-auto_dirty { background: #fff3e0; color: #e65100; }
.trigger-cron { background: #f3e5f5; color: #7b1fa2; }
.task-status {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
}
.status-completed { background: #e8f5e9; color: #2e7d32; }
.status-processing { background: #e3f2fd; color: #1565c0; }
.status-pending { background: #f5f5f5; color: #757575; }
.task-failed {
  display: inline-block;
  background: #ffebee;
  color: #c62828;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}
.task-none { color: #ccc; }
.task-empty {
  text-align: center;
  padding: 40px 0;
  color: #999;
}
.task-row {
  cursor: pointer;
}
.task-row:hover td {
  background: var(--bg-hover, #f5f7fa);
}
.task-expand {
  text-align: center;
}
.expand-arrow {
  display: inline-block;
  font-size: 10px;
  color: #999;
  transition: transform 0.15s;
}
.expand-arrow.expanded {
  transform: rotate(90deg);
}
.task-detail-row td {
  padding: 0 !important;
  background: var(--bg-secondary, #fafbfc);
}
.task-detail {
  padding: 12px 16px 12px 46px;
  min-height: 60px;
}
.task-sub-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.task-sub-table th {
  text-align: left;
  padding: 6px 8px;
  border-bottom: 1px solid var(--border-color);
  font-weight: 600;
  color: var(--text-secondary);
}
.task-sub-table td {
  padding: 6px 8px !important;
  border-bottom: 1px solid var(--border-color);
}
.task-log-title {
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.task-error {
  max-width: 250px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--el-color-danger, #e53e3e);
  font-size: 12px;
}

/* ======================== 切片抽屉 - 时间轴 ======================== */

/* Drawer 容器样式已在非 scoped 块中覆盖（teleport 到 body） */

/* --- 自定义 Header --- */
.chunk-drawer-header {
  width: 100%;
}
.chunk-drawer-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.chunk-drawer-icon {
  font-size: 18px;
  color: var(--accent);
  opacity: 0.8;
}
.chunk-drawer-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.chunk-drawer-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-muted);
}
.chunk-meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
}
.chunk-meta-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent-green, #3fb950);
}
.chunk-meta-divider {
  width: 1px;
  height: 10px;
  background: var(--border-color);
}

/* --- 空状态 --- */
.chunk-empty {
  text-align: center;
  padding: 60px 20px;
}
.chunk-empty-icon {
  font-size: 48px;
  opacity: 0.3;
  margin-bottom: 12px;
  color: var(--text-muted);
}
.chunk-empty-text {
  color: var(--text-secondary);
  font-size: 15px;
  margin: 0 0 4px;
}
.chunk-empty-hint {
  color: var(--text-muted);
  font-size: 12px;
  margin: 0;
}

/* --- 时间轴布局 --- */
.chunk-timeline {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.chunk-timeline-node {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  position: relative;
  padding-left: 24px;
  animation: chunkFadeUp 0.35s ease both;
}

@keyframes chunkFadeUp {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}

/* --- 圆点 --- */
.chunk-timeline-dot {
  position: absolute;
  left: 0;
  top: 18px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--border-color);
  border: 2px solid var(--bg-secondary);
  z-index: 1;
  flex-shrink: 0;
}
.chunk-timeline-dot.dot-vectorized {
  background: var(--accent-green, #3fb950);
}
.chunk-timeline-dot.dot-failed {
  background: var(--accent-red, #f85149);
}
.chunk-timeline-dot.dot-summary {
  background: var(--accent);
  box-shadow: 0 0 6px rgba(245, 200, 122, 0.4);
}

/* --- 连接线 --- */
.chunk-timeline-line {
  position: absolute;
  left: 4px;
  top: 30px;
  width: 2px;
  height: calc(100% - 8px);
  background: linear-gradient(to bottom, var(--border-color), transparent);
  z-index: 0;
}
.chunk-timeline-node:last-child .chunk-timeline-line {
  display: none;
}

/* --- 卡片 --- */
.chunk-card-custom {
  width: 100%;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-left: 3px solid var(--accent);
  border-radius: 10px;
  overflow: hidden;
  margin-bottom: 16px;
  transition: all 0.2s ease;
}
.chunk-card-custom:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(245, 200, 122, 0.12);
  border-color: rgba(245, 200, 122, 0.3);
}
.chunk-card-custom.card-failed {
  border-left-color: var(--accent-red, #f85149);
}
.chunk-card-custom.card-failed:hover {
  box-shadow: 0 4px 16px rgba(248, 81, 73, 0.12);
}
.chunk-card-custom.card-summary {
  border-left-color: var(--accent);
  background: rgba(245, 200, 122, 0.03);
}

/* --- 顶部区域 --- */
.chunk-card-top {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 14px 8px;
}

/* --- 编号徽章 --- */
.chunk-badge {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: var(--bg-secondary);
  border: 1.5px solid var(--border-color);
  flex-shrink: 0;
  position: relative;
}
.chunk-badge-num {
  font-size: 13px;
  font-weight: 700;
  color: var(--text-secondary);
  line-height: 1;
}
.chunk-badge-star {
  font-size: 8px;
  color: var(--accent);
  position: absolute;
  top: -4px;
  right: -4px;
  line-height: 1;
}
.chunk-badge.badge-summary {
  border-color: var(--accent);
  background: rgba(245, 200, 122, 0.1);
}
.chunk-badge.badge-summary .chunk-badge-num {
  color: var(--accent);
}
.chunk-badge.badge-failed {
  border-color: var(--accent-red, #f85149);
}

/* --- 卡片信息区 --- */
.chunk-card-info {
  flex: 1;
  min-width: 0;
}
.chunk-card-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.chunk-card-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

/* --- 状态芯片 --- */
.chunk-status-chip {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  flex-shrink: 0;
  font-weight: 500;
}
.chip-icon {
  font-size: 10px;
}
.chip-vectorized {
  background: rgba(63, 185, 80, 0.12);
  color: var(--accent-green, #3fb950);
}
.chip-failed {
  background: rgba(248, 81, 73, 0.12);
  color: var(--accent-red, #f85149);
}
.chip-pending, .chip-processing {
  background: rgba(210, 153, 34, 0.12);
  color: var(--accent-yellow, #d29922);
}

/* --- 卡片元数据行 --- */
.chunk-card-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 2px;
  font-size: 11px;
  color: var(--text-muted);
}
.chunk-meta-tokens {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 11px;
}
.chunk-meta-length {
  font-size: 11px;
}

/* --- 内容区 --- */
.chunk-card-body {
  position: relative;
  margin: 0 14px 10px;
}
.chunk-body-scroll {
  max-height: 120px;
  overflow-y: auto;
  padding: 10px 12px;
  background: rgba(0, 0, 0, 0.2);
  border-radius: 6px;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12px;
  line-height: 1.55;
  color: var(--text-secondary);
  white-space: pre-wrap;
  word-break: break-all;
}
.chunk-card-body::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 28px;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.2));
  pointer-events: none;
  border-radius: 0 0 6px 6px;
}

/* --- 错误信息 --- */
.chunk-error-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: rgba(248, 81, 73, 0.08);
  border-top: 1px solid rgba(248, 81, 73, 0.15);
  font-size: 12px;
  color: var(--accent-red, #f85149);
}
.chunk-error-icon {
  font-size: 13px;
  flex-shrink: 0;
}
.chunk-error-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ============ 语义搜索 ============ */
.semantic-result-card {
  cursor: pointer;
  border-left: 3px solid #409eff;
}
.semantic-result-card:hover {
  border-color: #66b1ff;
  background: #f0f9ff;
}
.score-badge {
  display: inline-block;
  font-size: 11px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 8px;
  margin-left: 6px;
  vertical-align: middle;
}
.score-high { background: #e1f3d8; color: #2b7a2b; }
.score-mid  { background: #fef0db; color: #b8821a; }
.score-low  { background: #fde2e2; color: #c0392b; }
.summary-badge {
  display: inline-block;
  font-size: 10px;
  padding: 0 5px;
  border-radius: 4px;
  background: #ecf5ff;
  color: #409eff;
  margin-left: 4px;
  vertical-align: middle;
}
.semantic-snippet {
  font-size: 12px;
  color: #666;
  margin-top: 4px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.search-highlight {
  background: #fff3b0;
  color: #333;
  padding: 0 2px;
  border-radius: 2px;
}
.btn-sm {
  padding: 3px 10px;
  font-size: 12px;
  line-height: 1.5;
  border-radius: 4px;
}
.btn-outline {
  background: transparent;
  border: 1px solid #dcdfe6;
  color: #606266;
  cursor: pointer;
  transition: all .2s;
}
.btn-outline:hover { color: #409eff; border-color: #c6e2ff; background: #ecf5ff; }
.btn-outline.active { color: #fff; background: #409eff; border-color: #409eff; }
</style>

<!-- 非 scoped：抽屉容器被 teleport 到 body，scoped CSS 无法覆盖 -->
<style>
.chunk-drawer.el-drawer {
  background: var(--bg-secondary) !important;
  border-left: 1px solid rgba(255, 255, 255, 0.08) !important;
}
.chunk-drawer .el-drawer__header {
  padding: 16px 20px 12px;
  margin-bottom: 0;
  border-bottom: 1px solid var(--border-color);
}
.chunk-drawer .el-drawer__title {
  display: none;
}
.chunk-drawer .el-drawer__body {
  padding: 16px 20px 24px;
  overflow-y: auto;
}
</style>
