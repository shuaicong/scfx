<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="960px"
    class="document-preview-dialog"
    @update:model-value="$emit('update:modelValue', $event)"
    @close="handleClose"
  >
    <div class="preview-container">
      <!-- AI 分析栏 -->
      <div v-if="showAiAnalysis" class="ai-analysis-bar">
        <button
          v-if="!aiAnalysisResult"
          class="ai-analyze-btn"
          :disabled="aiLoading"
          @click="analyzeDocument"
        >
          <span v-if="aiLoading" class="ai-loading"></span>
          <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none">
            <path d="M12 2L2 7L12 12L22 7L12 2Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            <path d="M2 17L12 22L22 17" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            <path d="M2 12L12 17L22 12" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          {{ aiLoading ? 'AI 分析中...' : '让 AI 分析此文档' }}
        </button>
        <div v-else class="ai-result">
          <div class="ai-result-header">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
              <path d="M12 2L2 7L12 12L22 7L12 2Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M2 17L12 22L22 17" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M2 12L12 17L22 12" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
            <span>AI 分析结果</span>
            <button class="ai-close-btn" @click="aiAnalysisResult = ''">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
                <path d="M18 6L6 18M6 6L18 18" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
              </svg>
            </button>
          </div>
          <div class="ai-result-content" v-html="aiAnalysisResult"></div>
        </div>
      </div>

      <!-- 加载状态 -->
      <div v-if="loading" class="loading-state">
        <div class="loading-spinner"></div>
        <span>正在加载...</span>
      </div>

      <!-- PDF 预览 -->
      <div v-else-if="isPdf" class="pdf-preview">
        <iframe
          v-if="pdfUrl"
          :src="pdfUrl"
          class="preview-iframe"
        />
        <div v-else class="preview-error">
          <p>无法加载 PDF</p>
        </div>
      </div>

      <!-- 图片预览 -->
      <div v-else-if="isImage" class="image-preview">
        <img :src="fullUrl" :alt="title" class="preview-image" />
      </div>

      <!-- Word 文档预览 (HTML 转换后) -->
      <div v-else-if="isWord" class="word-preview">
        <div v-if="wordHtml" class="word-content" v-html="wordHtml"></div>
        <div v-else class="preview-error">
          <p>无法加载 Word 文档</p>
        </div>
      </div>

      <!-- Excel 预览 (HTML 表格) -->
      <div v-else-if="isExcel" class="excel-preview">
        <div v-if="excelHtml" class="excel-content" v-html="excelHtml"></div>
        <div v-else class="preview-error">
          <p>无法加载 Excel</p>
        </div>
      </div>

      <!-- 文本文件预览 -->
      <div v-else-if="isText" class="text-preview">
        <pre class="text-content">{{ textContent }}</pre>
      </div>

      <!-- 不支持的格式 -->
      <div v-else class="unsupported-preview">
        <svg width="48" height="48" viewBox="0 0 48 48" fill="none">
          <path d="M28 4H12C10.9391 4 9.92178 4.42143 9.17157 5.17157C8.42143 5.92178 8 6.93913 8 8V40C8 41.0609 8.42143 42.0782 9.17157 42.8284C9.92178 43.5786 10.9391 44 12 44H36C37.0609 44 38.0782 43.5786 38.8284 42.8284C39.5786 42.0782 40 41.0609 40 40V16L28 4Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          <path d="M28 4V16H40" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          <path d="M24 26L32 34M32 26L24 34" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
        </svg>
        <p>该文件格式暂不支持预览</p>
        <a :href="fullUrl" download class="download-btn">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
            <path d="M8 12V2M8 12L4 8M8 12L12 8" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
            <path d="M2 14H14" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
          下载文件
        </a>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/index'

interface Props {
  modelValue: boolean
  url: string
  title?: string
}

const props = defineProps<Props>()

defineEmits<{
  'update:modelValue': [value: boolean]
}>()

// 加载状态
const loading = ref(false)

// 转换后的内容
const wordHtml = ref('')
const excelHtml = ref('')
const textContent = ref('')
const pdfUrl = ref('')

// 处理 URL 前缀
const fullUrl = computed(() => {
  if (!props.url) return ''
  if (props.url.startsWith('http://') || props.url.startsWith('https://')) {
    return props.url
  }
  return `http://localhost:5002${props.url}`
})

// 判断文件类型
const fileExtension = computed(() => {
  if (!props.url) return ''
  const match = props.url.toLowerCase().match(/\.([^.]+)$/)
  return match ? match[1] : ''
})

