<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { debounce } from 'lodash-es'
import { ElMessage } from 'element-plus'
import { categoryApi, type Category } from '@/api/category'
import { knowledgeApi } from '@/api/knowledge'
import { knowledgeCategoryApi } from '@/api/knowledge-category'

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
    const remoteVersion = res.data?.data?.version ?? 0
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

const getCategoryDepth = (category: Category): number => {
  let depth = 0
  let current = category
  const all = flattenCategories(folders.value)
  while (current.parentId) {
    depth++
    current = all.find(c => c.id === current.parentId) || current
    if (depth > 10) break // prevent infinite loop
  }
  return depth
}

const getDepthClass = (category: Category): string => {
  const depth = getCategoryDepth(category)
  if (depth >= 3) return 'depth-3'
  if (depth >= 2) return 'depth-2'
  if (depth >= 1) return 'depth-1'
  return ''
}

const getDepthClassByDepth = (depth: number): string => {
  if (depth >= 3) return 'depth-3'
  if (depth >= 2) return 'depth-2'
  if (depth >= 1) return 'depth-1'
  return ''
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
    c.name.toLowerCase().includes(query) ||
    (c.description && c.description.toLowerCase().includes(query))
  )
})

const flattenCategories = (cats: Category[]): Category[] => {
  return cats.flatMap(c => [c, ...flattenCategories(c.children || [])])
}

// Flatten tree to array with depth for recursive rendering
interface FlattenedCategory {
  category: Category
  depth: number
}

const flattenWithDepth = (cats: Category[], depth: number = 0): FlattenedCategory[] => {
  return cats.flatMap(c => [
    { category: c, depth },
    ...flattenWithDepth(c.children || [], depth + 1)
  ])
}

// Get visible categories based on expanded state
const visibleCategories = computed<FlattenedCategory[]>(() => {
  const result: FlattenedCategory[] = []

  const walk = (cats: Category[], depth: number, parentExpanded: boolean) => {
    for (const cat of cats) {
      const isExpanded = expandedIds.value.has(cat.id)
      // Only include if parent is expanded (or it's root level, depth === 0)
      if (depth === 0 || parentExpanded) {
        result.push({ category: cat, depth })
      }
      // Recurse into children only if this category is expanded
      if (cat.children?.length && isExpanded) {
        walk(cat.children, depth + 1, true)
      }
    }
  }

  walk(folders.value, 0, false)
  return result
})

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

// Delete confirmation dialog state
const deleteDialogVisible = ref(false)
const deleteTargetId = ref<number | null>(null)
const deleteTargetName = ref('')

// Batch selection state
const selectedIds = ref<Set<number>>(new Set())
const batchMode = ref(false)

// Color picker preset colors
const presetColors = [
  '#58A6FF', '#3FB950', '#F0883E', '#FF6B6B',
  '#FFD93D', '#A371F7', '#8B949E', '#F5C87A'
]

// Default emoji icons
const defaultEmojis = [
  '📁', '📂', '📃', '📄', '📅', '📆', '📇', '📈',
  '📉', '📊', '📋', '📌', '📍', '📎', '📏', '📐',
  '🗂️', '🗃️', '🗄️', '🗑️', '📑', '🔖', '🏷️', '🏷️',
  '📦', '📧', '📨', '📩', '📪', '📫', '📬', '📭',
  '💼', '📁', '📋', '📌', '📎', '🔗', '📎', '🖇️',
  '💰', '💵', '💴', '💶', '💷', '💸', '💳', '🧾',
  '📝', '📜', '📃', '📄', '📑', '📋', '🗒️', '🗓️',
  '🌐', '🌍', '🌎', '🌏', '🌐', '🗺️', '🧭', '🧭',
  '📡', '🔍', '🔎', '🔏', '🔐', '🔑', '🔒', '🔓',
]

// Description tooltip state
const hoveredCategoryId = ref<number | null>(null)

// More dropdown state
const moreDropdownOpen = ref(false)

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

// ============================================
// 10. 分类知识自动标签
// ============================================
const autoTagging = async (knowledgeId: number) => {
  // 调用后端 AI 自动提取标签接口
  // POST /api/knowledge/{id}/auto-tag
  ElMessage.info('AI 自动标签功能开发中')
}

// ============================================
// 向量化操作
// ============================================
const handleVectorize = async (category: Category) => {
  contextMenuVisible.value = false
  ElMessage.info(`开始对分类「${category.name}」下的知识进行向量化...`)
  // TODO: 调用后端 API 发起向量化任务
  // POST /api/collection/vectorize-category
}

// ============================================
// 11. 分类模板市场
// ============================================
// 12. 分类间知识移动历史
// ============================================
const showMoveHistory = ref(false)
const moveHistory = ref<any[]>([])

const loadMoveHistory = async (knowledgeId: number) => {
  try {
    const res = await knowledgeCategoryApi.getMoveHistory(knowledgeId)
    moveHistory.value = res.data.data || []
    showMoveHistory.value = true
  } catch (e) {
    console.error('Failed to load move history:', e)
    ElMessage.error('加载移动历史失败')
  }
}

// ============================================
// 13. 分类季节性提醒
// ============================================
const checkSeasonalStatus = (category: Category): { active: boolean; message: string } => {
  if (!category.activeSeasonStart || !category.activeSeasonEnd) {
    return { active: true, message: '' }
  }

  const now = new Date()
  const month = now.getMonth() + 1
  const start = parseInt(category.activeSeasonStart)
  const end = parseInt(category.activeSeasonEnd)

  if (start <= end) {
    if (month >= start && month <= end) {
      return { active: true, message: '' }
    }
  } else {
    // 跨年情况，如 9-2 月
    if (month >= start || month <= end) {
      return { active: true, message: '' }
    }
  }

  return {
    active: false,
    message: `此分类将在 ${category.activeSeasonStart} 月自动展开`
  }
}

// ============================================
// 14. 分类智能推荐
// ============================================
const recommendCategories = async (knowledgeTitle: string, knowledgeContent: string) => {
  // 调用后端智能推荐接口
  // POST /api/knowledge/{id}/recommend-categories
  ElMessage.info('智能推荐功能开发中，根据标题和内容自动推荐分类')
}

// ============================================
// 16. 分类批量重命名
// ============================================
const showBatchRenameDialog = ref(false)
const batchRenameMode = ref<'prefix' | 'suffix' | 'replace'>('prefix')
const batchRenameValue = ref('')
const batchRenameReplace = ref('')

const findCategoryById = (id: number): Category | undefined => {
  return flattenCategories(folders.value).find(c => c.id === id)
}

const openBatchRenameDialog = () => {
  if (selectedIds.value.size === 0) {
    ElMessage.warning('请先选择要重命名的分类')
    return
  }
  showBatchRenameDialog.value = true
}

