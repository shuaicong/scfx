<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { debounce } from 'lodash-es'
import { categoryApi, type Category } from '@/api/category'

// Undo action types
interface UndoAction {
  type: 'create' | 'update' | 'delete' | 'move'
  before: Partial<Category> | null
  after: Partial<Category> | null
  timestamp: number
}

const undoStack = ref<UndoAction[]>([])
const MAX_UNDO_SIZE = 10

const pushUndo = (action: UndoAction) => {
  undoStack.value.push(action)
  if (undoStack.value.length > MAX_UNDO_SIZE) {
    undoStack.value.shift()
  }
}

// Undo functionality
const undo = async () => {
  const action = undoStack.value.pop()
  if (!action) return

  switch (action.type) {
    case 'create':
      if (action.after?.id) {
        await categoryApi.delete(action.after.id)
      }
      break
    case 'update':
      if (action.after?.id && action.before) {
        await categoryApi.update(action.after.id, action.before)
      }
      break
    case 'delete':
      if (action.before?.id) {
        await categoryApi.restore(action.before.id)
      }
      break
    case 'move':
      if (action.after?.id && action.before) {
        await categoryApi.update(action.after.id, action.before)
      }
      break
  }
  await loadTree()
}

// Real-time sync state
const localVersion = ref(0)
const showSyncNotification = ref(false)
let syncInterval: ReturnType<typeof setInterval> | null = null

const checkVersion = async () => {
  try {
    const res = await categoryApi.version()
    const remoteVersion = res.data.data.version
    if (remoteVersion > localVersion.value && localVersion.value > 0) {
      showSyncNotification.value = true
    }
    localVersion.value = remoteVersion
  } catch (e) {
    console.error('Failed to check version:', e)
  }
}

const refreshTree = async () => {
  await loadTree()
  const res = await categoryApi.tree()
  localVersion.value = res.data.version
  showSyncNotification.value = false
}

// Depth path calculation
const getDepthPath = (category: Category, allCategories: Category[], parentPath: string = ''): string => {
  const siblings = allCategories.filter(c => c.parentId === category.parentId)
  const index = siblings.findIndex(c => c.id === category.id) + 1
  const currentPath = parentPath ? `${parentPath}.${index}` : `${index}`

  if (category.children?.length) {
    return `${currentPath} (${category.children.length}个子分类)`
  }
  return currentPath
}

const getDisplayDepth = (category: Category): string => {
  const path = getDepthPath(category, flattenCategories(folders.value))
  return `[${path}] ${category.name}`
}

const props = defineProps<{
  selectedId?: number
}>()

const emit = defineEmits<{
  (e: 'select', category: Category): void
  (e: 'update', categories: Category[]): void
}>()

const folders = ref<Category[]>([])
const expandedIds = ref<Set<number>>(new Set())
const loading = ref(false)
const selectedCategory = ref<Category | null>(null)

// Search state
const searchQuery = ref('')
const searchResults = computed(() => {
  if (!searchQuery.value) return []
  const query = searchQuery.value.toLowerCase()
  return flattenCategories(folders.value).filter(c =>
    c.name.toLowerCase().includes(query)
  )
})

const flattenCategories = (cats: Category[]): Category[] => {
  return cats.flatMap(c => [c, ...flattenCategories(c.children || [])])
}

// Navigate to search result
const navigateToCategory = (category: Category) => {
  // Expand all parent categories first
  const expandParents = (cats: Category[], targetId: number, path: number[] = []): boolean => {
    for (const cat of cats) {
      if (cat.id === targetId) {
        // Expand all ancestors
        path.forEach(id => expandedIds.value.add(id))
        expandedIds.value = new Set(expandedIds.value)
        return true
      }
      if (cat.children) {
        if (expandParents(cat.children, targetId, [...path, cat.id])) {
          return true
        }
      }
    }
    return false
  }
  expandParents(folders.value, category.id)
  selectCategory(category)
  searchQuery.value = ''
}

// Drag and drop state
const draggedCategory = ref<Category | null>(null)
const dropTargetId = ref<number | null>(null)

// Context menu state
const contextMenuVisible = ref(false)
const contextMenuPosition = ref({ x: 0, y: 0 })
const contextMenuCategory = ref<Category | null>(null)

// Dialog state
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingCategory = ref<Partial<Category>>({})

// Batch selection state
const selectedIds = ref<Set<number>>(new Set())
const batchMode = ref(false)

