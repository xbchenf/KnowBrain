import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory('/'),
  routes: [
    // ==================== 登录（独立，无布局） ====================
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
      meta: { guest: true }
    },

    // ==================== Q&A 问答界面（WebLayout 侧边栏） ====================
    {
      path: '/',
      component: () => import('../layouts/WebLayout.vue'),
      meta: { auth: true },
      children: [
        { path: '', name: 'chat', component: () => import('../views/ChatView.vue') },
        { path: 'docs', name: 'docs', component: () => import('../views/DocBrowseView.vue') }
      ]
    },

    // ==================== 管理后台（AdminLayout 侧边栏） ====================
    {
      path: '/admin',
      component: () => import('../views/AdminLayout.vue'),
      meta: { auth: true, roles: ['ADMIN', 'MANAGER'] },
      children: [
        { path: '', redirect: '/admin/dashboard' },
        { path: 'dashboard', name: 'dashboard', component: () => import('../views/DashboardView.vue') },
        { path: 'departments', name: 'departments', component: () => import('../views/DepartmentView.vue') },
        { path: 'users', name: 'users', component: () => import('../views/UserView.vue') },
        { path: 'scenarios', name: 'scenarios', component: () => import('../views/ScenarioView.vue') },
        { path: 'feedback', name: 'feedback', component: () => import('../views/FeedbackView.vue') },
        { path: 'stats', name: 'stats', component: () => import('../views/StatsView.vue') },
        { path: 'audit-logs', name: 'audit-logs', component: () => import('../views/AuditLogView.vue') },
        { path: 'im', name: 'im-integration', component: () => import('../views/ImIntegrationView.vue') },
        { path: 'evaluation', name: 'evaluation', component: () => import('../views/EvaluationView.vue') }
      ]
    },

    // ==================== 独立页面（无侧边栏） ====================
    {
      path: '/spaces/:id',
      name: 'space-detail',
      component: () => import('../views/SpaceDetailView.vue'),
      meta: { auth: true }
    },
    {
      path: '/documents/:id',
      name: 'document-preview',
      component: () => import('../views/DocumentPreviewView.vue'),
      meta: { auth: true }
    }
  ]
})

// 路由守卫
router.beforeEach((to) => {
  const token = localStorage.getItem('kb_token')
  const userStr = localStorage.getItem('kb_user')
  const user = userStr ? JSON.parse(userStr) : null
  const role = user?.role || ''

  // 1. 需要登录但无 token → 跳登录页
  if (to.meta.auth && !token) {
    return '/login'
  }

  // 2. 已登录访问登录页 → 按角色跳转
  if (to.meta.guest && token) {
    if (role === 'USER') return '/'
    return '/admin/dashboard'
  }

  // 3. 角色权限检查（管理后台仅 ADMIN / MANAGER 可访问）
  if (to.meta.roles) {
    const allowedRoles = to.meta.roles as string[]
    if (!allowedRoles.includes(role)) {
      // USER 角色访问管理后台 → 跳转到前台
      if (role === 'USER') return '/'
      // 未登录 → 跳登录页
      return '/login'
    }
  }
})

export default router