// 从 URL 中提取 file_id
const fileId = computed(() => {
  if (!props.url) return ''
  // 匹配 /api/files/{file_id}/xxx 格式
  const match = props.url.match(/\/api\/files\/([^/]+)/)
  return match ? match[1] : ''
})

const isPdf = computed(() => fileExtension.value === 'pdf')
const isImage = computed(() => ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg'].includes(fileExtension.value))
const isWord = computed(() => ['docx', 'doc'].includes(fileExtension.value))
const isExcel = computed(() => ['xlsx', 'xls'].includes(fileExtension.value))
const isText = computed(() => ['txt', 'md', 'json', 'html', 'css', 'js', 'ts'].includes(fileExtension.value))

// 加载 Word 文档 HTML
const loadWordPreview = async () => {
  if (!isWord.value || !fileId.value) return
  loading.value = true
  try {
    const res: any = await request.get(`/files/${fileId.value}/word`)
    if (res.code === 200 && res.data) {
      wordHtml.value = res.data.html || ''
    }
  } catch (e) {
    console.error('Word preview error:', e)
    wordHtml.value = ''
  } finally {
    loading.value = false
  }
}

// 加载 Excel HTML 表格
const loadExcelPreview = async () => {
  if (!isExcel.value || !fileId.value) return
  loading.value = true
  try {
    const res: any = await request.get(`/files/${fileId.value}/excel`)
    if (res.code === 200 && res.data) {
      excelHtml.value = res.data.html || ''
    }
  } catch (e) {
    console.error('Excel preview error:', e)
    excelHtml.value = ''
  } finally {
    loading.value = false
  }
}

// 加载文本文件内容
const loadTextContent = async () => {
  if (!isText.value) return
  loading.value = true
  try {
    const res: any = await request.get(`/files/${fileId.value}/text`)
    if (res.code === 200 && res.data) {
      textContent.value = res.data.content || ''
    }
  } catch (e) {
    console.error('Text preview error:', e)
    textContent.value = ''
  } finally {
    loading.value = false
  }
}

// 加载 PDF 配置
const loadPdfConfig = async () => {
  if (!isPdf.value || !fileId.value) return
  loading.value = true
  try {
    const res: any = await request.get(`/files/${fileId.value}/pdfjs`)
    if (res.code === 200 && res.data) {
      // PDF.js viewer URL
      pdfUrl.value = `/pdfjs-4.4.124/web/viewer.html?file=${encodeURIComponent(res.data.pdf_url)}`
    }
  } catch (e) {
    console.error('PDF config error:', e)
    pdfUrl.value = ''
  } finally {
    loading.value = false
  }
}

// AI 分析
const showAiAnalysis = ref(true)
const aiLoading = ref(false)
const aiAnalysisResult = ref('')

// 分析文档内容
const analyzeDocument = async () => {
  if (!textContent.value && !wordHtml.value && !excelHtml.value) {
    ElMessage.warning('请先加载文档内容')
    return
  }

  aiLoading.value = true
  aiAnalysisResult.value = ''

  try {
    // 构建提示词
    const content = textContent.value || wordHtml.value.replace(/<[^>]+>/g, ' ').substring(0, 5000) || excelHtml.value.replace(/<[^>]+>/g, ' ').substring(0, 5000)
    const prompt = `请分析以下文档内容，提取关键信息，包括：
1. 文档主题
2. 主要内容摘要
3. 重要数据或指标
4. 结论或要点

文档内容：
${content.substring(0, 3000)}

请用简洁的结构化方式输出分析结果。`

    // 调用 AI 分析
    const response = await fetch('http://localhost:5002/api/chat/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        question: prompt,
        top_k: 0,
        use_internet: false,
        deep_thinking: false
      })
    })

    const reader = response.body?.getReader()
    const decoder = new TextDecoder()

    if (reader) {
      let result = ''
      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        const chunk = decoder.decode(value)
        const lines = chunk.split('\n')

        for (const line of lines) {
          if (line.startsWith('data: ')) {
            try {
              const data = JSON.parse(line.substring(6))
              if (data.type === 'text' && data.content) {
                result += data.content
                aiAnalysisResult.value = result
              }
            } catch (e) {
              // ignore parse errors
            }
          }
        }
      }
    }

    if (!aiAnalysisResult.value) {
      aiAnalysisResult.value = '<p style="color: #8b949e;">AI 分析完成，未返回有效结果</p>'
    }
  } catch (e: any) {
    console.error('AI analysis error:', e)
    aiAnalysisResult.value = `<p style="color: #f85149;">分析失败: ${e.message || '未知错误'}</p>`
  } finally {
    aiLoading.value = false
  }
}

