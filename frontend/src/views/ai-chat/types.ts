/** 可视化数据块 */
export interface VisualizationBlock {
  type: 'line' | 'heatmap' | 'comparison' | 'table'
  title: string
  data: {
    xAxis?: string[]
    series?: { name: string; data: number[]; type?: string }[]
    rows?: { region: string; province?: string; price: number; change: string; remark: string }[]
    unit?: string
  }
}

/** SQL 来源标签 */
export interface SqlSource {
  type: 'sql'
  query_summary: string
  source_name: string
  date: string
  url: string
  grain_standard: string
}
