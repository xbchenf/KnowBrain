<template>
  <div class="web-layout">
    <!-- 左侧边栏 — 导航 + 对话历史 / 空间列表 -->
    <aside class="sidebar">
      <div class="sidebar-header">
        <div class="logo" @click="startNewChat">
          <svg width="24" height="24" viewBox="0 0 48 48" fill="none">
            <rect width="48" height="48" rx="12" fill="#409EFF"/>
            <path d="M14 20c0-3.3 2.7-6 6-6h2c1.1 0 2 .9 2 2s-.9 2-2 2h-2c-1.1 0-2 .9-2 2s.9 2 2 2h8c3.3 0 6 2.7 6 6s-2.7 6-6 6h-2c-1.1 0-2-.9-2-2s.9-2 2-2h2c1.1 0 2-.9 2-2s-.9-2-2-2h-8c-3.3 0-6-2.7-6-6z" fill="white"/>
          </svg>
          <span>KnowBrain</span>
        </div>
      </div>

      <!-- 导航切换 -->
      <nav class="nav-tabs">
        <button
          :class="['nav-tab', { active: activeNav === 'chat' }]"
          @click="goChat"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
          对话
        </button>
        <button
          :class="['nav-tab', { active: activeNav === 'docs' }]"
          @click="goDocs"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
          文档库
        </button>
      </nav>

      <!-- 对话模式 -->
      <template v-if="activeNav === 'chat'">
        <button class="new-chat-btn" @click="startNewChat">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          新对话
        </button>

        <div class="sidebar-list" v-if="history.length">
          <div
            v-for="(h, i) in history"
            :key="i"
            class="sidebar-item"
            :class="{ active: i === activeIndex }"
            @click="selectChat(i)"
          >{{ h }}</div>
        </div>
        <div class="sidebar-list-empty" v-else>
          <p>暂无对话记录</p>
        </div>
      </template>

      <!-- 文档库模式 — 空间列表 -->
      <template v-if="activeNav === 'docs'">
        <div class="sidebar-list" v-if="spaces.length">
          <div
            v-for="s in spaces"
            :key="s.id"
            :class="['sidebar-item', { active: selectedSpaceId === s.id }]"
            @click="selectSpace(s)"
          >
            <span class="sidebar-item-icon">📁</span>
            <span class="sidebar-item-text">{{ s.name }}</span>
          </div>
        </div>
        <div class="sidebar-list-empty" v-else>
          <p>暂无可用空间</p>
        </div>
      </template>

      <div class="sidebar-footer">
        <div class="user-info" @click="goSettings" title="个人设置">
          <div class="user-avatar">{{ userInitial }}</div>
          <span class="user-name">{{ userName }}</span>
        </div>
        <button class="logout-btn" @click="logout" title="退出登录">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>
        </button>
      </div>
    </aside>

    <!-- 主区域 -->
    <main class="main-area">
      <router-view :key="chatKey" />
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { listSpaces } from '../api'

const router = useRouter()
const route = useRoute()

const activeNav = ref<'chat' | 'docs'>('chat')
const chatKey = ref(0)
const history = ref<string[]>([])
const activeIndex = ref(0)
const userName = ref('用户')
const userInitial = ref('U')

// 文档库 — 空间列表
const spaces = ref<any[]>([])
const selectedSpaceId = computed(() => {
  const id = route.query.spaceId
  return id ? Number(id) : null
})

// 同步 activeNav 从路由
function syncNavFromRoute() {
  if (route.path === '/docs') {
    activeNav.value = 'docs'
  } else {
    activeNav.value = 'chat'
  }
}

onMounted(() => {
  const u = localStorage.getItem('kb_user')
  if (u) {
    try {
      const parsed = JSON.parse(u)
      userName.value = parsed.name || '用户'
      userInitial.value = (parsed.name || '用').charAt(0)
    } catch { /* ignore */ }
  }
  syncNavFromRoute()
  loadSpaces()
})

watch(() => route.path, syncNavFromRoute)