// 监听弹窗打开，加载内容
watch(() => props.modelValue, async (val) => {
  if (val) {
    loading.value = true
    wordHtml.value = ''
    excelHtml.value = ''
    textContent.value = ''
    pdfUrl.value = ''
    aiAnalysisResult.value = ''

    // 根据类型加载对应预览
    if (isPdf.value) {
      await loadPdfConfig()
    } else if (isWord.value) {
      await loadWordPreview()
    } else if (isExcel.value) {
      await loadExcelPreview()
    } else if (isText.value) {
      await loadTextContent()
    } else {
      loading.value = false
    }
  }
})

const handleClose = () => {
  // 清理状态
  wordHtml.value = ''
  excelHtml.value = ''
  textContent.value = ''
  pdfUrl.value = ''
  aiAnalysisResult.value = ''
}
</script>

<style scoped>
.document-preview-dialog :deep(.el-dialog) {
  background: #161b22;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
}

.document-preview-dialog :deep(.el-dialog__header) {
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  padding: 16px 20px;
}

.document-preview-dialog :deep(.el-dialog__title) {
  color: #f5c87a;
  font-size: 16px;
  font-weight: 600;
}

.document-preview-dialog :deep(.el-dialog__body) {
  padding: 0;
}

.preview-container {
  min-height: 500px;
  max-height: 70vh;
  overflow: hidden;
}

.preview-iframe {
  width: 100%;
  height: 70vh;
  border: none;
  background: #fff;
}

.image-preview {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: #0d1117;
  min-height: 500px;
}

.preview-image {
  max-width: 100%;
  max-height: 70vh;
  object-fit: contain;
}

.word-preview,
.unsupported-preview {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  background: #0d1117;
  min-height: 500px;
  color: #8b949e;
}

.word-preview-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  font-size: 18px;
  color: #f5c87a;
}

.word-preview-tip {
  margin: 0 0 24px 0;
  font-size: 14px;
  color: #6e7681;
}

.download-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  background: linear-gradient(135deg, #f5c87a 0%, #d4a574 100%);
  border-radius: 8px;
  color: #1a1f2e;
  font-size: 14px;
  font-weight: 500;
  text-decoration: none;
  cursor: pointer;
  transition: all 0.2s;
}

.download-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(245, 200, 122, 0.3);
}

.unsupported-preview svg {
  margin-bottom: 16px;
  color: #6e7681;
}

.unsupported-preview p {
  margin: 0 0 24px 0;
  font-size: 14px;
}

/* 加载状态 */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  background: #0d1117;
  min-height: 500px;
  color: #8b949e;
  gap: 16px;
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 3px solid rgba(245, 200, 122, 0.2);
  border-top-color: #f5c87a;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* PDF 预览 */
.pdf-preview {
  background: #525659;
  min-height: 500px;
}

.pdf-preview .preview-iframe {
  width: 100%;
  height: 70vh;
  border: none;
}

/* Excel 预览 */
.excel-preview {
  background: #0d1117;
  padding: 20px;
  overflow: auto;
  max-height: 70vh;
}

.excel-content {
  color: #c9d1d9;
  font-size: 14px;
}

.excel-content :deep(table) {
  border-collapse: collapse;
  width: 100%;
  background: #161b22;
  border-radius: 8px;
  overflow: hidden;
}