const confirmBatchRename = async () => {
  for (const id of selectedIds.value) {
    const category = findCategoryById(id)
    if (!category) continue

    let newName = category.name
    switch (batchRenameMode.value) {
      case 'prefix':
        newName = batchRenameValue.value + newName
        break
      case 'suffix':
        newName = newName + batchRenameValue.value
        break
      case 'replace':
        newName = newName.replace(batchRenameValue.value, batchRenameReplace.value)
        break
    }

    await categoryApi.update(id, { name: newName })
  }

  showBatchRenameDialog.value = false
  batchRenameValue.value = ''
  batchRenameReplace.value = ''
  clearSelection()
  await loadTree()
}

// ============================================
// 16. 分类知识完整性检查
// ============================================
const integrityCheck = async (knowledgeId: number) => {
  // 调用后端检查接口
  // GET /api/knowledge/{id}/integrity-check
  ElMessage.info('知识完整性检查功能开发中')
}

// ============================================
// 16. 分类收藏夹
// ============================================
const userFavorites = ref<Category[][]>([])
const MAX_FAVORITES = 5

const loadFavorites = () => {
  const saved = localStorage.getItem('category-favorites')
  if (saved) {
    try {
      userFavorites.value = JSON.parse(saved)
    } catch (e) {
      userFavorites.value = []
    }
  }
}

const saveFavorites = () => {
  localStorage.setItem('category-favorites', JSON.stringify(userFavorites.value))
}

const addToFavorites = (categoryIds: number[]) => {
  const categories = categoryIds.map(id => findCategoryById(id)).filter(Boolean) as Category[]
  userFavorites.value.push(categories)
  if (userFavorites.value.length > MAX_FAVORITES) {
    userFavorites.value.shift()
  }
  saveFavorites()
  ElMessage.success('已添加到收藏夹')
}

const loadFavoriteCategories = () => {
  // Load a saved favorite group by index
  const idx = parseInt(localStorage.getItem('current-favorite-index') || '0')
  if (userFavorites.value[idx]) {
    userFavorites.value[idx].forEach(cat => selectCategory(cat))
  }
}

// ============================================
// 16. 分类热点分析
// ============================================
const showHotAnalysis = ref(false)
const hotAnalysisData = ref<any[]>([])

const loadHotAnalysis = async () => {
  try {
    const res = await categoryApi.hotAnalysis()
    hotAnalysisData.value = res.data.data || []
    showHotAnalysis.value = true
  } catch (e) {
    console.error('Failed to load hot analysis:', e)
    ElMessage.error('加载热点分析失败')
  }
}

// ============================================
// 16. 分类合并建议
// ============================================
const showMergeSuggestions = ref(false)
const mergeSuggestions = ref<any[]>([])

const loadMergeSuggestions = async () => {
  try {
    const res = await categoryApi.mergeSuggestions()
    mergeSuggestions.value = res.data.data || []
    showMergeSuggestions.value = true
  } catch (e) {
    console.error('Failed to load merge suggestions:', e)
    ElMessage.error('加载合并建议失败')
  }
}

// Preview category state
const previewCategory = ref<Category | null>(null)
const previewKnowledge = ref<any[]>([])
const showPreview = ref(false)

// ============================================
// 1. 分类容量预警
// ============================================
const CAPACITY_WARNING_THRESHOLD = 100

const getCapacityWarning = (category: Category): string | undefined => {
  if ((category.knowledgeCount || 0) >= CAPACITY_WARNING_THRESHOLD) {
    return `该分类知识过多（${category.knowledgeCount}条），建议拆分为多个子分类`
  }
  return undefined
}

// ============================================
// 2. 排序方式切换
// ============================================
type SortMode = 'manual' | 'name' | 'count' | 'update'
const sortMode = ref<SortMode>('manual')

const sortedFolders = computed(() => {
  if (sortMode.value === 'manual') return folders.value

  const flat = [...flattenCategories(folders.value)]
  switch (sortMode.value) {
    case 'name':
      return flat.sort((a, b) => a.name.localeCompare(b.name))
    case 'count':
      return flat.sort((a, b) => (b.knowledgeCount || 0) - (a.knowledgeCount || 0))
    case 'update':
      return flat.sort((a, b) => new Date(b.updatedAt || 0).getTime() - new Date(a.updatedAt || 0).getTime())
    default:
      return folders.value
  }
})

// ============================================
// 3. 订阅通知
// ============================================
const subscribedCategoryIds = ref<Set<number>>(new Set())
const notifyCount = ref(0)

const toggleSubscription = async (categoryId: number) => {
  if (subscribedCategoryIds.value.has(categoryId)) {
    subscribedCategoryIds.value.delete(categoryId)
  } else {
    subscribedCategoryIds.value.add(categoryId)
    // 调用后端订阅接口
  }
  // Trigger reactivity
  subscribedCategoryIds.value = new Set(subscribedCategoryIds.value)
}

const checkNotifications = async () => {
  // 检查订阅的分类是否有新知识
  notifyCount.value = 0 // 重置
}

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

