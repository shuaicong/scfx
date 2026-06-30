import { ref, reactive, onMounted } from 'vue'
import { reportApi } from '@/api/report'

export interface ReportFilter {
  page: number
  size: number
  variety: string
  status: string
  keyword: string
}

export function useReportList() {
  const reports = ref<any[]>([])
  const loading = ref(false)
  const total = ref(0)
  const filters = reactive<ReportFilter>({
    page: 1,
    size: 15,
    variety: '',
    status: '',
    keyword: ''
  })

  async function load() {
    loading.value = true
    try {
      const params: Record<string, any> = { page: filters.page, size: filters.size }
      if (filters.variety) params.variety = filters.variety
      if (filters.status) params.status = filters.status
      if (filters.keyword) params.keyword = filters.keyword
      const res = await reportApi.list(params)
      const data = (res as any).data || {}
      reports.value = data.records || data || []
      total.value = data.total || reports.value.length
    } catch (e) {
      console.error('加载报告列表失败:', e)
      reports.value = []
    } finally {
      loading.value = false
    }
  }

  async function remove(id: number) {
    await reportApi.delete(id)
    load()
  }

  onMounted(load)

  return { reports, loading, total, filters, load, remove }
}
