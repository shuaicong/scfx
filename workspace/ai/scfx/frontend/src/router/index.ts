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
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