// Color picker preset colors
const presetColors = [
  '#58A6FF', '#3FB950', '#F0883E', '#FF6B6B',
  '#FFD93D', '#A371F7', '#8B949E', '#F5C87A'
]

// Description tooltip state
const hoveredCategoryId = ref<number | null>(null)

// Pinned categories
const pinnedCategories = computed(() => {
  return flattenCategories(folders.value)
    .filter(c => c.pinned === 1)
    .slice(0, 3) // 最多置顶 3 个
})

const togglePin = async (category: Category) => {
  const newPinned = category.pinned === 1 ? 0 : 1
  await categoryApi.update(category.id, { pinned: newPinned })
  await loadTree()
}

// Preview category state
const previewCategory = ref<Category | null>(null)
const previewKnowledge = ref<any[]>([])
const showPreview = ref(false)

const openPreview = async (category: Category) => {
  previewCategory.value = category
  // 实际应调用 API 获取该分类下的知识列表
  previewKnowledge.value = []
  showPreview.value = true
  contextMenuVisible.value = false
}

const closePreview = () => {
  showPreview.value = false
  previewCategory.value = null
  previewKnowledge.value = []
}

// Batch create state
const showBatchCreate = ref(false)
const batchCreateNames = ref('')

const openBatchCreateDialog = (parentId: number | null = null) => {
  editingCategory.value = { ...editingCategory.value, parentId }
  showBatchCreate.value = true
  contextMenuVisible.value = false
}

const confirmBatchCreate = async () => {
  const names = batchCreateNames.value.split('\n').filter(n => n.trim())
  for (const name of names) {
    await categoryApi.create({
      name: name.trim(),
      icon: editingCategory.value.icon || '📁',
      color: editingCategory.value.color,
      parentId: editingCategory.value.parentId,
      sortOrder: 99
    })
  }
  showBatchCreate.value = false
  batchCreateNames.value = ''
  await loadTree()
}

// Autocomplete state
const autocompleteQuery = ref('')
const autocompleteResults = ref<Category[]>([])

const searchCategories = async (query: string) => {
  if (!query) {
    autocompleteResults.value = []
    return
  }
  try {
    const res = await categoryApi.search(query)
    // API returns { code, data: [...] } after interceptor, so use res.data
    autocompleteResults.value = res.data || []
  } catch (e) {
    console.error('Failed to search categories:', e)
    autocompleteResults.value = []
  }
}

const debouncedSearch = debounce(searchCategories, 300)

const showDescription = (category: Category) => {
  if (category.description) {
    hoveredCategoryId.value = category.id
  }
}

const hideDescription = () => {
  hoveredCategoryId.value = null
}

const toggleSelect = (id: number, event: Event) => {
  event.stopPropagation()
  if (selectedIds.value.has(id)) {
    selectedIds.value.delete(id)
  } else {
    selectedIds.value.add(id)
  }
  batchMode.value = selectedIds.value.size > 0
}

const selectAll = () => {
  const all = flattenCategories(folders.value)
  all.forEach(c => selectedIds.value.add(c.id))
}

const clearSelection = () => {
  selectedIds.value.clear()
  batchMode.value = false
}

// Trash state
const trashCategories = ref<Category[]>([])
const showTrash = ref(false)

const loadTrash = async () => {
  const res = await categoryApi.trash()
  trashCategories.value = res.data.data || []
  showTrash.value = true
}

const restoreCategory = async (id: number) => {
  await categoryApi.restore(id)
  await loadTrash()
  await loadTree()
}

const permanentDeleteCategory = async (id: number) => {
  if (confirm('确定要永久删除吗？此操作不可恢复。')) {
    await categoryApi.permanentDelete(id)
    await loadTrash()
  }
}

const closeTrash = () => {
  showTrash.value = false
  loadTree()
}

// Merge category
const mergeCategory = async (sourceId: number, targetId: number) => {
  if (confirm('合并后源分类的知识将转移到目标分类，确定要合并吗？')) {
    await categoryApi.merge(sourceId, targetId)
    await loadTree()
  }
}

// Merge dialog state
const mergeDialogVisible = ref(false)
const mergeSourceCategory = ref<Category | null>(null)
const mergeTargetId = ref<number | ''>('')

const openMergeDialog = (category: Category) => {
  mergeSourceCategory.value = category
  mergeTargetId.value = ''
  mergeDialogVisible.value = true
  contextMenuVisible.value = false
}

const confirmMerge = async () => {
  if (mergeSourceCategory.value && mergeTargetId.value) {
    await mergeCategory(mergeSourceCategory.value.id, mergeTargetId.value)
    mergeDialogVisible.value = false
  }
}

