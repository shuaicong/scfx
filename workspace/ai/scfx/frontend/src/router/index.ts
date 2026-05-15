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
        path: 'scripts/:id/versions',
        name: 'VersionHistory',
        component: () => import('../views/scripts/VersionHistory.vue'),
        meta: { title: '版本历史', hideSidebar: true }
      },
      {
        path: 'scripts/:scriptId/executions/:executionId',
        name: 'ExecutionDetail',
        component: () => import('../views/scripts/ExecutionDetail.vue'),
        meta: { title: '执行详情', hideSidebar: true }
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
        path: 'system',
        name: 'System',
        component: () => import('../views/layout/Layout.vue'),
        meta: { title: '系统管理' },
        children: [
          {
            path: 'datasource',
            name: 'DataSource',
            component: () => import('../views/system/DataSource.vue'),
            meta: { title: '数据源管理' }
          },
          {
            path: 'datasource/:code',
            name: 'DataSourceDetail',
            component: () => import('../views/system/DataSourceDetail.vue'),
            meta: { title: '数据源详情', hideSidebar: true }
          }
        ]
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
