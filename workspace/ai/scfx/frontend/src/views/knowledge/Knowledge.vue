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
        <button class="btn btn-secondary" @click="showMappingDialog = true">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 2L2 7l10 5 10-5-10-5z"/>
            <path d="M2 17l10 5 10-5"/>
            <path d="M2 12l10 5 10-5"/>
          </svg>
          映射配置
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
      </div>

      <div class="list-header">
        <div class="list-header-left">
          <h2 class="list-title">{{ currentListTitle }}</h2>
          <span class="list-count">{{ pagination.total }} 条</span>
        </div>
        <div class="list-header-right">
          <div class="view-toggle">
            <button :class="{ active: viewMode === 'card' }" @click="viewMode = 'card'">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/>
                <rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/>
              </svg>
              卡片
            </button>
            <button :class="{ active: viewMode === 'table' }" @click="viewMode = 'table'">
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
          <button class="btn btn-secondary" @click="goToVisualization">可视化</button>
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
          />
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
      <div class="card-grid" id="cardGrid" v-loading="loading" v-show="viewMode === 'card' && !previewVisible">
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
            <span>{{ item.sourceName || item.sourceType }}</span>
            <span>{{ item.publishTime || item.createdAt }}</span>
          </div>
          <div class="card-tags" v-if="item.tags?.length">
            <span v-for="tag in item.tags" :key="tag" class="card-tag" :class="tag">{{ tag }}</span>
          </div>
        </div>
        <div v-if="filteredList.length === 0 && !loading" class="empty-state">
          <span class="empty-icon">📭</span>
          <span class="empty-text">暂无数据</span>
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
              <span>{{ item.sourceName || item.sourceType }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Table View -->
      <div class="table-view" id="tableView" v-show="viewMode === 'table'">
        <table class="data-table">
          <thead>
            <tr>
              <th style="width: 40px;">
                <div class="checkbox" :class="{ checked: isAllSelected }" @click="toggleSelectAll"></div>
              </th>
              <th>标题</th>
              <th>来源</th>
              <th>状态</th>
              <th>标签</th>
              <th>日期</th>
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
              <td>{{ item.sourceName || item.sourceType }}</td>
              <td>
                <span class="status-badge" :class="item.vectorStatus">
                  {{ getStatusText(item.vectorStatus) }}
                </span>
              </td>
              <td>
                <div class="table-tags" v-if="item.tags?.length">
                  <span v-for="tag in item.tags" :key="tag" class="card-tag" :class="tag">{{ tag }}</span>
                </div>
              </td>
              <td>{{ item.publishTime || item.createdAt }}</td>
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
          <div class="label">日期</div>
          <div class="value">{{ currentPreview.publishTime || currentPreview.createdAt || '-' }}</div>
        </div>
        <div class="preview-meta-item">
          <div class="label">文本块</div>
          <div class="value">{{ currentPreview.chunkCount || 0 }}</div>
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

      <div class="preview-tags-section" v-if="currentPreview?.tags?.length">
        <span v-for="tag in currentPreview.tags" :key="tag" class="preview-tag" :class="tag">{{ tag }}</span>
        <span class="add-tag-btn">+ 添加标签</span>
      </div>

      <div class="preview-content" v-if="currentPreview">
        <div class="preview-section">
          <div class="preview-section-title">内容摘要</div>
          <div class="content-block" v-if="currentPreview.content || currentPreview.contentHtml">
            <h2>{{ currentPreview.title }}</h2>
            <div v-if="currentPreview.contentHtml" class="content-html" v-html="currentPreview.contentHtml"></div>
            <p v-else>{{ currentPreview.content }}</p>
          </div>
          <div class="content-block" v-else>
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

      <div class="preview-footer" v-if="currentPreview">
        <button class="btn btn-secondary" @click="showDetailDialog = true">编辑</button>
        <button class="btn btn-primary" @click="handleRevectorize(currentPreview)" :disabled="currentPreview.revectorizing">
          {{ currentPreview.revectorizing ? '处理中...' : '向量化' }}
        </button>
      </div>
    </aside>
  </div>

  <!-- Add Dialog -->
  <div class="modal-overlay" v-if="showAddDialog" @click.self="showAddDialog = false">
    <div class="modal">
      <div class="modal-header">
        <h3>添加知识</h3>
        <button class="modal-close" @click="showAddDialog = false">✕</button>
      </div>
      <div class="modal-tabs">
        <button class="modal-tab" :class="{ active: addTab === 'upload' }" @click="addTab = 'upload'">上传文档</button>
        <button class="modal-tab" :class="{ active: addTab === 'manual' }" @click="addTab = 'manual'">人工录入</button>
      </div>
      <div class="modal-body">
        <div v-if="addTab === 'upload'">
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
              <input type="file" ref="fileInput" @change="handleFileChange" accept=".pdf,.doc,.docx,.txt,.md" style="display: none;" />
              <div class="file-upload-icon">📁</div>
              <div class="file-upload-text">将文件拖到此处，或<em>点击上传</em></div>
              <div class="file-upload-hint">支持 PDF、Word、TXT、Markdown 格式</div>
            </div>
          </div>
        </div>
        <div v-if="addTab === 'manual'">
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

  <!-- Detail Dialog -->
  <div class="modal-overlay" v-if="showDetailDialog" @click.self="showDetailDialog = false">
    <div class="modal modal-lg">
      <div class="modal-header">
        <h3>编辑知识</h3>
        <button class="modal-close" @click="showDetailDialog = false">✕</button>
      </div>
      <div class="modal-body">
        <div class="detail-grid" v-if="currentPreview?.id">
          <div class="detail-item">
            <div class="detail-label">ID</div>
            <div class="detail-value">{{ currentPreview.id }}</div>
          </div>
          <div class="detail-item">
            <div class="detail-label">标题</div>
            <input type="text" v-model="editForm.title" class="edit-input" />
          </div>
          <div class="detail-item">
            <div class="detail-label">来源名称</div>
            <div class="detail-value">{{ currentPreview.sourceName || '-' }}</div>
          </div>
          <div class="detail-item">
            <div class="detail-label">来源类型</div>
            <input type="text" v-model="editForm.sourceType" class="edit-input" />
          </div>
          <div class="detail-item">
            <div class="detail-label">作者</div>
            <input type="text" v-model="editForm.author" class="edit-input" />
          </div>
          <div class="detail-item">
            <div class="detail-label">发布时间</div>
            <div class="detail-value">{{ currentPreview.publishTime || '-' }}</div>
          </div>
          <div class="detail-item-full">
            <div class="detail-label">内容</div>
            <textarea v-model="editForm.content" class="edit-textarea" rows="8"></textarea>
          </div>
        </div>
      </div>
      <div class="modal-footer">
        <button class="btn btn-secondary" @click="showDetailDialog = false">取消</button>
        <button class="btn btn-primary" @click="handleSave" :disabled="saving">{{ saving ? '保存中...' : '保存' }}</button>
      </div>
    </div>

    <!-- Category Mapping Dialog -->
    <CategoryMappingDialog v-model="showMappingDialog" />
  </div>
</div>
</template>
<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { knowledgeApi } from '@/api/knowledge'
import { categoryApi, type Category } from '@/api/category'
import { vectorizationApi } from '@/api/vectorization'
import CategoryTree from '@/components/CategoryTree.vue'
import CategoryMappingDialog from '@/components/CategoryMappingDialog.vue'

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
  tags?: string[]
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
const viewMode = ref('card')
const previewVisible = ref(false)
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

const isAllSelected = computed(() =>
  list.value.length > 0 && list.value.every(item => selectedItems.value.includes(item.id!))
)

// Current preview
const currentPreview = ref<KnowledgeItem | null>(null)
const relatedItems = ref<KnowledgeItem[]>([])

// Dialogs
const showAddDialog = ref(false)
const showDetailDialog = ref(false)
const showMappingDialog = ref(false)
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

const uploadForm = reactive({
  title: '',
  source: '',
  author: '',
  file: null as File | null
})

const manualForm = reactive({
  title: '',
  content: '',
  source: '',
  author: ''
})

const uploading = ref(false)
const submitting = ref(false)
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

const filteredList = computed(() => {
  const q = filters.search.toLowerCase().trim()
  if (!q) return list.value
  return list.value.filter(item =>
    (item.title && item.title.toLowerCase().includes(q)) ||
    (item.content && item.content.toLowerCase().includes(q))
  )
})

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
  // Poll vectorization stats every 10 seconds
  statsTimer.value = setInterval(() => {
    loadVectorStats()
  }, 10000)
})