// Load category tree
const loadTree = async () => {
  loading.value = true
  try {
    const res = await categoryApi.tree()
    folders.value = res.data.data || []
    emit('update', folders.value)
  } finally {
    loading.value = false
  }
}

// Toggle expand state
const toggleExpand = (id: number, event: Event) => {
  event.stopPropagation()
  if (expandedIds.value.has(id)) {
    expandedIds.value.delete(id)
  } else {
    expandedIds.value.add(id)
  }
  expandedIds.value = new Set(expandedIds.value) // trigger reactivity
  saveExpandedState()
}

// Select category
const selectCategory = (category: Category) => {
  selectedCategory.value = category
  emit('select', category)
}

// Save expand state to localStorage
const saveExpandedState = () => {
  localStorage.setItem('category-expanded-ids', JSON.stringify([...expandedIds.value]))
}

// Load expand state from localStorage
const loadExpandedState = () => {
  const saved = localStorage.getItem('category-expanded-ids')
  if (saved) {
    try {
      expandedIds.value = new Set(JSON.parse(saved))
    } catch (e) {
      expandedIds.value = new Set()
    }
  }
}

// Open create dialog
const openCreateDialog = (parentId: number | null = null) => {
  dialogMode.value = 'create'
  editingCategory.value = { parentId, sortOrder: 99 }
  dialogVisible.value = true
  contextMenuVisible.value = false
}

// Open edit dialog
const openEditDialog = (category: Category) => {
  dialogMode.value = 'edit'
  editingCategory.value = { ...category }
  dialogVisible.value = true
  contextMenuVisible.value = false
}

// Save category
const saveCategory = async () => {
  if (dialogMode.value === 'create') {
    const res = await categoryApi.create(editingCategory.value)
    pushUndo({
      type: 'create',
      before: null,
      after: res.data,
      timestamp: Date.now()
    })
  } else if (editingCategory.value.id) {
    const existing = flattenCategories(folders.value).find(c => c.id === editingCategory.value.id)
    await categoryApi.update(editingCategory.value.id, editingCategory.value)
    pushUndo({
      type: 'update',
      before: existing || null,
      after: editingCategory.value,
      timestamp: Date.now()
    })
  }
  dialogVisible.value = false
  await loadTree()
}

// Delete category
const deleteCategory = async (id: number) => {
  if (confirm('确定要删除该分类吗？子分类也会被删除。')) {
    const category = flattenCategories(folders.value).find(c => c.id === id)
    await categoryApi.delete(id)
    pushUndo({
      type: 'delete',
      before: category || null,
      after: null,
      timestamp: Date.now()
    })
    await loadTree()
  }
  contextMenuVisible.value = false
}

// Context menu handlers
const showContextMenu = (event: MouseEvent, category: Category) => {
  event.preventDefault()
  contextMenuCategory.value = category
  contextMenuPosition.value = { x: event.clientX, y: event.clientY }
  contextMenuVisible.value = true
}

const hideContextMenu = () => {
  contextMenuVisible.value = false
}

// Drag and drop handlers
const onDragStart = (event: DragEvent, category: Category) => {
  draggedCategory.value = category
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
  }
}

const onDragOver = (event: DragEvent, category: Category) => {
  event.preventDefault()
  dropTargetId.value = category.id
}

const onDragLeave = () => {
  dropTargetId.value = null
}

const onDrop = async (event: DragEvent, targetCategory: Category) => {
  event.preventDefault()
  if (!draggedCategory.value || draggedCategory.value.id === targetCategory.id) return

  // Store original state for undo
  const originalState = {
    parentId: draggedCategory.value.parentId,
    sortOrder: draggedCategory.value.sortOrder
  }

  // Update parent and sort order to target category's values
  const updateData = {
    parentId: targetCategory.parentId,
    sortOrder: targetCategory.sortOrder
  }
  await categoryApi.update(draggedCategory.value.id, updateData)

  // Push undo action
  pushUndo({
    type: 'move',
    before: originalState,
    after: updateData,
    timestamp: Date.now()
  })

  await loadTree()

  draggedCategory.value = null
  dropTargetId.value = null
}

// Duplicate category
const duplicateCategory = async (category: Category) => {
  await categoryApi.create({
    name: category.name + ' (copy)',
    icon: category.icon,
    color: category.color,
    parentId: category.parentId,
    sortOrder: category.sortOrder + 1
  })
  await loadTree()
  contextMenuVisible.value = false
}

