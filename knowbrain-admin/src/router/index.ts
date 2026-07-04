import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory('/admin/'),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
      meta: { guest: true }
    },
    {
      path: '/',
      redirect: '/dashboard'
    },
    {
      path: '/dashboard',
      name: 'dashboard',
      component: () => import('../views/DashboardView.vue'),
      meta: { auth: true }
    },
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
    },
    {
      path: '/departments',
      name: 'departments',
      component: () => import('../views/DepartmentView.vue'),
      meta: { auth: true }
    },
    {
      path: '/users',
      name: 'users',
      component: () => import('../views/UserView.vue'),
      meta: { auth: true }
    }
  ]
})

// 路由守卫 — 未登录跳转登录页
router.beforeEach((to) => {
  const token = localStorage.getItem('kb_token')
  const userStr = localStorage.getItem('kb_user')
  const user = userStr ? JSON.parse(userStr) : null
  const role = user?.role || ''

  if (to.meta.auth && !token) {
    return '/login'
  }
  if (to.meta.guest && token) {
    return '/dashboard'
  }
  // USER 角色不能访问后台 → 重定向到前台
  if (to.meta.auth && role === 'USER') {
    window.location.href = '/'
    return false
  }
})

export default router