.excel-content :deep(th) {
  background: linear-gradient(135deg, #f5c87a 0%, #d4a574 100%);
  color: #1a1f2e;
  font-weight: 600;
  padding: 12px 16px;
  text-align: left;
  border-bottom: 2px solid rgba(0, 0, 0, 0.1);
}

.excel-content :deep(td) {
  padding: 10px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  color: #c9d1d9;
}

.excel-content :deep(tr:hover td) {
  background: rgba(245, 200, 122, 0.05);
}

.excel-content :deep(.excel-table) {
  width: 100%;
  border: none;
}

/* Word 预览 */
.word-preview {
  background: #0d1117;
  padding: 24px;
  overflow: auto;
  max-height: 70vh;
}

.word-content {
  color: #c9d1d9;
  font-size: 14px;
  line-height: 1.8;
  max-width: 800px;
  margin: 0 auto;
}

.word-content :deep(h1) {
  color: #f5c87a;
  font-size: 24px;
  margin: 0 0 16px 0;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.word-content :deep(h2) {
  color: #f5c87a;
  font-size: 20px;
  margin: 24px 0 12px 0;
}

.word-content :deep(h3) {
  color: #f5c87a;
  font-size: 16px;
  margin: 20px 0 10px 0;
}

.word-content :deep(p) {
  margin: 0 0 12px 0;
}

.word-content :deep(ul),
.word-content :deep(ol) {
  margin: 0 0 12px 0;
  padding-left: 24px;
}

.word-content :deep(li) {
  margin-bottom: 4px;
}

.word-content :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 16px 0;
  background: #161b22;
  border-radius: 8px;
  overflow: hidden;
}

.word-content :deep(th) {
  background: linear-gradient(135deg, #f5c87a 0%, #d4a574 100%);
  color: #1a1f2e;
  font-weight: 600;
  padding: 10px 14px;
  text-align: left;
}

.word-content :deep(td) {
  padding: 8px 14px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.word-content :deep(blockquote) {
  margin: 16px 0;
  padding: 12px 16px;
  background: rgba(245, 200, 122, 0.1);
  border-left: 3px solid #f5c87a;
  border-radius: 0 4px 4px 0;
}

.word-content :deep(code) {
  background: rgba(0, 0, 0, 0.3);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Fira Code', monospace;
  font-size: 13px;
}

.word-content :deep(pre) {
  background: rgba(0, 0, 0, 0.3);
  padding: 16px;
  border-radius: 8px;
  overflow-x: auto;
}

.word-content :deep(pre code) {
  background: none;
  padding: 0;
}

/* 文本预览 */
.text-preview {
  background: #0d1117;
  padding: 20px;
  overflow: auto;
  max-height: 70vh;
}

.text-content {
  margin: 0;
  padding: 0;
  color: #c9d1d9;
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

/* 错误状态 */
.preview-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  background: #0d1117;
  min-height: 300px;
  color: #6e7681;
}

/* AI 分析栏 */
.ai-analysis-bar {
  padding: 12px 16px;
  background: rgba(245, 200, 122, 0.05);
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.ai-analyze-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: linear-gradient(135deg, rgba(245, 200, 122, 0.15) 0%, rgba(245, 200, 122, 0.05) 100%);
  border: 1px solid rgba(245, 200, 122, 0.3);
  border-radius: 6px;
  color: #f5c87a;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.ai-analyze-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, rgba(245, 200, 122, 0.25) 0%, rgba(245, 200, 122, 0.1) 100%);
  border-color: rgba(245, 200, 122, 0.5);
}

.ai-analyze-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.ai-loading {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(245, 200, 122, 0.3);
  border-top-color: #f5c87a;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.ai-result {
  background: rgba(0, 0, 0, 0.2);
  border-radius: 8px;
  overflow: hidden;
}

.ai-result-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: rgba(245, 200, 122, 0.1);
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  color: #f5c87a;
  font-size: 13px;
  font-weight: 500;
}

.ai-close-btn {
  margin-left: auto;
  padding: 4px;
  background: none;
  border: none;
  color: #6e7681;
  cursor: pointer;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ai-close-btn:hover {
  background: rgba(255, 255, 255, 0.1);
  color: #c9d1d9;
}

.ai-result-content {
  padding: 14px;
  color: #c9d1d9;
  font-size: 13px;
  line-height: 1.7;
  max-height: 200px;
  overflow-y: auto;
}

.ai-result-content :deep(h1),
.ai-result-content :deep(h2),
.ai-result-content :deep(h3) {
  color: #f5c87a;
  margin: 0 0 8px 0;
}

.ai-result-content :deep(h1) { font-size: 16px; }
.ai-result-content :deep(h2) { font-size: 14px; }
.ai-result-content :deep(h3) { font-size: 13px; }

.ai-result-content :deep(p) {
  margin: 0 0 8px 0;
}

.ai-result-content :deep(ul),
.ai-result-content :deep(ol) {
  margin: 0 0 8px 0;
  padding-left: 20px;
}

.ai-result-content :deep(li) {
  margin-bottom: 4px;
}

.ai-result-content :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 8px 0;
  font-size: 12px;
}

.ai-result-content :deep(th),
.ai-result-content :deep(td) {
  padding: 6px 10px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  text-align: left;
}

.ai-result-content :deep(th) {
  background: rgba(245, 200, 122, 0.15);
  color: #f5c87a;
}
</style>