// Calculate indent
const getIndent = (depth: number) => depth * 16

// Render category row recursively
const renderCategoryRow = (category: Category, depth: number) => {
  const hasChildren = category.children && category.children.length > 0
  const isExpanded = expandedIds.value.has(category.id)
  const isSelected = selectedCategory.value?.id === category.id

  return { hasChildren, isExpanded, isSelected }
}

// Watch selectedId prop
watch(() => props.selectedId, (newId) => {
  if (newId && selectedCategory.value?.id !== newId) {
    // Find and select the category with matching id
    const findAndSelect = (cats: Category[]): boolean => {
      for (const cat of cats) {
        if (cat.id === newId) {
          selectedCategory.value = cat
          emit('select', cat)
          return true
        }
        if (cat.children) {
          if (findAndSelect(cat.children)) return true
        }
      }
      return false
    }
    findAndSelect(folders.value)
  }
})

onMounted(() => {
  loadTree()
  loadExpandedState()
  document.addEventListener('click', hideContextMenu)
  // Keyboard shortcut for undo
  const handleKeydown = (e: KeyboardEvent) => {
    if (e.ctrlKey && e.key === 'z') {
      e.preventDefault()
      undo()
    }
  }
  document.addEventListener('keydown', handleKeydown)
  // Initial version check for sync
  checkVersion()
  // Poll for version updates every 30 seconds
  syncInterval = setInterval(checkVersion, 30000)
  onUnmounted(() => {
    document.removeEventListener('keydown', handleKeydown)
  })
})

onUnmounted(() => {
  document.removeEventListener('click', hideContextMenu)
  if (syncInterval) {
    clearInterval(syncInterval)
  }
})

defineExpose({ loadTree })
</script>