const savePermission = async () => {
  if (!permissionCategory.value) return
  try {
    await changePermission(permissionCategory.value.id, editingCategory.value.permissionLevel || 'public')
    permissionDialogVisible.value = false
  } catch (e) {
    console.error('Failed to save permission:', e)
  }
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

// 转义正则特殊字符
const escapeRegex = (str: string): string => str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

// 关键词高亮函数
const highlightText = (text: string, query: string): string => {
  if (!query) return text
  const regex = new RegExp(`(${escapeRegex(query)})`, 'gi')
  return text.replace(regex, '<mark>$1</mark>')
}

// Get parent category path for display
const getParentPath = (category: Category): string => {
  const all = flattenCategories(folders.value)
  const parts: string[] = []
  let current = category
  while (current.parentId) {
    const parent = all.find(c => c.id === current.parentId)
    if (parent) {
      parts.unshift(parent.name)
      current = parent
    } else {
      break
    }
  }
  return parts.length > 0 ? parts.join(' > ') : ''
}

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

// ============================================
// 5. 分类内知识搜索
// ============================================
const categorySearchQuery = ref('')
const categorySearchResults = ref<any[]>([])
const categorySearchCategoryId = ref<number | null>(null)
const showCategorySearch = ref(false)

const searchInCategory = async (categoryId: number, query: string) => {
  if (!query) {
    categorySearchResults.value = []
    return
  }
  try {
    // 调用后端 API 按分类筛选知识
    // GET /api/knowledge/category/{categoryId}/search?query=xxx
    const res = await knowledgeApi.searchInCategory(categoryId, query)
    categorySearchResults.value = res.data || []
  } catch (e) {
    console.error('Failed to search knowledge in category:', e)
    categorySearchResults.value = []
  }
}

const openCategorySearch = (category: Category) => {
  categorySearchCategoryId.value = category.id
  categorySearchQuery.value = ''
  categorySearchResults.value = []
  showCategorySearch.value = true
  contextMenuVisible.value = false
}

const debouncedCategorySearch = debounce((categoryId: number, query: string) => {
  searchInCategory(categoryId, query)
}, 300)

// ============================================
// 6. 分类权限管理
// ============================================
const PERMISSION_LEVELS = ['public', 'team', 'private'] as const

const changePermission = async (categoryId: number, level: string) => {
  try {
    await categoryApi.update(categoryId, { permissionLevel: level })
    await loadTree()
    ElMessage.success('权限已更新')
  } catch (e) {
    console.error('Failed to change permission:', e)
    ElMessage.error('权限更新失败')
  }
}

const showPermissionDialog = (category: Category) => {
  permissionCategory.value = category
  editingCategory.value = { ...category }
  permissionDialogVisible.value = true
  contextMenuVisible.value = false
}

const permissionCategory = ref<Category | null>(null)
const permissionDialogVisible = ref(false)

// ============================================
// 7. 知识批量移动
// ============================================
const batchMoveTargetId = ref<number | null>(null)
const batchMoveDialogVisible = ref(false)

const openBatchMoveDialog = () => {
  batchMoveTargetId.value = null
  batchMoveDialogVisible.value = true
}

const confirmBatchMove = async () => {
  if (!batchMoveTargetId.value || selectedIds.value.size === 0) return
  try {
    const knowledgeIds = [...selectedIds.value]
    await knowledgeCategoryApi.batchMove(knowledgeIds, batchMoveTargetId.value)
    ElMessage.success(`已移动 ${knowledgeIds.length} 条知识`)
    clearSelection()
    await loadTree()
    batchMoveDialogVisible.value = false
  } catch (e) {
    console.error('Failed to batch move knowledge:', e)
    ElMessage.error('批量移动失败')
  }
}

// ============================================
// 8. 分类数据对比
// ============================================
const compareMode = ref(false)
const compareCategories = ref<Category[]>([])
const compareDialogVisible = ref(false)

const toggleCompareMode = () => {
  compareMode.value = !compareMode.value
  if (!compareMode.value) {
    compareCategories.value = []
  }
}

const addToCompare = (category: Category) => {
  if (compareCategories.value.length >= 5) {
    ElMessage.warning('最多对比 5 个分类')
    return
  }
  if (!compareCategories.value.find(c => c.id === category.id)) {
    compareCategories.value.push(category)
  }
}

const removeFromCompare = (categoryId: number) => {
  compareCategories.value = compareCategories.value.filter(c => c.id !== categoryId)
}

const generateCompareTable = () => {
  // 生成对比表格数据
  return compareCategories.value.map(cat => ({
    name: cat.name,
    icon: cat.icon,
    knowledgeCount: cat.knowledgeCount || 0,
    description: cat.description || '-',
    permissionLevel: cat.permissionLevel || 'public',
    createdAt: cat.createdAt || '-',
    updatedAt: cat.updatedAt || '-'
  }))
}

const openCompareDialog = () => {
  if (compareCategories.value.length < 2) {
    ElMessage.warning('请至少选择 2 个分类进行对比')
    return
  }
  compareDialogVisible.value = true
}

// ============================================
// 9. 分类命名规范校验
// ============================================
const NAME_PATTERN = /^【.*】.*|^\[.*\].*$/

const validateCategoryName = (name: string): { valid: boolean; message: string } => {
  if (!name.trim()) {
    return { valid: false, message: '名称不能为空' }
  }
  if (NAME_PATTERN.test(name)) {
    return { valid: true, message: '符合规范' }
  }
  // 允许保存，但给出格式建议
  return { valid: true, message: '建议格式：【类型】名称，如【价格】玉米日报' }
}

const formatName = (name: string): string => {
  // 不自动添加前缀，保持用户输入的原样
  return name
}

// Add validation feedback when editing
const nameValidationResult = computed(() => {
  return validateCategoryName(editingCategory.value.name || '')
})

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

const openMergeDialogFromSuggestion = (suggestion: any) => {
  const category = findCategoryById(suggestion.id)
  if (category) {
    openMergeDialog(category)
  }
  showMergeSuggestions.value = false
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

// Duplicate name check
const checkDuplicateNames = async () => {
  const res = await categoryApi.mergeSuggestions()
  const duplicates = res.data.data || []
  if (duplicates.length > 0) {
    ElMessage.warning(`发现 ${duplicates.length} 个同名分类，建议合并：${duplicates.map((d: any) => d.name).join('、')}`)
  } else {
    ElMessage.success('没有发现同名分类')
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
  editingCategory.value = { parentId, sortOrder: 99, icon: '📁' }
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
  // Validate name before saving
  const validation = validateCategoryName(editingCategory.value.name || '')
  if (!validation.valid) {
    ElMessage.warning(validation.message)
    return
  }

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
  const category = flattenCategories(folders.value).find(c => c.id === id)
  if (category) {
    deleteTargetId.value = id
    deleteTargetName.value = category.name
    deleteDialogVisible.value = true
  }
  contextMenuVisible.value = false
}

const confirmDelete = async () => {
  if (deleteTargetId.value) {
    // Check if category has knowledge
    const countRes = await knowledgeCategoryApi.getCountByCategory(deleteTargetId.value)
    const count = countRes.data?.data?.count || 0
    if (count > 0) {
      ElMessage.error(`该分类下还有 ${count} 条知识，请先删除或移动知识后再试`)
      deleteDialogVisible.value = false
      return
    }

    // Check if category has children
    const category = flattenCategories(folders.value).find(c => c.id === deleteTargetId.value)
    if (category && category.children && category.children.length > 0) {
      ElMessage.error(`该分类下还有 ${category.children.length} 个子分类，请先删除子分类后再试`)
      deleteDialogVisible.value = false
      return
    }

    await categoryApi.delete(deleteTargetId.value)
    pushUndo({
      type: 'delete',
      before: category || null,
      after: null,
      timestamp: Date.now()
    })
    await loadTree()
  }
  deleteDialogVisible.value = false
  deleteTargetId.value = null
}

// Context menu handlers
const showContextMenu = (event: MouseEvent, category: Category) => {
  event.preventDefault()
  contextMenuCategory.value = category

  // Calculate position with boundary check
  let x = event.clientX
  let y = event.clientY
  const menuWidth = 160
  const menuItemHeight = 36
  const menuItemCount = 16 // approximate number of items
  const menuHeight = menuItemHeight * menuItemCount

  // Adjust if menu would overflow right edge
  if (x + menuWidth > window.innerWidth) {
    x = window.innerWidth - menuWidth - 10
  }
  // Adjust if menu would overflow bottom edge - flip up
  if (y + menuHeight > window.innerHeight) {
    y = y - menuHeight - 20 // show above click point
    if (y < 10) {
      // If still off screen top, cap at 10
      y = 10
    }
  }

  contextMenuPosition.value = { x, y }
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
  loadFavorites()
  checkNotifications()
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
      <!-- Search bar -->
      <div class="toolbar-search">
        <svg class="search-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="11" cy="11" r="8"/>
          <line x1="21" y1="21" x2="16.65" y2="16.65"/>
        </svg>
        <input
          v-model="autocompleteQuery"
          @input="debouncedSearch(autocompleteQuery)"
          placeholder="搜索分类..."
          class="toolbar-search-input"
        />
        <div v-if="autocompleteResults.length > 0" class="toolbar-search-results">
          <div
            v-for="result in autocompleteResults"
            :key="result.id"
            class="toolbar-search-result-item"
            :class="{ selected: selectedCategory?.id === result.id }"
            @click="navigateToCategory(result); autocompleteQuery = ''; autocompleteResults = []"
          >
            <span class="search-icon-emoji">{{ result.icon }}</span>
            <div class="search-result-info">
              <span class="search-result-name" v-html="highlightText(result.name, autocompleteQuery)"></span>
              <span v-if="result.description" class="search-result-desc">
                {{ result.description.length > 50 ? result.description.slice(0, 50) + '...' : result.description }}
              </span>
              <span v-if="getParentPath(result)" class="search-result-path">{{ getParentPath(result) }}</span>
              <div class="search-result-meta">
                <span v-if="result.knowledgeCount" class="search-result-count">{{ result.knowledgeCount }} 条</span>
                <span class="search-result-depth">第 {{ getCategoryDepth(result) + 1 }} 层</span>
              </div>
            </div>
          </div>
        </div>
        <button class="btn-more" @click="moreDropdownOpen = !moreDropdownOpen">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="1"/>
            <circle cx="12" cy="5" r="1"/>
            <circle cx="12" cy="19" r="1"/>
          </svg>
          更多
        </button>
        <div class="more-menu" v-show="moreDropdownOpen">
          <button class="more-menu-item" @click="openCreateDialog(null); moreDropdownOpen = false">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="5" x2="12" y2="19"/>
              <line x1="5" y1="12" x2="19" y2="12"/>
            </svg>
            新建分类
          </button>
          <button class="more-menu-item" @click="openBatchCreateDialog(null); moreDropdownOpen = false">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="3" width="18" height="18" rx="2"/>
              <line x1="12" y1="8" x2="12" y2="16"/>
              <line x1="8" y1="12" x2="16" y2="12"/>
            </svg>
            批量创建
          </button>
          <div class="more-menu-divider"></div>
          <button class="more-menu-item" @click="checkDuplicateNames(); moreDropdownOpen = false">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <line x1="12" y1="8" x2="12" y2="12"/>
              <line x1="12" y1="16" x2="12.01" y2="16"/>
            </svg>
            重名检测
          </button>
          <button class="more-menu-item" @click="loadHotAnalysis(); moreDropdownOpen = false">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.38-.5-2-1-3-1.072-2.143-.224-4.054 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.153.433-2.294 1-3a2.5 2.5 0 0 0 2.5 2.5z"/>
            </svg>
            热点分析
          </button>
          <button class="more-menu-item" @click="loadMergeSuggestions(); moreDropdownOpen = false">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M16 3h5v5"/>
              <path d="M8 21H3v-5"/>
              <path d="M21 3l-9 9"/>
              <path d="M3 21l9-9"/>
            </svg>
            合并建议
          </button>
          <div class="more-menu-divider"></div>
          <button class="more-menu-item" @click="loadTrash(); moreDropdownOpen = false">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="3,6 5,6 21,6"/>
              <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
            </svg>
            回收站
          </button>
        </div>
      </div>
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

    <!-- Favorite categories -->
    <div v-if="userFavorites.length > 0" class="favorites-section">
      <div class="favorites-header">我的收藏 ({{ userFavorites.length }}/{{ MAX_FAVORITES }})</div>
      <div
        v-for="(favGroup, idx) in userFavorites"
        :key="idx"
        class="favorite-group"
      >
        <div class="favorite-group-items">
          <span
            v-for="cat in favGroup"
            :key="cat.id"
            class="favorite-item"
            :class="{ selected: selectedCategory?.id === cat.id }"
            :title="cat.name"
            @click="selectCategory(cat)"
          >
            <span class="favorite-icon">{{ cat.icon }}</span>
            <span class="favorite-name">{{ cat.name }}</span>
          </span>
        </div>
      </div>
    </div>

    <!-- Category list -->
    <div class="tree-content">
      <div v-if="loading" class="loading">加载中...</div>
      <template v-else>
        <div
          v-for="{ category, depth } in visibleCategories"
          :key="category.id"
          class="folder-wrapper"
        >
          <div
            class="folder-row"
            :class="[{ selected: selectedCategory?.id === category.id, 'drag-over': dropTargetId === category.id }, getDepthClassByDepth(depth)]"
            :style="{ paddingLeft: getIndent(depth) + 'px' }"
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
            <span
              v-if="category.children?.length"
              class="folder-toggle"
              :class="{ expanded: expandedIds.has(category.id) }"
              @click.stop="toggleExpand(category.id, $event)"
            >
              <svg v-if="expandedIds.has(category.id)" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="6,9 12,15 18,9"/>
              </svg>
              <svg v-else width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="9,6 15,12 9,18"/>
              </svg>
            </span>
            <span v-else class="folder-toggle-placeholder"></span>
            <div class="folder-label">
              <span class="folder-icon">{{ category.icon }}</span>
              <span class="folder-name" :title="category.name">{{ category.name }}</span>
            </div>
            <span class="folder-count" v-if="category.knowledgeCount">{{ category.knowledgeCount }}条</span>
            <button class="inline-add-btn" @click.stop="openCreateDialog(category.id)" title="添加子分类">+</button>
          </div>
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
      <div class="context-menu-item" @click="showPermissionDialog(contextMenuCategory!)">
        权限设置
      </div>
      <div class="context-menu-item" @click="addToFavorites([contextMenuCategory!.id])">
        添加到收藏夹
      </div>
      <div class="context-menu-item" @click="handleVectorize(contextMenuCategory!)">
        向量化
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
            <div v-if="nameValidationResult.message && dialogMode === 'create'" class="validation-hint">
              {{ nameValidationResult.message }}
            </div>
          </div>
          <div class="form-item">
            <label>图标</label>
            <div class="emoji-picker">
              <div
                v-for="emoji in defaultEmojis"
                :key="emoji"
                class="emoji-option"
                :class="{ selected: editingCategory.icon === emoji }"
                @click="editingCategory.icon = emoji"
              >
                {{ emoji }}
              </div>
            </div>
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

    <!-- Delete confirmation dialog -->
    <div v-if="deleteDialogVisible" class="dialog-overlay" @click.self="deleteDialogVisible = false">
      <div class="delete-dialog">
        <div class="delete-dialog-icon">⚠️</div>
        <h3 class="delete-dialog-title">删除确认</h3>
        <p class="delete-dialog-message">确定要删除分类「{{ deleteTargetName }}」吗？</p>
        <p class="delete-dialog-warning">删除后，该分类下的所有子分类也将被永久移除</p>
        <div class="delete-dialog-actions">
          <button class="btn-cancel" @click="deleteDialogVisible = false">取消</button>
          <button class="btn-delete" @click="confirmDelete">删除</button>
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
      <div class="dialog dialog-preview">
        <div class="dialog-header">
          <h3>分类预览：{{ previewCategory?.name }}</h3>
          <button class="close-btn" @click="closePreview">×</button>
        </div>
        <div class="dialog-body">
          <div class="preview-info">
            <span class="preview-icon">{{ previewCategory?.icon }}</span>
            <span class="preview-name">{{ previewCategory?.name }}</span>
          </div>
          <div class="preview-details">
            <div class="preview-detail-item" v-if="previewCategory?.description">
              <span class="preview-label">描述</span>
              <span class="preview-value">{{ previewCategory?.description }}</span>
            </div>
            <div class="preview-detail-item">
              <span class="preview-label">知识数量</span>
              <span class="preview-value">{{ previewCategory?.knowledgeCount || 0 }} 条</span>
            </div>
            <div class="preview-detail-item">
              <span class="preview-label">子分类</span>
              <span class="preview-value">{{ previewCategory?.children?.length || 0 }} 个</span>
            </div>
            <div class="preview-detail-item" v-if="previewCategory && getParentPath(previewCategory)">
              <span class="preview-label">父分类</span>
              <span class="preview-value">{{ getParentPath(previewCategory!) }}</span>
            </div>
            <div class="preview-detail-item">
              <span class="preview-label">创建时间</span>
              <span class="preview-value">{{ previewCategory?.createdAt || '-' }}</span>
            </div>
            <div class="preview-detail-item">
              <span class="preview-label">最后更新</span>
              <span class="preview-value">{{ previewCategory?.updatedAt || '-' }}</span>
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

    <!-- Permission dialog -->
    <div v-if="permissionDialogVisible" class="dialog-overlay" @click.self="permissionDialogVisible = false">
      <div class="dialog">
        <div class="dialog-header">
          <h3>权限设置：{{ permissionCategory?.name }}</h3>
          <button class="close-btn" @click="permissionDialogVisible = false">×</button>
        </div>
        <div class="dialog-body">
          <div class="form-item">
            <label>权限级别</label>
            <div class="permission-options">
              <label v-for="level in PERMISSION_LEVELS" :key="level" class="permission-option">
                <input
                  type="radio"
                  :value="level"
                  v-model="editingCategory.permissionLevel"
                />
                <span class="permission-label">
                  {{ level === 'public' ? '公开' : level === 'team' ? '团队' : '私有' }}
                </span>
              </label>
            </div>
          </div>
          <div class="permission-desc">
            <p v-if="editingCategory.permissionLevel === 'public'">公开：所有用户都可以查看此分类及其知识</p>
            <p v-if="editingCategory.permissionLevel === 'team'">团队：仅团队成员可以查看此分类及其知识</p>
            <p v-if="editingCategory.permissionLevel === 'private'">私有：仅创建者可以查看此分类及其知识</p>
          </div>
        </div>
        <div class="dialog-footer">
          <button class="btn-cancel" @click="permissionDialogVisible = false">取消</button>
          <button class="btn-confirm" @click="savePermission">保存</button>
        </div>
      </div>
    </div>

    <!-- Category search dialog -->
    <div v-if="showCategorySearch" class="dialog-overlay" @click.self="showCategorySearch = false">
      <div class="dialog dialog-wide">
        <div class="dialog-header">
          <h3>搜索分类知识</h3>
          <button class="close-btn" @click="showCategorySearch = false">×</button>
        </div>
        <div class="dialog-body">
          <div class="search-input-wrapper">
            <input
              v-model="categorySearchQuery"
              @input="debouncedCategorySearch(categorySearchCategoryId!, categorySearchQuery)"
              placeholder="输入关键词搜索..."
              class="search-input"
            />
          </div>
          <div v-if="categorySearchResults.length > 0" class="search-results-list">
            <div
              v-for="item in categorySearchResults"
              :key="item.id"
              class="search-result-item"
            >
              <span class="result-title">{{ item.title }}</span>
              <span class="result-snippet">{{ item.snippet || item.content?.substring(0, 100) }}...</span>
            </div>
          </div>
          <div v-else-if="categorySearchQuery && !categorySearchResults.length" class="empty-results">
            未找到匹配的知识
          </div>
          <div v-else class="empty-results">
            输入关键词开始搜索
          </div>
        </div>
        <div class="dialog-footer">
          <button class="btn-cancel" @click="showCategorySearch = false">关闭</button>
        </div>
      </div>
    </div>

    <!-- Batch move dialog -->
    <div v-if="batchMoveDialogVisible" class="dialog-overlay" @click.self="batchMoveDialogVisible = false">
      <div class="dialog">
        <div class="dialog-header">
          <h3>批量移动知识</h3>
          <button class="close-btn" @click="batchMoveDialogVisible = false">×</button>
        </div>
        <div class="dialog-body">
          <p class="batch-hint">将 {{ selectedIds.size }} 条知识移动到：</p>
          <div class="form-item">
            <select v-model="batchMoveTargetId" class="merge-select">
              <option :value="null">选择目标分类</option>
              <option
                v-for="cat in flattenCategories(folders)"
                :key="cat.id"
                :value="cat.id"
              >
                {{ cat.icon }} {{ cat.name }}
              </option>
            </select>
          </div>
        </div>
        <div class="dialog-footer">
          <button class="btn-cancel" @click="batchMoveDialogVisible = false">取消</button>
          <button class="btn-confirm" :disabled="!batchMoveTargetId" @click="confirmBatchMove">确定移动</button>
        </div>
      </div>
    </div>

    <!-- Compare dialog -->
    <div v-if="compareDialogVisible" class="dialog-overlay" @click.self="compareDialogVisible = false">
      <div class="dialog dialog-wide">
        <div class="dialog-header">
          <h3>分类对比</h3>
          <button class="close-btn" @click="compareDialogVisible = false">×</button>
        </div>
        <div class="dialog-body">
          <table class="compare-table">
            <thead>
              <tr>
                <th>对比项</th>
                <th v-for="cat in compareCategories" :key="cat.id">
                  {{ cat.icon }} {{ cat.name }}
                  <button class="btn-remove-compare" @click="removeFromCompare(cat.id)">×</button>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>知识数量</td>
                <td v-for="cat in compareCategories" :key="cat.id">{{ cat.knowledgeCount || 0 }}</td>
              </tr>
              <tr>
                <td>描述</td>
                <td v-for="cat in compareCategories" :key="cat.id">{{ cat.description || '-' }}</td>
              </tr>
              <tr>
                <td>权限级别</td>
                <td v-for="cat in compareCategories" :key="cat.id">
                  {{ cat.permissionLevel === 'public' ? '公开' : cat.permissionLevel === 'team' ? '团队' : '私有' }}
                </td>
              </tr>
              <tr>
                <td>创建时间</td>
                <td v-for="cat in compareCategories" :key="cat.id">{{ cat.createdAt || '-' }}</td>
              </tr>
              <tr>
                <td>更新时间</td>
                <td v-for="cat in compareCategories" :key="cat.id">{{ cat.updatedAt || '-' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="dialog-footer">
          <button class="btn-cancel" @click="compareDialogVisible = false">关闭</button>
        </div>
      </div>
    </div>

    <!-- Move history dialog -->
    <div v-if="showMoveHistory" class="dialog-overlay" @click.self="showMoveHistory = false">
      <div class="dialog">
        <div class="dialog-header">
          <h3>移动历史</h3>
          <button class="close-btn" @click="showMoveHistory = false">×</button>
        </div>
        <div class="dialog-body">
          <div v-if="moveHistory.length === 0" class="empty-history">
            暂无移动记录
          </div>
          <div v-else class="history-list">
            <div
              v-for="(item, index) in moveHistory"
              :key="index"
              class="history-item"
            >
              <span class="history-time">{{ item.createdAt }}</span>
              <span class="history-action">{{ item.action || '移动分类' }}</span>
            </div>
          </div>
        </div>
        <div class="dialog-footer">
          <button class="btn-cancel" @click="showMoveHistory = false">关闭</button>
        </div>
      </div>
    </div>

    <!-- Batch rename dialog -->
    <div v-if="showBatchRenameDialog" class="dialog-overlay" @click.self="showBatchRenameDialog = false">
      <div class="dialog">
        <div class="dialog-header">
          <h3>批量重命名</h3>
          <button class="close-btn" @click="showBatchRenameDialog = false">×</button>
        </div>
        <div class="dialog-body">
          <p class="batch-hint">将 {{ selectedIds.size }} 个分类进行批量重命名</p>
          <div class="form-item">
            <label>重命名模式</label>
            <select v-model="batchRenameMode" class="batch-rename-mode">
              <option value="prefix">添加前缀</option>
              <option value="suffix">添加后缀</option>
              <option value="replace">替换文本</option>
            </select>
          </div>
          <div class="form-item">
            <label>{{ batchRenameMode === 'replace' ? '要替换的文本' : '添加的文本' }}</label>
            <input v-model="batchRenameValue" :placeholder="batchRenameMode === 'prefix' ? '输入前缀' : batchRenameMode === 'suffix' ? '输入后缀' : '输入要替换的文本'" />
          </div>
          <div v-if="batchRenameMode === 'replace'" class="form-item">
            <label>替换为</label>
            <input v-model="batchRenameReplace" placeholder="输入替换文本" />
          </div>
        </div>
        <div class="dialog-footer">
          <button class="btn-cancel" @click="showBatchRenameDialog = false">取消</button>
          <button class="btn-confirm" @click="confirmBatchRename">确定</button>
        </div>
      </div>
    </div>

    <!-- Hot analysis dialog -->
    <div v-if="showHotAnalysis" class="dialog-overlay" @click.self="showHotAnalysis = false">
      <div class="dialog dialog-wide">
        <div class="dialog-header">
          <h3>分类热点分析</h3>
          <button class="close-btn" @click="showHotAnalysis = false">×</button>
        </div>
        <div class="dialog-body">
          <div v-if="hotAnalysisData.length === 0" class="empty-history">
            暂无热点数据
          </div>
          <div v-else class="hot-analysis-list">
            <div
              v-for="(item, index) in hotAnalysisData"
              :key="index"
              class="hot-analysis-item"
            >
              <div class="hot-rank">{{ index + 1 }}</div>
              <div class="hot-info">
                <span class="hot-name">{{ item.name }}</span>
                <span class="hot-count">{{ item.count }} 条知识</span>
              </div>
            </div>
          </div>
        </div>
        <div class="dialog-footer">
          <button class="btn-cancel" @click="showHotAnalysis = false">关闭</button>
        </div>
      </div>
    </div>

    <!-- Merge suggestions dialog -->
    <div v-if="showMergeSuggestions" class="dialog-overlay" @click.self="showMergeSuggestions = false">
      <div class="dialog dialog-wide">
        <div class="dialog-header">
          <h3>分类合并建议</h3>
          <button class="close-btn" @click="showMergeSuggestions = false">×</button>
        </div>
        <div class="dialog-body">
          <div v-if="mergeSuggestions.length === 0" class="empty-history">
            暂无合并建议
          </div>
          <div v-else class="merge-suggestions-list">
            <div
              v-for="(suggestion, index) in mergeSuggestions"
              :key="index"
              class="merge-suggestion-item"
            >
              <div class="merge-suggestion-info">
                <span class="merge-icon">{{ suggestion.icon || '📁' }}</span>
                <span class="merge-name">{{ suggestion.name }}</span>
                <span class="merge-count">{{ suggestion.count }} 条知识</span>
              </div>
              <button class="btn-merge" @click="openMergeDialogFromSuggestion(suggestion)">合并</button>
            </div>
          </div>
        </div>
        <div class="dialog-footer">
          <button class="btn-cancel" @click="showMergeSuggestions = false">关闭</button>
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
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid var(--border-color);
  gap: 12px;
  flex-shrink: 0;
}

.toolbar-search {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  max-width: 320px;
}

.toolbar-search-input {
  width: 100%;
  padding: 8px 12px 8px 36px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-primary);
  color: var(--text-primary);
  font-size: 14px;
  transition: all 0.2s;
}

.toolbar-search-input:focus {
  border-color: var(--accent);
  outline: none;
  box-shadow: 0 0 0 3px var(--accent-bg);
}

.toolbar-search-input::placeholder {
  color: var(--text-muted);
}

.toolbar-search .search-icon {
  position: absolute;
  left: 10px;
  color: var(--text-muted);
  pointer-events: none;
}

.btn-more {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: transparent;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
  flex-shrink: 0;
}

.btn-more:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.more-menu {
  position: absolute;
  top: 100%;
  right: 0;
  min-width: 160px;
  background: #1e2128;
  border: 1px solid #404550;
  border-radius: 8px;
  padding: 6px;
  z-index: 100;
  box-shadow: 0 8px 24px rgba(0,0,0,0.4);
  margin-top: 8px;
}

.btn-more {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: transparent;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
  flex-shrink: 0;
}

.btn-more:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.toolbar-search-results {
  position: absolute;
  top: 100%;
  left: 0;
  width: 320px;
  margin-top: 6px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  max-height: 400px;
  overflow-y: auto;
  z-index: 200;
  box-shadow: 0 8px 24px rgba(0,0,0,0.3);
}

.toolbar-search-result-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.15s;
  border-bottom: 1px solid var(--border-color);
}

.toolbar-search-result-item:last-child {
  border-bottom: none;
}

.toolbar-search-result-item:hover,
.toolbar-search-result-item.selected {
  background: var(--bg-hover);
}

.search-icon-emoji {
  font-size: 20px;
  flex-shrink: 0;
}

.search-result-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.search-result-name {
  font-size: 14px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-primary);
}

.search-result-name mark {
  background: var(--accent-bg);
  color: var(--accent);
  padding: 0 2px;
  border-radius: 2px;
  font-weight: 600;
}

.search-result-desc {
  font-size: 12px;
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-top: 2px;
}

.search-result-path {
  font-size: 11px;
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.search-result-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 2px;
}

.search-result-count {
  font-size: 11px;
  color: var(--text-secondary);
  background: var(--bg-tertiary);
  padding: 1px 6px;
  border-radius: 8px;
  flex-shrink: 0;
}

.search-result-depth {
  font-size: 11px;
  color: var(--accent);
  background: var(--accent-bg);
  padding: 1px 6px;
  border-radius: 8px;
  flex-shrink: 0;
}

.more-dropdown {
  position: relative;
  z-index: 200;
}

.more-menu {
  position: absolute;
  top: 100%;
  left: auto;
  right: 0;
  min-width: 160px;
  background: #1e2128;
  border: 1px solid #404550;
  border-radius: 8px;
  padding: 6px;
  z-index: 100;
  box-shadow: 0 8px 24px rgba(0,0,0,0.4);
  margin-top: 8px;
}

.more-menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 12px;
  background: transparent;
  border: none;
  border-radius: 6px;
  color: #c0c0c0;
  font-size: 13px;
  cursor: pointer;
  text-align: left;
  transition: all 0.15s;
}

.more-menu-item:hover {
  background: #2a3040;
  color: var(--accent);
}

.more-menu-divider {
  height: 1px;
  background: #404550;
  margin: 6px 0;
}

.sort-select {
  padding: 6px 10px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
}

.sort-select:focus {
  outline: none;
  border-color: var(--accent);
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
  gap: 10px;
  padding: 12px 14px;
  cursor: pointer;
  font-size: 14px;
  transition: background 0.15s;
}

.search-result-item:hover {
  background: var(--bg-hover);
}

.search-icon {
  font-size: 16px;
  color: var(--text-secondary);
}

.search-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-primary);
}

