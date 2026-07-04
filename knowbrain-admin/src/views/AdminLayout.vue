<template>
  <div class="admin-layout">
    <!-- 亮色侧边栏 -->
    <aside class="admin-sidebar">
      <div class="sidebar-logo">
        <div class="logo-icon">
          <svg width="22" height="22" viewBox="0 0 48 48" fill="none">
            <rect width="48" height="48" rx="12" fill="#409EFF"/>
            <path d="M14 20c0-3.3 2.7-6 6-6h2c1.1 0 2 .9 2 2s-.9 2-2 2h-2c-1.1 0-2 .9-2 2s.9 2 2 2h8c3.3 0 6 2.7 6 6s-2.7 6-6 6h-2c-1.1 0-2-.9-2-2s.9-2 2-2h2c1.1 0 2-.9 2-2s-.9-2-2-2h-8c-3.3 0-6-2.7-6-6z" fill="white"/>
          </svg>
        </div>
        <h1>KnowBrain</h1>
      </div>

      <nav class="sidebar-nav">
        <router-link to="/dashboard" class="nav-item" :class="{ active: route.path === '/dashboard' }">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/></svg>
          <span>工作台</span>
        </router-link>

        <div v-if="isAdmin" class="nav-group-label">系统管理</div>

        <router-link v-if="isAdmin" to="/departments" class="nav-item" :class="{ active: route.path === '/departments' }">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>
          <span>部门管理</span>
        </router-link>

        <router-link v-if="isAdmin" to="/users" class="nav-item" :class="{ active: route.path === '/users' }">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="12" cy="6" r="4"/><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/></svg>
          <span>用户管理</span>
        </router-link>
      </nav>

      <div class="sidebar-footer">
        <div class="user-card">
          <div class="user-avatar">{{ userInitial }}</div>
          <div class="user-meta">
            <div class="user-name">{{ userName }}</div>
            <div class="user-role">{{ isAdmin ? '系统管理员' : '知识管理员' }}</div>
          </div>
        </div>
      </div>
    </aside>

    <!-- 右侧主区域 -->
    <div class="main-area">
      <header class="admin-header">
        <div class="header-left">
          <span class="breadcrumb">{{ pageTitle }}</span>
        </div>
        <div class="header-right">
          <a href="/" class="header-link" target="_blank">前往前台</a>
          <button class="logout-btn" @click="logout">退出登录</button>
        </div>
      </header>

      <main class="admin-main">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()
const userName = ref('管理员')
const userInitial = ref('管')
const isAdmin = ref(false)

const pageTitles: Record<string, string> = {
  '/dashboard': '工作台',
  '/departments': '部门管理',
  '/users': '用户管理'
}
const pageTitle = computed(() => pageTitles[route.path] || 'KnowBrain 管理后台')

onMounted(() => {
  const u = localStorage.getItem('kb_user')
  if (u) {
    try {
      const parsed = JSON.parse(u)
      userName.value = parsed.name || '管理员'
      userInitial.value = (parsed.name || '管').charAt(0)
      isAdmin.value = parsed.role === 'ADMIN'
    } catch { /* ignore */ }
  }
})

function logout() {
  localStorage.removeItem('kb_token')
  localStorage.removeItem('kb_user')
  router.push('/login')
}
</script>

<style scoped>
/* ===== 整体布局 ===== */
.admin-layout { display: flex; min-height: 100vh; background: #f5f7fa; }

/* ===== 亮色侧边栏 ===== */
.admin-sidebar {
  width: 220px; background: #fff; border-right: 1px solid #e4e7ed;
  display: flex; flex-direction: column; flex-shrink: 0; position: fixed;
  top: 0; left: 0; bottom: 0; z-index: 100;
}
.sidebar-logo {
  padding: 20px 20px 16px; display: flex; align-items: center; gap: 10px;
}
.logo-icon { display: flex; align-items: center; }
.sidebar-logo h1 { font-size: 17px; color: #303133; font-weight: 700; letter-spacing: -.3px; }

/* 导航 */
.sidebar-nav { flex: 1; padding: 4px 12px; overflow-y: auto; }
.nav-group-label {
  font-size: 11px; color: #909399; padding: 16px 10px 6px; font-weight: 600; letter-spacing: .5px;
}
.nav-item {
  display: flex; align-items: center; gap: 10px; padding: 9px 12px; border-radius: 8px;
  color: #606266; font-size: 14px; text-decoration: none; transition: all .12s; margin-bottom: 1px;
}
.nav-item:hover { background: #f5f7fa; color: #303133; }
.nav-item.active { background: #ecf5ff; color: #409EFF; font-weight: 500; }
.nav-item.active svg { color: #409EFF; }
.nav-item svg { flex-shrink: 0; }

/* 用户卡片 */
.sidebar-footer { padding: 12px 16px; border-top: 1px solid #f0f0f0; }
.user-card { display: flex; align-items: center; gap: 10px; }
.user-avatar {
  width: 34px; height: 34px; border-radius: 50%; color: #fff;
  background: linear-gradient(135deg, #409EFF, #67c23a);
  display: flex; align-items: center; justify-content: center;
  font-size: 13px; font-weight: 600; flex-shrink: 0;
}
.user-meta { min-width: 0; }
.user-name { font-size: 13px; color: #303133; font-weight: 500; }
.user-role { font-size: 11px; color: #909399; }

/* ===== 主区域 ===== */
.main-area { flex: 1; margin-left: 220px; display: flex; flex-direction: column; min-height: 100vh; }

/* 顶栏 */
.admin-header {
  display: flex; align-items: center; justify-content: space-between;
  height: 52px; background: #fff; border-bottom: 1px solid #e4e7ed;
  padding: 0 24px; position: sticky; top: 0; z-index: 50;
}
.breadcrumb { font-size: 14px; color: #303133; font-weight: 500; }
.header-right { display: flex; align-items: center; gap: 16px; }
.header-link { font-size: 13px; color: #909399; text-decoration: none; }
.header-link:hover { color: #409EFF; }
.logout-btn {
  padding: 5px 14px; border-radius: 6px; border: 1px solid #e4e7ed; background: #fff;
  cursor: pointer; font-size: 12px; color: #606266; font-family: inherit; transition: all .12s;
}
.logout-btn:hover { border-color: #409EFF; color: #409EFF; }

/* 内容区 */
.admin-main { flex: 1; padding: 20px 24px; }
</style>