<template>
  <div class="category-tree" @click="hideContextMenu">
    <!-- Sync notification banner -->
    <div v-if="showSyncNotification" class="sync-notification" @click="refreshTree">
      检测到分类更新，点击刷新
    </div>
    <!-- Toolbar -->
    <div class="tree-toolbar">
      <button v-if="!batchMode" class="btn-new" @click="openCreateDialog(null)">+ 新建分类</button>
      <button v-if="!batchMode" class="btn-batch-tool" @click="openBatchCreateDialog(null)">批量创建</button>
      <template v-else>
        <span class="batch-info">已选择 {{ selectedIds.size }} 项</span>
        <button class="btn-batch" @click="selectAll">全选</button>
        <button class="btn-batch" @click="clearSelection">取消选择</button>
      </template>
      <button class="btn-trash" @click="loadTrash">回收站</button>
    </div>

    <!-- Pinned categories -->
    <div v-if="pinnedCategories.length > 0" class="pinned-section">
      <div class="pinned-header">置顶分类</div>
      <div
        v-for="cat in pinnedCategories"
        :key="cat.id"
        class="pinned-item"
        :class="{ selected: selectedCategory?.id === cat.id }"
        @click="selectCategory(cat)"
      >
        <span class="pinned-icon">{{ cat.icon }}</span>
        <span class="pinned-name">{{ cat.name }}</span>
        <button class="pin-btn pinned" @click.stop="togglePin(cat)" title="取消置顶">📌</button>
      </div>
    </div>

    <!-- Search input with autocomplete -->
    <div class="tree-search">
      <input
        v-model="autocompleteQuery"
        @input="debouncedSearch(autocompleteQuery)"
        placeholder="搜索分类..."
        class="search-input"
      />
      <div v-if="autocompleteResults.length > 0" class="search-results">
        <div
          v-for="result in autocompleteResults"
          :key="result.id"
          class="search-result-item"
          @click="navigateToCategory(result); autocompleteQuery = ''; autocompleteResults = []"
        >
          <span class="search-icon">{{ result.icon }}</span>
          <span class="search-name">{{ result.name }}</span>
        </div>
      </div>
    </div>

    <!-- Category list -->
    <div class="tree-content">
      <div v-if="loading" class="loading">加载中...</div>
      <template v-else>
        <div
          v-for="category in folders"
          :key="category.id"
          class="folder-wrapper"
        >
          <div
            class="folder-row"
            :class="{ selected: selectedCategory?.id === category.id, 'drag-over': dropTargetId === category.id }"
            :style="{ paddingLeft: getIndent(0) + 'px' }"
            draggable="true"
            @dragstart="onDragStart($event, category)"
            @dragover="onDragOver($event, category)"
            @dragleave="onDragLeave"
            @drop="onDrop($event, category)"
            @click="selectCategory(category)"
            @contextmenu="showContextMenu($event, category)"
            @mouseenter="showDescription(category)"
            @mouseleave="hideDescription"
          >
            <div v-if="hoveredCategoryId === category.id && category.description" class="description-tooltip">
              {{ category.description }}
            </div>
            <input
              type="checkbox"
              class="batch-checkbox"
              :checked="selectedIds.has(category.id)"
              @click="toggleSelect(category.id, $event)"
            />
            <span
              v-if="category.children?.length"
              class="folder-toggle"
              :class="{ expanded: expandedIds.has(category.id) }"
              @click="toggleExpand(category.id, $event)"
            >
              ▶
            </span>
            <span v-else class="folder-toggle-placeholder"></span>
            <span class="folder-icon">{{ category.icon }}</span>
            <span
              v-if="category.color"
              class="folder-color"
              :style="{ backgroundColor: category.color }"
            ></span>
            <span class="folder-name">{{ getDisplayDepth(category) }}</span>
            <span class="folder-count">({{ category.knowledgeCount || 0 }})</span>
          </div>

          <!-- Render children recursively -->
          <template v-if="category.children?.length && expandedIds.has(category.id)">
            <div
              v-for="child in category.children"
              :key="child.id"
              class="folder-wrapper sub"
            >
              <div
                class="folder-row"
                :class="{ selected: selectedCategory?.id === child.id, 'drag-over': dropTargetId === child.id }"
                :style="{ paddingLeft: getIndent(1) + 'px' }"
                draggable="true"
                @dragstart="onDragStart($event, child)"
                @dragover="onDragOver($event, child)"
                @dragleave="onDragLeave"
                @drop="onDrop($event, child)"
                @click="selectCategory(child)"
                @contextmenu="showContextMenu($event, child)"
                @mouseenter="showDescription(child)"
                @mouseleave="hideDescription"
              >
                <div v-if="hoveredCategoryId === child.id && child.description" class="description-tooltip">
                  {{ child.description }}
                </div>
                <input
                  type="checkbox"
                  class="batch-checkbox"
                  :checked="selectedIds.has(child.id)"
                  @click="toggleSelect(child.id, $event)"
                />
                <span class="folder-toggle-placeholder"></span>
                <span class="folder-icon">{{ child.icon }}</span>
                <span
                  v-if="child.color"
                  class="folder-color"
                  :style="{ backgroundColor: child.color }"
                ></span>
                <span class="folder-name">{{ getDisplayDepth(child) }}</span>
                <span class="folder-count">({{ child.knowledgeCount || 0 }})</span>
              </div>
            </div>
          </template>
        </div>
      </template>
    </div>

    <!-- Trash panel -->
    <div v-if="showTrash" class="trash-panel">
      <div class="trash-header">
        <h3>回收站</h3>
        <button class="close-btn" @click="closeTrash">×</button>
      </div>
      <div class="trash-content">
        <div v-if="trashCategories.length === 0" class="empty-trash">回收站为空</div>
        <div
          v-for="cat in trashCategories"
          :key="cat.id"
          class="trash-item"
        >
          <span class="trash-icon">{{ cat.icon }}</span>
          <span class="trash-name">{{ cat.name }}</span>
          <div class="trash-actions">
            <button class="btn-restore" @click="restoreCategory(cat.id)">恢复</button>
            <button class="btn-permanent-delete" @click="permanentDeleteCategory(cat.id)">永久删除</button>
          </div>
        </div>
      </div>
    </div>

    <!-- Context menu -->
    <div
      v-if="contextMenuVisible"
      class="context-menu"
      :style="{ left: contextMenuPosition.x + 'px', top: contextMenuPosition.y + 'px' }"
      @click.stop
    >
      <div class="context-menu-item" @click="openCreateDialog(contextMenuCategory?.id || null)">
        新建子分类
      </div>
      <div class="context-menu-item" @click="openBatchCreateDialog(contextMenuCategory?.id || null)">
        批量创建
      </div>
      <div class="context-menu-item" @click="openEditDialog(contextMenuCategory!)">
        编辑
      </div>
      <div class="context-menu-item" @click="togglePin(contextMenuCategory!)">
        {{ contextMenuCategory?.pinned === 1 ? '取消置顶' : '置顶' }}
      </div>
      <div class="context-menu-item" @click="openPreview(contextMenuCategory!)">
        预览
      </div>
      <div class="context-menu-item" @click="duplicateCategory(contextMenuCategory!)">
        复制
      </div>
      <div class="context-menu-item" @click="openMergeDialog(contextMenuCategory!)">
        合并
      </div>
      <div class="context-menu-item danger" @click="deleteCategory(contextMenuCategory!.id)">
        删除
      </div>
    </div>

    <!-- Create/Edit dialog -->
    <div v-if="dialogVisible" class="dialog-overlay" @click.self="dialogVisible = false">
      <div class="dialog">
        <div class="dialog-header">
          <h3>{{ dialogMode === 'create' ? '新建分类' : '编辑分类' }}</h3>
          <button class="close-btn" @click="dialogVisible = false">×</button>
        </div>
        <div class="dialog-body">
          <div class="form-item">
            <label>名称</label>
            <input v-model="editingCategory.name" placeholder="分类名称" />
          </div>
          <div class="form-item">
            <label>图标</label>
            <input v-model="editingCategory.icon" placeholder="emoji 图标" maxlength="2" />
          </div>
          <div class="form-item">
            <label>颜色</label>
            <input type="color" v-model="editingCategory.color" class="color-input" />
            <div class="color-picker">
              <div
                v-for="color in presetColors"
                :key="color"
                class="color-option"
                :class="{ selected: editingCategory.color === color }"
                :style="{ backgroundColor: color }"
                @click="editingCategory.color = color"
              ></div>
            </div>
          </div>
          <div class="form-item">
            <label>排序</label>
            <input type="number" v-model="editingCategory.sortOrder" />
          </div>
        </div>
        <div class="dialog-footer">
          <button class="btn-cancel" @click="dialogVisible = false">取消</button>
          <button class="btn-confirm" @click="saveCategory">确定</button>
        </div>
      </div>
    </div>

    <!-- Merge dialog -->
    <div v-if="mergeDialogVisible" class="dialog-overlay" @click.self="mergeDialogVisible = false">
      <div class="dialog">
        <div class="dialog-header">
          <h3>合并分类</h3>
          <button class="close-btn" @click="mergeDialogVisible = false">×</button>
        </div>
        <div class="dialog-body">
          <p class="merge-info">将「{{ mergeSourceCategory?.name }}」合并到：</p>
          <div class="form-item">
            <select v-model="mergeTargetId" class="merge-select">
              <option value="">选择目标分类</option>
              <option
                v-for="cat in flattenCategories(folders)"
                :key="cat.id"
                :value="cat.id"
                :disabled="cat.id === mergeSourceCategory?.id"
              >
                {{ cat.icon }} {{ cat.name }}
              </option>
            </select>
          </div>
        </div>
        <div class="dialog-footer">
          <button class="btn-cancel" @click="mergeDialogVisible = false">取消</button>
          <button class="btn-confirm" :disabled="!mergeTargetId" @click="confirmMerge">确定合并</button>
        </div>
      </div>
    </div>

    <!-- Preview dialog -->
    <div v-if="showPreview" class="dialog-overlay" @click.self="closePreview">
      <div class="dialog">
        <div class="dialog-header">
          <h3>分类预览：{{ previewCategory?.name }}</h3>
          <button class="close-btn" @click="closePreview">×</button>
        </div>
        <div class="dialog-body">
          <div class="preview-info">
            <span class="preview-icon">{{ previewCategory?.icon }}</span>
            <span class="preview-name">{{ previewCategory?.name }}</span>
          </div>
          <div class="preview-section">
            <h4>知识列表</h4>
            <div v-if="previewKnowledge.length === 0" class="empty-preview">
              暂无知识
            </div>
            <div v-else class="preview-list">
              <div
                v-for="knowledge in previewKnowledge"
                :key="knowledge.id"
                class="preview-item"
              >
                {{ knowledge.title }}
              </div>
            </div>
          </div>
        </div>
        <div class="dialog-footer">
          <button class="btn-cancel" @click="closePreview">关闭</button>
        </div>
      </div>
    </div>

    <!-- Batch create dialog -->
    <div v-if="showBatchCreate" class="dialog-overlay" @click.self="showBatchCreate = false">
      <div class="dialog">
        <div class="dialog-header">
          <h3>批量创建分类</h3>
          <button class="close-btn" @click="showBatchCreate = false">×</button>
        </div>
        <div class="dialog-body">
          <p class="batch-hint">每行一个分类名称</p>
          <textarea
            v-model="batchCreateNames"
            class="batch-textarea"
            placeholder="分类名称1&#10;分类名称2&#10;分类名称3"
            rows="8"
          ></textarea>
        </div>
        <div class="dialog-footer">
          <button class="btn-cancel" @click="showBatchCreate = false">取消</button>
          <button class="btn-confirm" @click="confirmBatchCreate">确定创建</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.category-tree {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.tree-toolbar {
  padding: 8px;
  border-bottom: 1px solid var(--border-color);
}

.tree-search {
  padding: 8px;
  position: relative;
}

.search-input {
  width: 100%;
  padding: 8px;
  border: 1px solid var(--border-color);
  border-radius: 4px;
  background: var(--bg-primary);
  color: var(--text-primary);
  box-sizing: border-box;
}

.search-input::placeholder {
  color: var(--text-muted);
}

.search-results {
  position: absolute;
  left: 8px;
  right: 8px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  max-height: 200px;
  overflow-y: auto;
  z-index: 100;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.search-result-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: pointer;
}

.search-result-item:hover {
  background: var(--bg-hover);
}

.search-icon {
  font-size: 14px;
}

.search-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.btn-new {
  width: 100%;
  padding: 8px;
  background: var(--accent-bg);
  border: 1px solid var(--accent);
  border-radius: 4px;
  color: var(--accent);
  cursor: pointer;
}

.btn-batch-tool {
  width: 100%;
  padding: 8px;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  color: var(--text-secondary);
  cursor: pointer;
  font-size: 12px;
}

.btn-batch-tool:hover {
  background: var(--bg-hover);
}

.btn-new:hover {
  background: var(--accent);
  color: var(--bg-primary);
}

.tree-content {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.folder-row {
  display: flex;
  align-items: center;
  padding: 8px;
  border-radius: 4px;
  cursor: pointer;
  gap: 6px;
  position: relative;
}

.folder-row:hover {
  background: var(--bg-hover);
}

.folder-row.selected {
  background: var(--accent-bg);
  color: var(--accent);
}

.folder-row.drag-over {
  background: var(--accent-bg);
  outline: 2px dashed var(--accent);
}

.folder-toggle {
  width: 16px;
  height: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  cursor: pointer;
  transition: transform 0.2s;
}

.folder-toggle.expanded {
  transform: rotate(90deg);
}

.folder-toggle-placeholder {
  width: 16px;
}

.folder-icon {
  font-size: 14px;
}

.folder-color {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.folder-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.folder-count {
  color: var(--text-muted);
  font-size: 12px;
}

.context-menu {
  position: fixed;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  padding: 4px 0;
  z-index: 1000;
  min-width: 120px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.context-menu-item {
  padding: 8px 16px;
  cursor: pointer;
  font-size: 14px;
}

.context-menu-item:hover {
  background: var(--bg-hover);
}

.context-menu-item.danger {
  color: var(--danger);
}

.dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1001;
}

.dialog {
  background: var(--bg-card);
  border-radius: 8px;
  width: 400px;
  max-width: 90vw;
}

.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid var(--border-color);
}

.dialog-header h3 {
  margin: 0;
}

.close-btn {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: var(--text-secondary);
}

.dialog-body {
  padding: 16px;
}

.form-item {
  margin-bottom: 12px;
}

.form-item label {
  display: block;
  margin-bottom: 4px;
  color: var(--text-secondary);
  font-size: 12px;
}

.form-item input {
  width: 100%;
  padding: 8px;
  border: 1px solid var(--border-color);
  border-radius: 4px;
  background: var(--bg-primary);
  color: var(--text-primary);
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 16px;
  border-top: 1px solid var(--border-color);
}

.btn-cancel, .btn-confirm {
  padding: 8px 16px;
  border-radius: 4px;
  cursor: pointer;
}

.btn-cancel {
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  color: var(--text-primary);
}

.btn-confirm {
  background: var(--accent);
  border: none;
  color: var(--bg-primary);
}

.loading {
  padding: 20px;
  text-align: center;
  color: var(--text-muted);
}

.tree-toolbar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.batch-info {
  flex: 1;
  display: flex;
  align-items: center;
  color: var(--text-secondary);
  font-size: 14px;
}

.btn-batch {
  padding: 8px 12px;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  color: var(--text-primary);
  cursor: pointer;
  font-size: 12px;
}

.btn-batch:hover {
  background: var(--bg-hover);
}

.btn-trash {
  padding: 8px 12px;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  color: var(--text-secondary);
  cursor: pointer;
  font-size: 12px;
}

.btn-trash:hover {
  background: var(--bg-hover);
  color: var(--danger);
}

.batch-checkbox {
  width: 16px;
  height: 16px;
  margin-right: 4px;
  cursor: pointer;
}

.trash-panel {
  position: fixed;
  top: 0;
  right: 0;
  width: 360px;
  height: 100%;
  background: var(--bg-card);
  border-left: 1px solid var(--border-color);
  z-index: 1002;
  display: flex;
  flex-direction: column;
  box-shadow: -2px 0 8px rgba(0, 0, 0, 0.15);
}

.trash-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid var(--border-color);
}

.trash-header h3 {
  margin: 0;
}

.trash-content {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.empty-trash {
  padding: 20px;
  text-align: center;
  color: var(--text-muted);
}

.trash-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  border-bottom: 1px solid var(--border-color);
}

.trash-item:last-child {
  border-bottom: none;
}

.trash-icon {
  font-size: 14px;
}

.trash-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trash-actions {
  display: flex;
  gap: 4px;
}

.btn-restore, .btn-permanent-delete {
  padding: 4px 8px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
}

.btn-restore {
  background: var(--accent-bg);
  border: 1px solid var(--accent);
  color: var(--accent);
}

.btn-restore:hover {
  background: var(--accent);
  color: var(--bg-primary);
}

.btn-permanent-delete {
  background: transparent;
  border: 1px solid var(--danger);
  color: var(--danger);
}

.btn-permanent-delete:hover {
  background: var(--danger);
  color: var(--bg-primary);
}

.merge-info {
  margin: 0 0 16px 0;
  color: var(--text-secondary);
}

.merge-select {
  width: 100%;
  padding: 8px;
  border: 1px solid var(--border-color);
  border-radius: 4px;
  background: var(--bg-primary);
  color: var(--text-primary);
  font-size: 14px;
}

.btn-confirm:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.color-input {
  width: 60px;
  height: 32px;
  padding: 2px;
  border: 1px solid var(--border-color);
  border-radius: 4px;
  cursor: pointer;
}

.color-picker {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.color-option {
  width: 24px;
  height: 24px;
  border-radius: 4px;
  cursor: pointer;
  border: 2px solid transparent;
  transition: transform 0.1s, border-color 0.1s;
}

.color-option:hover {
  transform: scale(1.1);
}

.color-option.selected {
  border-color: var(--text-primary);
}

.description-tooltip {
  position: absolute;
  left: 100%;
  top: 0;
  margin-left: 8px;
  padding: 8px 12px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  max-width: 300px;
  z-index: 100;
  white-space: pre-wrap;
  box-shadow: 0 4px 12px rgba(0,0,0,0.3);
}

.sync-notification {
  position: fixed;
  top: 20px;
  left: 50%;
  transform: translateX(-50%);
  padding: 12px 24px;
  background: var(--accent);
  color: var(--bg-primary);
  border-radius: 8px;
  cursor: pointer;
  z-index: 1000;
  box-shadow: 0 4px 12px rgba(0,0,0,0.3);
}

.pinned-section {
  padding: 8px;
  border-bottom: 1px solid var(--border-color);
}

.pinned-header {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 8px;
}

.pinned-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  border-radius: 4px;
  cursor: pointer;
}

.pinned-item:hover {
  background: var(--bg-hover);
}

.pinned-item.selected {
  background: var(--accent-bg);
}

.pinned-icon {
  font-size: 14px;
}

.pinned-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pin-btn {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 12px;
  opacity: 0.6;
}

.pin-btn:hover {
  opacity: 1;
}

.pin-btn.pinned {
  opacity: 1;
}

.preview-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  background: var(--bg-tertiary);
  border-radius: 4px;
  margin-bottom: 16px;
}

.preview-icon {
  font-size: 20px;
}

.preview-name {
  font-weight: 500;
}

.preview-section h4 {
  margin: 0 0 8px 0;
  color: var(--text-secondary);
  font-size: 12px;
}

.empty-preview {
  padding: 20px;
  text-align: center;
  color: var(--text-muted);
}

.preview-list {
  max-height: 200px;
  overflow-y: auto;
}

.preview-item {
  padding: 8px 12px;
  border-bottom: 1px solid var(--border-color);
}

.preview-item:last-child {
  border-bottom: none;
}

.batch-hint {
  margin: 0 0 12px 0;
  color: var(--text-secondary);
  font-size: 12px;
}

.batch-textarea {
  width: 100%;
  padding: 8px;
  border: 1px solid var(--border-color);
  border-radius: 4px;
  background: var(--bg-primary);
  color: var(--text-primary);
  font-family: inherit;
  resize: vertical;
  box-sizing: border-box;
}
</style>