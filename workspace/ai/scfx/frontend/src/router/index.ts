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
        name: 'Collection',
        component: () => import('../views/collection/Collection.vue'),
        meta: { title: '采集管理' }
      },
      {
        path: 'scripts',
        name: 'Scripts',
        component: () => import('../views/scripts/Scripts.vue'),
        meta: { title: '脚本管理' }
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
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
