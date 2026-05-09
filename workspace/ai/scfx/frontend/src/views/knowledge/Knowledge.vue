<template>
  <div class="knowledge-container">
    <!-- Left Sidebar -->
    <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
      <button class="sidebar-toggle-btn" @click="toggleSidebar" :title="sidebarCollapsed ? '展开' : '折叠'">
        {{ sidebarCollapsed ? '▶' : '◀' }}
      </button>

      <div class="sidebar-tabs">
        <button class="sidebar-tab" :class="{ active: sidebarTab === 'tree' }" @click="sidebarTab = 'tree'">分类</button>
        <button class="sidebar-tab" :class="{ active: sidebarTab === 'tag' }" @click="sidebarTab = 'tag'">标签</button>
      </div>

      <div class="sidebar-content" v-show="sidebarTab === 'tree'">
        <div class="sidebar-section">
          <div class="sidebar-header">
            来源分类
            <button class="sidebar-header-btn" title="新建分类">+</button>
          </div>
          <div
            v-for="source in sources"
            :key="source.key"
            class="sidebar-item"
            :class="{ active: selectedSource === source.key }"
            @click="selectSource(source.key)"
          >
            <span class="icon">{{ source.icon }}</span>
            <span class="name">{{ source.name }}</span>
            <span class="count">{{ source.count }}</span>
          </div>
        </div>
      </div>

      <div class="sidebar-content" v-show="sidebarTab === 'tag'">
        <div class="sidebar-section">
          <div class="sidebar-header">
            标签
            <button class="sidebar-header-btn" title="新建标签">+</button>
          </div>
          <div
            class="sidebar-item"
            :class="{ active: selectedTag === 'all' }"
            @click="selectTag('all')"
          >
            <span class="icon">🏷️</span>
            <span class="name">全部标签</span>
            <span class="count">{{ totalTags }}</span>
          </div>
          <div
            v-for="tag in tags"
            :key="tag.key"
            class="sidebar-item"
            :class="{ active: selectedTag === tag.key }"
            @click="selectTag(tag.key)"
            style="padding-left: 20px;"
          >
            <span class="tag-dot" :style="{ background: tag.color }"></span>
            <span class="name">{{ tag.name }}</span>
            <span class="count">{{ tag.count }}</span>
          </div>
        </div>
      </div>

      <div class="sidebar-footer">
        <button class="btn btn-secondary">
          <el-icon><Upload /></el-icon>
          导入
        </button>
        <button class="btn btn-secondary">
          <el-icon><Download /></el-icon>
          导出
        </button>
      </div>
    </aside>

    <!-- List Area -->
    <section class="list-area" :class="{ 'list-collapsed': listCollapsed }">
      <button class="list-collapse-btn" @click="toggleList" title="折叠列表">◀</button>

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
          <el-button circle @click="loadData" :loading="loading" title="刷新">
            <el-icon><Refresh /></el-icon>
          </el-button>
          <el-button type="primary" @click="showAddDialog = true">
            <el-icon><Plus /></el-icon>
            新建
          </el-button>
        </div>
      </div>

      <!-- Filters -->
      <div class="filters-bar">
        <div class="filter-dropdown" v-click-outside="() => sourceDropdownOpen = false">
          <button class="filter-dropdown-btn" @click="sourceDropdownOpen = !sourceDropdownOpen">
            来源 <span>{{ filters.sourceName || '全部' }}</span>
            <el-icon><ArrowDown /></el-icon>
          </button>
          <div class="filter-dropdown-menu" v-show="sourceDropdownOpen">
            <div
              v-for="source in sources"
              :key="source.key"
              class="filter-dropdown-item"
              :class="{ selected: filters.sourceType === source.key }"
              @click="selectSourceFilter(source.key, source.name)"
            >{{ source.icon }} {{ source.name }}</div>
          </div>
        </div>

        <div class="filter-dropdown" v-click-outside="() => statusDropdownOpen = false">
          <button class="filter-dropdown-btn" @click="statusDropdownOpen = !statusDropdownOpen">
            状态 <span>{{ filters.vectorStatus || '全部' }}</span>
            <el-icon><ArrowDown /></el-icon>
          </button>
          <div class="filter-dropdown-menu" v-show="statusDropdownOpen">
            <div class="filter-dropdown-item" :class="{ selected: !filters.vectorStatus }" @click="selectStatusFilter('')">全部</div>
            <div class="filter-dropdown-item" :class="{ selected: filters.vectorStatus === 'vectorized' }" @click="selectStatusFilter('vectorized')">✅ 已向量化</div>
            <div class="filter-dropdown-item" :class="{ selected: filters.vectorStatus === 'pending' }" @click="selectStatusFilter('pending')">⏳ 未向量化</div>
            <div class="filter-dropdown-item" :class="{ selected: filters.vectorStatus === 'processing' }" @click="selectStatusFilter('processing')">🔄 处理中</div>
            <div class="filter-dropdown-item" :class="{ selected: filters.vectorStatus === 'failed' }" @click="selectStatusFilter('failed')">❌ 失败</div>
          </div>
        </div>

        <el-input
          v-model="filters.search"
          placeholder="搜索标题..."
          style="width: 200px;"
          clearable
          @keyup.enter="loadData"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>

        <div class="filter-spacer"></div>
        <el-button text v-if="hasFilters" @click="clearFilters" type="info">
          <el-icon><Close /></el-icon> 清除筛选
        </el-button>
      </div>

      <!-- Batch Bar -->
      <div class="batch-bar" v-if="selectedItems.length > 0">
        <div class="batch-bar-left">
          <span>已选择 <strong>{{ selectedItems.length }}</strong> 项</span>
        </div>
        <div style="display: flex; gap: 8px;">
          <el-button size="small" @click="batchVectorize">🔄 重向量化</el-button>
          <el-button size="small" @click="batchDelete" type="danger">🗑️ 删除</el-button>
          <el-button size="small" @click="clearSelection">✕</el-button>
        </div>
      </div>

      <!-- Card Grid -->
      <div class="card-grid" v-show="viewMode === 'card' && !listCollapsed">
        <div
          v-for="item in list"
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
          <div class="card-actions">
            <el-button size="small" text @click.stop="viewDetail(item)">预览</el-button>
            <el-button size="small" text type="primary" @click.stop="handleRevectorize(item)">重向量化</el-button>
            <el-button size="small" text type="danger" @click.stop="handleDelete(item)">删除</el-button>
          </div>
        </div>

        <el-empty v-if="list.length === 0 && !loading" description="暂无数据" />
      </div>

      <!-- Table View -->
      <div class="table-view" v-show="viewMode === 'table' && !listCollapsed">
        <el-table
          :data="list"
          stripe
          v-loading="loading"
          @selection-change="handleSelectionChange"
          @row-click="handleRowClick"
        >
          <el-table-column type="selection" width="40" />
          <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
          <el-table-column prop="sourceName" label="来源" width="120">
            <template #default="{ row }">
              <span>{{ row.sourceIcon || '📄' }} {{ row.sourceName || row.sourceType || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="vectorStatus" label="状态" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.vectorStatus === 'vectorized'" type="success" size="small">已向量化</el-tag>
              <el-tag v-else-if="row.vectorStatus === 'pending'" type="warning" size="small">未向量化</el-tag>
              <el-tag v-else-if="row.vectorStatus === 'processing'" type="info" size="small">处理中</el-tag>
              <el-tag v-else-if="row.vectorStatus === 'failed'" type="danger" size="small">失败</el-tag>
              <el-tag v-else size="small">{{ row.vectorStatus || '-' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="chunkCount" label="块数" width="80" />
          <el-table-column prop="publishTime" label="日期" width="120" />
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button type="info" size="small" text @click.stop="viewDetail(row)">查看</el-button>
              <el-button type="primary" size="small" text @click.stop="handleRevectorize(row)">重向量化</el-button>
              <el-button type="danger" size="small" text @click.stop="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- Pagination -->
      <div class="pagination-wrapper" v-if="!listCollapsed">
        <div class="pagination-info">
          显示 {{ pagination.start }}-{{ pagination.end }} 条，共 {{ pagination.total }} 条
        </div>
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="prev, pager, next"
          @size-change="loadData"
          @current-change="loadData"
        />
        <el-select v-model="pagination.size" style="width: 120px;" @change="loadData">
          <el-option :label="`${pagination.size} 条/页`" :value="pagination.size" />
        </el-select>
      </div>
    </section>

    <!-- Preview Panel -->
    <aside class="preview-panel" :class="{ hidden: !previewVisible, fullscreen: previewFullscreen }">
      <div class="preview-header">
        <div class="preview-title">
          <span class="preview-title-icon">{{ currentPreview?.sourceIcon || '📄' }}</span>
          <span>{{ currentPreview?.title || '知识详情' }}</span>
        </div>
        <div class="preview-header-actions">
          <el-button circle text @click="togglePreviewList" title="隐藏列表">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="3" width="18" height="18" rx="2"/><line x1="9" y1="3" x2="9" y2="21"/>
            </svg>
          </el-button>
          <el-button circle text @click="openInNewWindow" title="新窗口打开">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/>
              <polyline points="15,3 21,3 21,9"/><line x1="10" y1="14" x2="21" y2="3"/>
            </svg>
          </el-button>
          <el-button circle text @click="toggleFullscreen" :title="previewFullscreen ? '退出全屏' : '全屏'">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline v-if="previewFullscreen" points="9,21 3,21 3,15"/><polyline v-if="previewFullscreen" points="15,3 21,3 21,9"/>
              <line v-if="previewFullscreen" x1="3" y1="21" x2="10" y2="14"/><line v-if="previewFullscreen" x1="21" y1="3" x2="14" y2="10"/>
              <polyline v-if="!previewFullscreen" points="15,3 21,3 21,9"/><polyline v-if="!previewFullscreen" points="9,21 3,21 3,15"/>
              <line v-if="!previewFullscreen" x1="21" y1="3" x2="14" y2="10"/><line v-if="!previewFullscreen" x1="3" y1="21" x2="10" y2="14"/>
            </svg>
          </el-button>
          <el-button circle text @click="closePreview" title="关闭">
            <el-icon><Close /></el-icon>
          </el-button>
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
          <div class="content-block" v-if="currentPreview.content">
            <h2>{{ currentPreview.title }}</h2>
            <p>{{ currentPreview.content }}</p>
          </div>
          <div class="content-block" v-else>
            <p style="color: var(--text-muted);">暂无内容摘要</p>
          </div>
        </div>

        <div class="preview-section" v-if="relatedItems.length > 0">
          <div class="preview-section-title">关联知识</div>
          <div class="related-list">
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
        </div>
      </div>

      <div class="preview-footer" v-if="currentPreview">
        <el-button @click="showDetailDialog = true">编辑</el-button>
        <el-button type="primary" @click="handleRevectorize(currentPreview)" :loading="currentPreview.revectorizing">
          向量化
        </el-button>
      </div>
    </aside>
  </div>

  <!-- Add Dialog -->
  <el-dialog v-model="showAddDialog" title="添加知识" width="700px" :close-on-click-modal="false">
    <el-tabs v-model="addTab">
      <el-tab-pane label="上传文档" name="upload">
        <el-form :model="uploadForm" label-width="100px">
          <el-form-item label="标题" required>
            <el-input v-model="uploadForm.title" placeholder="请输入文档标题" />
          </el-form-item>
          <el-form-item label="来源">
            <el-input v-model="uploadForm.source" placeholder="如：粮信网" />
          </el-form-item>
          <el-form-item label="作者">
            <el-input v-model="uploadForm.author" placeholder="可选" />
          </el-form-item>
          <el-form-item label="选择文件" required>
            <el-upload
              ref="uploadRef"
              :auto-upload="false"
              :limit="1"
              accept=".pdf,.doc,.docx,.txt,.md"
              :on-change="handleFileChange"
              drag
            >
              <el-icon class="upload-icon"><UploadFilled /></el-icon>
              <div class="upload-text">将文件拖到此处，或<em>点击上传</em></div>
              <template #tip>
                <div class="el-upload__tip">支持 PDF、Word、TXT、Markdown 格式</div>
              </template>
            </el-upload>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="showAddDialog = false">取消</el-button>
          <el-button type="primary" @click="handleUpload" :loading="uploading">上传</el-button>
        </template>
      </el-tab-pane>

      <el-tab-pane label="人工录入" name="manual">
        <el-form :model="manualForm" label-width="100px">
          <el-form-item label="标题" required>
            <el-input v-model="manualForm.title" placeholder="请输入知识标题" />
          </el-form-item>
          <el-form-item label="内容" required>
            <el-input v-model="manualForm.content" type="textarea" :rows="8" placeholder="请输入知识内容" />
          </el-form-item>
          <el-form-item label="来源">
            <el-input v-model="manualForm.source" placeholder="可选，如：内部文档" />
          </el-form-item>
          <el-form-item label="作者">
            <el-input v-model="manualForm.author" placeholder="可选" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="showAddDialog = false">取消</el-button>
          <el-button type="primary" @click="handleManualAdd" :loading="submitting">提交</el-button>
        </template>
      </el-tab-pane>
    </el-tabs>
  </el-dialog>

  <!-- Detail Dialog -->
  <el-dialog v-model="showDetailDialog" title="知识详情" width="700px">
    <el-descriptions :column="2" border v-if="currentPreview?.id">
      <el-descriptions-item label="ID">{{ currentPreview.id }}</el-descriptions-item>
      <el-descriptions-item label="标题">{{ currentPreview.title }}</el-descriptions-item>
      <el-descriptions-item label="来源名称">{{ currentPreview.sourceName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="来源类型">{{ getSourceTypeText(currentPreview.sourceType) }}</el-descriptions-item>
      <el-descriptions-item label="作者">{{ currentPreview.author || '-' }}</el-descriptions-item>
      <el-descriptions-item label="发布时间">{{ currentPreview.publishTime || '-' }}</el-descriptions-item>
      <el-descriptions-item label="文本块数">{{ currentPreview.chunkCount || 0 }}</el-descriptions-item>
      <el-descriptions-item label="向量化状态">
        <el-tag v-if="currentPreview.vectorStatus === 'vectorized'" type="success">已向量化</el-tag>
        <el-tag v-else-if="currentPreview.vectorStatus === 'pending'" type="warning">未向量化</el-tag>
        <el-tag v-else-if="currentPreview.vectorStatus === 'processing'" type="info">处理中</el-tag>
        <el-tag v-else-if="currentPreview.vectorStatus === 'failed'" type="danger">失败</el-tag>
        <el-tag v-else>{{ currentPreview.vectorStatus || '-' }}</el-tag>
      </el-descriptions-item>
    </el-descriptions>
    <template #footer>
      <el-button @click="showDetailDialog = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Refresh, Plus, Close, Search, Upload, Download,
  UploadFilled, ArrowDown
} from '@element-plus/icons-vue'
import { knowledgeApi } from '@/api/knowledge'

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
  tags?: string[]
  revectorizing?: boolean
}

// Sources
const sources = ref([
  { key: 'all', name: '全部', icon: '📚', count: 0 },
  { key: 'liangxin', name: '粮信网', icon: '🌐', count: 0 },
  { key: 'mysteel', name: '我的钢铁', icon: '📺', count: 0 },
  { key: 'chinagrain', name: '中华粮网', icon: '🌾', count: 0 },
  { key: 'usda', name: 'USDA', icon: '🦃', count: 0 },
  { key: 'manual', name: '人工录入', icon: '✍️', count: 0 }
])

// Tags
const tags = ref([
  { key: 'price', name: '价格', color: '#f5c87a', count: 0 },
  { key: 'supply', name: '供需', color: '#58a6ff', count: 0 },
  { key: 'policy', name: '政策', color: '#a371f7', count: 0 },
  { key: 'international', name: '国际', color: '#3fb950', count: 0 }
])

// UI State
const sidebarCollapsed = ref(false)
const sidebarTab = ref('tree')
const listCollapsed = ref(false)
const viewMode = ref('card')
const previewVisible = ref(false)
const previewFullscreen = ref(false)
const sourceDropdownOpen = ref(false)
const statusDropdownOpen = ref(false)

// Filters
const selectedSource = ref('all')
const selectedTag = ref('all')

const filters = reactive({
  sourceType: '',
  sourceName: '',
  vectorStatus: '',
  search: ''
})

const hasFilters = computed(() => filters.sourceType || filters.vectorStatus || filters.search)

// List data
const list = ref<KnowledgeItem[]>([])
const loading = ref(false)
const selectedItems = ref<number[]>([])

// Pagination
const pagination = reactive({
  page: 1,
  size: 20,
  total: 0,
  start: 0,
  end: 0
})

// Current preview
const currentPreview = ref<KnowledgeItem | null>(null)
const relatedItems = ref<KnowledgeItem[]>([])

// Dialogs
const showAddDialog = ref(false)
const showDetailDialog = ref(false)
const addTab = ref('upload')
const uploadRef = ref()

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

const statusOptions = [
  { value: 'vectorized', label: '已向量化', color: '#3fb950' },
  { value: 'pending', label: '未向量化', color: '#d29922' },
  { value: 'processing', label: '处理中', color: '#58a6ff' },
  { value: 'failed', label: '失败', color: '#f85149' }
]

const totalTags = computed(() => tags.value.reduce((sum, t) => sum + t.count, 0))

const currentListTitle = computed(() => {
  if (selectedSource.value !== 'all') {
    return sources.value.find(s => s.key === selectedSource.value)?.name || '知识列表'
  }
  if (selectedTag.value !== 'all') {
    return tags.value.find(t => t.key === selectedTag.value)?.name || '知识列表'
  }
  return '知识列表'
})

// Methods
function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

function toggleList() {
  listCollapsed.value = !listCollapsed.value
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

function selectTag(key: string) {
  selectedTag.value = key
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
  selectedSource.value = 'all'
  pagination.page = 1
  loadData()
}

function setView(mode: string) {
  viewMode.value = mode
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await knowledgeApi.list({
      page: pagination.page,
      size: pagination.size,
      sourceType: filters.sourceType || undefined,
      vectorStatus: filters.vectorStatus || undefined
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

function handleCardClick(event: Event, item: KnowledgeItem) {
  if ((event.target as HTMLElement).closest('.card-checkbox') ||
      (event.target as HTMLElement).closest('.card-actions')) {
    return
  }
  viewDetail(item)
}

function handleRowClick(row: KnowledgeItem) {
  viewDetail(row)
}

function handleSelectionChange(selection: KnowledgeItem[]) {
  selectedItems.value = selection.map(item => item.id!).filter(Boolean)
}

function toggleSelect(id: number) {
  const idx = selectedItems.value.indexOf(id)
  if (idx >= 0) {
    selectedItems.value.splice(idx, 1)
  } else {
    selectedItems.value.push(id)
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

    // Load related items
    relatedItems.value = list.value
      .filter(i => i.id !== item.id)
      .slice(0, 3)
      .map(i => ({ ...i, score: Math.floor(Math.random() * 20) + 80 }))
  } catch (e) {
    currentPreview.value = item
    previewVisible.value = true
  }
}

function closePreview() {
  previewVisible.value = false
  currentPreview.value = null
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

function changeStatus(status: string) {
  if (!currentPreview.value) return
  currentPreview.value.vectorStatus = status
  ElMessage.success(`状态已更新为: ${status}`)
}

function getSourceTypeText(type?: string) {
  const map: Record<string, string> = {
    liangxin: '粮信网',
    mysteel: '我的钢铁',
    chinagrain: '中华粮网',
    usda: 'USDA',
    manual: '人工录入'
  }
  return map[type || ''] || type || '-'
}

function showAddDialogFn() {
  addTab.value = 'upload'
  Object.keys(uploadForm).forEach(k => {
    (uploadForm as any)[k] = ''
  })
  Object.keys(manualForm).forEach(k => {
    (manualForm as any)[k] = ''
  })
  uploadForm.file = null
  showAddDialog.value = true
}

function handleFileChange(file: any) {
  uploadForm.file = file.raw
  if (!uploadForm.title && file.name) {
    uploadForm.title = file.name.replace(/\.(pdf|doc|docx|txt|md)$/i, '')
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
    ElMessage.success('已发起重向量化任务')
    loadData()
  } catch (e: any) {
    if (e !== 'cancel') {
      console.error('重向量化失败', e)
      ElMessage.error('重向量化失败')
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
    ElMessage.success('已发起重向量化任务')
    clearSelection()
    loadData()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error('重向量化失败')
    }
  }
}

async function batchDelete() {
  if (selectedItems.value.length === 0) return
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${selectedItems.value.length} 项知识吗？删除后无法恢复！`, '警告', { type: 'warning' })
    for (const id of selectedItems.value) {
      await knowledgeApi.delete(id)
    }
    ElMessage.success('删除成功')
    clearSelection()
    loadData()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// Click outside directive
const vClickOutside = {
  mounted(el: HTMLElement, binding: any) {
    el.__clickOutsideHandler__ = (event: Event) => {
      if (!el.contains(event.target as Node)) {
        binding.value()
      }
    }
    document.addEventListener('click', el.__clickOutsideHandler__)
  },
  unmounted(el: HTMLElement) {
    document.removeEventListener('click', el.__clickOutsideHandler__)
  }
}

onMounted(() => {
  loadData()
})
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
}

.sidebar-toggle-btn {
  position: absolute;
  top: 50%;
  right: -14px;
  transform: translateY(-50%);
  width: 28px;
  height: 28px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--text-muted);
  z-index: 100;
  opacity: 0;
  transition: all 0.2s;
}

.sidebar:hover .sidebar-toggle-btn {
  opacity: 1;
}

.sidebar-toggle-btn:hover {
  background: var(--accent);
  color: #1a1f2e;
  border-color: var(--accent);
}

.sidebar-tabs {
  display: flex;
  border-bottom: 1px solid var(--border-color);
  padding: 8px 8px 0;
}

.sidebar-tab {
  flex: 1;
  padding: 10px;
  background: transparent;
  border: none;
  border-bottom: 2px solid transparent;
  color: var(--text-muted);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}

.sidebar-tab:hover {
  color: var(--text-secondary);
}

.sidebar-tab.active {
  color: var(--accent);
  border-bottom-color: var(--accent);
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
}

.view-toggle button.active {
  background: var(--accent);
  color: #1a1f2e;
}

.view-toggle button:hover:not(.active) {
  color: var(--text-primary);
}

/* Filters */
.filters-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
  background: var(--bg-primary);
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
}

.filter-dropdown {
  position: relative;
}

.filter-dropdown-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}

.filter-dropdown-btn:hover {
  border-color: var(--accent);
  color: var(--text-primary);
}

.filter-dropdown-btn span {
  color: var(--text-muted);
}

.filter-dropdown-menu {
  position: absolute;
  top: 100%;
  left: 0;
  min-width: 160px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 4px;
  z-index: 100;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
}

.filter-dropdown-item {
  padding: 8px 12px;
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.15s;
}

.filter-dropdown-item:hover {
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

.filter-dropdown-item.selected {
  background: var(--accent-bg);
  color: var(--accent);
}

.filter-spacer {
  flex: 1;
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
}

.card:hover {
  border-color: var(--accent);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
}

.card.selected {
  border-color: var(--accent);
  background: var(--accent-bg);
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
  margin-bottom: 12px;
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

.card-actions {
  display: flex;
  gap: 4px;
  padding-top: 12px;
  border-top: 1px solid var(--border-color);
}

/* Table View */
.table-view {
  flex: 1;
  overflow: auto;
  padding: 0 20px 20px;
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

/* Upload dialog styles */
.upload-icon {
  font-size: 40px;
  color: #8c939d;
  margin-bottom: 10px;
}

.upload-text {
  color: #8c939d;
}

.upload-text em {
  color: #409eff;
  font-style: normal;
}
</style>