onUnmounted(() => {
  document.removeEventListener('click', clickHandler)
  if (statsTimer.value) clearInterval(statsTimer.value)
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

async function viewDetail(item: KnowledgeItem) {
  try {
    const res: any = await knowledgeApi.getById(item.id!)
    currentPreview.value = res.data || item
    previewVisible.value = true
    editForm.title = currentPreview.value.title || ''
    editForm.content = currentPreview.value.content || ''
    editForm.sourceType = currentPreview.value.sourceType || ''
    editForm.author = currentPreview.value.author || ''
  } catch (e) {
    currentPreview.value = item
    previewVisible.value = true
    editForm.title = item.title || ''
    editForm.content = item.content || ''
    editForm.sourceType = item.sourceType || ''
    editForm.author = item.author || ''
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
    showDetailDialog.value = false
    loadData()
  } catch (e) {
    console.error('保存失败', e)
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

function closePreview() {
  previewVisible.value = false
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

  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('title', uploadForm.title)
    formData.append('source', uploadForm.source || '')
    formData.append('author', uploadForm.author || '')
    formData.append('file', uploadForm.file)
    await knowledgeApi.upload(formData)
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
      author: manualForm.author || undefined
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
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// Mock data for testing

</script>

<style scoped>
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

.card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.card-tag {
  padding: 3px 8px;
  background: var(--bg-tertiary);
  border-radius: 4px;
  font-size: 11px;
  color: var(--text-secondary);
}

.card-tag.price {
  background: var(--accent-bg);
  color: var(--accent);
}

.card-tag.supply {
  background: rgba(88, 166, 255, 0.15);
  color: var(--blue);
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

.table-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
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
</style>
