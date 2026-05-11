<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { categoryApi, type Category } from '@/api/category'

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
    await categoryApi.create(editingCategory.value)
  } else if (editingCategory.value.id) {
    await categoryApi.update(editingCategory.value.id, editingCategory.value)
  }
  dialogVisible.value = false
  await loadTree()
}

// Delete category
const deleteCategory = async (id: number) => {
  if (confirm('确定要删除该分类吗？子分类也会被删除。')) {
    await categoryApi.delete(id)
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

  // Update parent and sort order to target category's values
  const updateData = {
    parentId: targetCategory.parentId,
    sortOrder: targetCategory.sortOrder
  }
  await categoryApi.update(draggedCategory.value.id, updateData)
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
})

onUnmounted(() => {
  document.removeEventListener('click', hideContextMenu)
})

defineExpose({ loadTree })
</script>

<template>
  <div class="category-tree" @click="hideContextMenu">
    <!-- Toolbar -->
    <div class="tree-toolbar">
      <button v-if="!batchMode" class="btn-new" @click="openCreateDialog(null)">+ 新建分类</button>
      <template v-else>
        <span class="batch-info">已选择 {{ selectedIds.size }} 项</span>
        <button class="btn-batch" @click="selectAll">全选</button>
        <button class="btn-batch" @click="clearSelection">取消选择</button>
      </template>
      <button class="btn-trash" @click="loadTrash">回收站</button>
    </div>

    <!-- Search input -->
    <div class="tree-search">
      <input
        v-model="searchQuery"
        placeholder="搜索分类..."
        class="search-input"
      />
      <div v-if="searchResults.length > 0" class="search-results">
        <div
          v-for="result in searchResults"
          :key="result.id"
          class="search-result-item"
          @click="navigateToCategory(result)"
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
          >
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
            <span class="folder-name">{{ category.name }}</span>
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
              >
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
                <span class="folder-name">{{ child.name }}</span>
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
      <div class="context-menu-item" @click="openEditDialog(contextMenuCategory!)">
        编辑
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
            <input type="color" v-model="editingCategory.color" />
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
</style>