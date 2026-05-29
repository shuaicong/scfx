import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    component: () => import('../views/layout/Layout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/dashboard/Dashboard.vue'),
        meta: { title: '仪表板' }
      },
      {
        path: 'collection',
        redirect: '/scripts'
      },
      {
        path: 'scripts',
        name: 'Scripts',
        component: () => import('../views/scripts/TaskList.vue'),
        meta: { title: '采集任务管理' }
      },
      {
        path: 'scripts/create',
        name: 'TaskCreate',
        component: () => import('../views/scripts/TaskDetail.vue'),
        meta: { title: '创建任务' }
      },
      {
        path: 'scripts/:id',
        name: 'TaskDetail',
        component: () => import('../views/scripts/TaskDetail.vue'),
        meta: { title: '任务详情', hideSidebar: true }
      },
      {
        path: 'scripts/:scriptId/executions/:executionId',
        name: 'ExecutionDetail',
        component: () => import('../views/scripts/ExecutionDetail.vue'),
        meta: { title: '执行详情', hideSidebar: true }
      },
      {
        path: 'scripts/:scriptId/executions',
        name: 'ExecutionHistory',
        component: () => import('../views/scripts/ExecutionHistory.vue'),
        meta: { title: '执行历史', hideSidebar: true }
      },
      {
        path: 'logs',
        name: 'Logs',
        component: () => import('../views/logs/Logs.vue'),
        meta: { title: '日志查看' }
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('../views/settings/Settings.vue'),
        meta: { title: '系统设置' }
      },
      {
        path: 'ai-chat',
        name: 'AIChat',
        component: () => import('../views/ai-chat/AiChat.vue'),
        meta: { title: 'AI 知识问答' }
      },
      {
        path: 'knowledge',
        name: 'Knowledge',
        component: () => import('../views/knowledge/Knowledge.vue'),
        meta: { title: '知识库管理' }
      },
      {
        path: 'knowledge/visualization',
        name: 'KnowledgeVisualization',
        component: () => import('../views/knowledge/KnowledgeVisualization.vue'),
        meta: { title: '向量可视化' }
      },
      {
        path: 'system/datasource',
        name: 'DataSource',
        component: () => import('../views/system/DataSource.vue'),
        meta: { title: '数据源管理' }
      },
      {
        path: 'system/datasource/:code',
        name: 'DataSourceDetail',
        component: () => import('../views/system/DataSourceDetail.vue'),
        meta: { title: '数据源详情', hideSidebar: true }
      },
      {
        path: 'settings/alert-rules',
        name: 'AlertRules',
        component: () => import('../views/settings/AlertRules.vue'),
        meta: { title: '告警规则' }
      },
      {
        path: 'settings/alert-records',
        name: 'AlertRecords',
        component: () => import('../views/settings/AlertRecords.vue'),
        meta: { title: '告警记录' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