.batch-info {
  flex: 1;
  display: flex;
  align-items: center;
  color: var(--text-secondary);
  font-size: 14px;
}

.btn-batch {
  padding: 6px 12px;
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

.tree-content {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.folder-wrapper {
  position: relative;
}

.folder-wrapper.sub::before {
  content: '';
  position: absolute;
  left: 8px;
  top: 0;
  bottom: 0;
  width: 1px;
  background: var(--border-color);
  opacity: 0.5;
}

.folder-row {
  display: flex;
  align-items: center;
  padding: 6px 8px;
  border-radius: 6px;
  cursor: pointer;
  gap: 6px;
  position: relative;
  transition: all 0.15s ease;
  font-size: 12px;
  min-width: 0;
}

.folder-row:hover {
  background: var(--bg-hover);
}

.folder-row.selected {
  background: var(--accent-bg);
}

.folder-row.depth-1 {
  background: rgba(245, 200, 122, 0.05);
}

.folder-row.depth-2 {
  background: rgba(245, 200, 122, 0.08);
}

.folder-row.depth-3 {
  background: rgba(245, 200, 122, 0.12);
}

.folder-row.drag-over {
  background: var(--accent-bg);
  outline: 2px dashed var(--accent);
}

.folder-toggle {
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  cursor: pointer;
  transition: all 0.15s;
  flex-shrink: 0;
}

.folder-toggle:hover {
  color: var(--accent);
}

.folder-toggle-placeholder {
  width: 18px;
  flex-shrink: 0;
}

.folder-label {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.folder-icon {
  font-size: 14px;
  flex-shrink: 0;
}

.folder-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
  color: var(--text-primary);
  min-width: 0;
}

.folder-row.selected .folder-name {
  color: var(--accent);
}

.folder-count {
  font-size: 10px;
  color: var(--text-muted);
  background: var(--bg-tertiary);
  padding: 1px 6px;
  border-radius: 8px;
  flex-shrink: 0;
  white-space: nowrap;
}

.inline-add-btn {
  display: none;
  width: 20px;
  height: 20px;
  padding: 0;
  background: var(--accent-bg);
  border: 1px solid var(--accent);
  border-radius: 4px;
  color: var(--accent);
  font-size: 14px;
  font-weight: bold;
  cursor: pointer;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all 0.15s;
}

.inline-add-btn:hover {
  background: var(--accent);
  color: #1a1f2e;
}

.folder-row:hover .inline-add-btn {
  display: flex;
}

.folder-icon {
  font-size: 12px;
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
  min-width: 0;
  max-width: 200px;
}

.folder-count {
  background: var(--bg-tertiary);
  color: var(--text-secondary);
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 10px;
  min-width: 20px;
  text-align: center;
}

.folder-row.selected .folder-count {
  background: var(--accent);
  color: var(--bg-primary);
}

.context-menu {
  position: fixed;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  padding: 4px 0;
  z-index: 1000;
  min-width: 120px;
  max-height: 70vh;
  overflow-y: auto;
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

.btn-delete {
  padding: 10px 24px;
  border-radius: 6px;
  border: none;
  background: #dc3545;
  color: white;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-delete:hover {
  background: #c82333;
}

.delete-dialog {
  background: var(--bg-card);
  border-radius: 12px;
  padding: 32px;
  width: 380px;
  max-width: 90vw;
  text-align: center;
}

.delete-dialog-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.delete-dialog-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 12px 0;
}

.delete-dialog-message {
  font-size: 14px;
  color: var(--text-primary);
  margin: 0 0 8px 0;
}

.delete-dialog-warning {
  font-size: 13px;
  color: #dc3545;
  margin: 0 0 24px 0;
}

.delete-dialog-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
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

.btn-compare {
  padding: 8px 12px;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  color: var(--text-secondary);
  cursor: pointer;
  font-size: 12px;
}

.btn-compare:hover {
  background: var(--bg-hover);
}

.btn-compare.active {
  background: var(--accent-bg);
  border-color: var(--accent);
  color: var(--accent);
}

.btn-export, .btn-import, .btn-duplicate {
  padding: 8px 12px;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  color: var(--text-secondary);
  cursor: pointer;
  font-size: 12px;
}

.btn-export:hover, .btn-import:hover, .btn-duplicate:hover {
  background: var(--bg-hover);
}

.btn-template {
  padding: 8px 12px;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  color: var(--text-secondary);
  cursor: pointer;
  font-size: 12px;
}

.btn-template:hover {
  background: var(--bg-hover);
  color: var(--accent);
}

.sort-select {
  padding: 8px 12px;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  color: var(--text-primary);
  font-size: 12px;
  cursor: pointer;
}

.sort-select:hover {
  background: var(--bg-hover);
}

.btn-notify {
  padding: 8px 12px;
  background: var(--danger);
  border: none;
  border-radius: 4px;
  color: white;
  cursor: pointer;
  font-size: 12px;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}

.seasonal-inactive {
  margin-left: 4px;
  cursor: help;
}

.capacity-warning {
  margin-left: 4px;
  cursor: help;
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

.emoji-picker {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.emoji-option {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  border-radius: 4px;
  cursor: pointer;
  border: 2px solid transparent;
  background: var(--bg-tertiary);
  transition: all 0.15s;
}

.emoji-option:hover {
  background: var(--bg-hover);
  transform: scale(1.1);
}

.emoji-option.selected {
  border-color: var(--accent);
  background: var(--accent-bg);
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

.favorites-section {
  padding: 8px;
  border-bottom: 1px solid var(--border-color);
}

.favorites-header {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 8px;
}

.favorite-group {
  margin-bottom: 4px;
}

.favorite-group-items {
  display: flex;
  gap: 4px;
  padding: 4px;
  background: var(--bg-tertiary);
  border-radius: 4px;
}

.favorite-item {
  cursor: pointer;
  font-size: 13px;
  padding: 6px 8px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 4px;
  transition: background 0.15s;
}

.favorite-icon {
  font-size: 14px;
}

.favorite-name {
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 80px;
}

.favorite-item:hover {
  background: var(--bg-hover);
}

.favorite-item.selected {
  background: var(--accent-bg);
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

.dialog-preview {
  width: 480px;
}

.preview-details {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.preview-detail-item {
  display: flex;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid var(--border-color);
}

.preview-detail-item:last-child {
  border-bottom: none;
}

.preview-label {
  width: 80px;
  flex-shrink: 0;
  color: var(--text-muted);
  font-size: 13px;
}

.preview-value {
  flex: 1;
  color: var(--text-primary);
  font-size: 13px;
  word-break: break-all;
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

.dialog-wide {
  width: 700px;
}

.permission-options {
  display: flex;
  gap: 16px;
}

.permission-option {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
}

.permission-label {
  font-size: 14px;
}

.permission-desc {
  margin-top: 12px;
  padding: 12px;
  background: var(--bg-tertiary);
  border-radius: 4px;
  font-size: 12px;
  color: var(--text-secondary);
}

.permission-desc p {
  margin: 4px 0;
}

.search-input-wrapper {
  margin-bottom: 16px;
}

.search-results-list {
  max-height: 300px;
  overflow-y: auto;
  border: 1px solid var(--border-color);
  border-radius: 4px;
}

.search-result-item {
  padding: 12px 14px;
  border-bottom: 1px solid var(--border-color);
  cursor: pointer;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 10px;
  transition: background 0.15s;
}

.search-result-item:last-child {
  border-bottom: none;
}

.search-result-item:hover {
  background: var(--bg-hover);
}

.result-title {
  display: block;
  font-weight: 500;
  margin-bottom: 4px;
}

.result-snippet {
  display: block;
  font-size: 12px;
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.empty-results {
  padding: 40px;
  text-align: center;
  color: var(--text-muted);
}

.compare-table {
  width: 100%;
  border-collapse: collapse;
}

.compare-table th,
.compare-table td {
  padding: 12px;
  border: 1px solid var(--border-color);
  text-align: left;
}

.compare-table th {
  background: var(--bg-tertiary);
  font-weight: 500;
}

.btn-remove-compare {
  background: none;
  border: none;
  cursor: pointer;
  color: var(--text-muted);
  margin-left: 8px;
  font-size: 16px;
}

.btn-remove-compare:hover {
  color: var(--danger);
}

.validation-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-muted);
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

.template-hint {
  color: var(--text-secondary);
  margin-bottom: 16px;
}

.template-import-section {
  margin-bottom: 16px;
}

.btn-import-template {
  padding: 8px 16px;
  background: var(--accent-bg);
  border: 1px solid var(--accent);
  border-radius: 4px;
  color: var(--accent);
  cursor: pointer;
}

.btn-import-template:hover {
  background: var(--accent);
  color: var(--bg-primary);
}

.template-tips {
  padding: 12px;
  background: var(--bg-tertiary);
  border-radius: 4px;
}

.template-tips h4 {
  margin: 0 0 8px 0;
  color: var(--text-secondary);
  font-size: 12px;
}

.template-tips p {
  margin: 0;
  color: var(--text-muted);
  font-size: 12px;
}

.empty-history {
  padding: 40px;
  text-align: center;
  color: var(--text-muted);
}

.history-list {
  max-height: 300px;
  overflow-y: auto;
}

.history-item {
  display: flex;
  justify-content: space-between;
  padding: 12px;
  border-bottom: 1px solid var(--border-color);
}

.history-item:last-child {
  border-bottom: none;
}

.history-time {
  color: var(--text-muted);
  font-size: 12px;
}

.history-action {
  color: var(--text-primary);
  font-size: 14px;
}

.btn-hot, .btn-merge {
  padding: 8px 12px;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  color: var(--text-secondary);
  cursor: pointer;
  font-size: 12px;
}

.btn-hot:hover, .btn-merge:hover {
  background: var(--bg-hover);
  color: var(--accent);
}

.batch-rename-mode {
  width: 100%;
  padding: 8px;
  border: 1px solid var(--border-color);
  border-radius: 4px;
  background: var(--bg-primary);
  color: var(--text-primary);
  font-size: 14px;
  cursor: pointer;
}

.hot-analysis-list {
  max-height: 400px;
  overflow-y: auto;
}

.hot-analysis-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-bottom: 1px solid var(--border-color);
}

.hot-analysis-item:last-child {
  border-bottom: none;
}

.hot-rank {
  width: 28px;
  height: 28px;
  background: var(--accent-bg);
  color: var(--accent);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 14px;
}

.hot-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.hot-name {
  font-weight: 500;
}

.hot-count {
  font-size: 12px;
  color: var(--text-muted);
}

.merge-suggestions-list {
  max-height: 400px;
  overflow-y: auto;
}

.merge-suggestion-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  border-bottom: 1px solid var(--border-color);
}

.merge-suggestion-item:last-child {
  border-bottom: none;
}

.merge-suggestion-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.merge-icon {
  font-size: 16px;
}

.merge-name {
  font-weight: 500;
}

.merge-count {
  font-size: 12px;
  color: var(--text-muted);
}

.btn-merge {
  padding: 4px 12px;
  background: var(--accent-bg);
  border: 1px solid var(--accent);
  border-radius: 4px;
  color: var(--accent);
  cursor: pointer;
  font-size: 12px;
}

.btn-merge:hover {
  background: var(--accent);
  color: var(--bg-primary);
}
</style>