async function loadSpaces() {
  try {
    const res = await listSpaces()
    spaces.value = res.data?.data?.records || res.data?.data || []
  } catch { /* ignore */ }
}

function goSettings() {
  router.push('/settings')
}

async function logout() {
  try { await import('../api/index').then(m => m.logoutApi()) } catch { /* ignore */ }
  localStorage.removeItem('kb_token')
  localStorage.removeItem('kb_user')
  router.push('/login')
}

function goChat() {
  router.push('/')
}

function goDocs() {
  router.push('/docs')
}

function selectSpace(s: any) {
  router.push({ path: '/docs', query: { spaceId: s.id, spaceName: s.name } })
}

function startNewChat() {
  chatKey.value++
  activeIndex.value = -1
  if (route.path !== '/') {
    router.push('/')
  }
}

function selectChat(i: number) {
  activeIndex.value = i
}
</script>

<style scoped>
.web-layout { display: flex; height: 100vh; }

/* ===== 侧边栏 ===== */
.sidebar {
  width: 260px; background: #f9fafb; border-right: 1px solid #e5e7eb;
  display: flex; flex-direction: column; flex-shrink: 0;
}
.sidebar-header { padding: 14px 16px; }
.logo {
  display: flex; align-items: center; gap: 10px; cursor: pointer;
  font-size: 16px; font-weight: 700; color: #1a1a1a;
}

/* 导航切换 */
.nav-tabs {
  display: flex;
  gap: 4px;
  margin: 0 12px 8px;
  padding: 4px;
  background: #eceff3;
  border-radius: 10px;
}
.nav-tab {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  padding: 7px 0;
  font-size: 13px;
  color: #606266;
  border: none;
  background: transparent;
  border-radius: 7px;
  cursor: pointer;
  transition: all .15s;
  font-family: inherit;
}
.nav-tab:hover { color: #303133; }
.nav-tab.active { background: #fff; color: #409EFF; font-weight: 500; box-shadow: 0 1px 2px rgba(0,0,0,.06); }

.new-chat-btn {
  display: flex; align-items: center; justify-content: center; gap: 6px;
  margin: 4px 12px; padding: 9px 0; border-radius: 8px;
  border: 1px solid #e5e7eb; background: #fff; cursor: pointer;
  font-size: 13px; color: #333; transition: all .15s; font-family: inherit;
}
.new-chat-btn:hover { background: #f3f4f6; }

/* 列表（对话历史 / 空间列表 共用） */
.sidebar-list { flex: 1; overflow-y: auto; padding: 4px 8px; }
.sidebar-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px; border-radius: 8px; cursor: pointer; font-size: 13px;
  color: #333; margin-bottom: 2px; overflow: hidden; white-space: nowrap;
  text-overflow: ellipsis; transition: background .12s;
}
.sidebar-item:hover { background: #e5e7eb; }
.sidebar-item.active { background: #e8f0fe; color: #409EFF; font-weight: 500; }
.sidebar-item-icon { font-size: 14px; flex-shrink: 0; }
.sidebar-item-text { overflow: hidden; text-overflow: ellipsis; }

.sidebar-list-empty { flex: 1; display: flex; align-items: center; justify-content: center; }
.sidebar-list-empty p { font-size: 12px; color: #bbb; }

.sidebar-footer { padding: 12px 16px; border-top: 1px solid #e5e7eb; display: flex; align-items: center; justify-content: space-between; }
.user-info { display: flex; align-items: center; gap: 8px; }
.user-avatar {
  width: 28px; height: 28px; border-radius: 50%; background: #409EFF;
  color: #fff; display: flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: 600;
}
.user-name { font-size: 13px; color: #333; font-weight: 500; }
.logout-btn {
  width: 30px; height: 30px; border-radius: 6px; border: none; background: transparent;
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  color: #909399; transition: all .12s;
}
.logout-btn:hover { background: #fee2e2; color: #ef4444; }

/* ===== 主区域 ===== */
.main-area { flex: 1; display: flex; flex-direction: column; overflow-y: auto; }
</style>
