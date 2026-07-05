<template>
  <div class="app-layout">
    <!-- 左侧边栏 — 对话历史 -->
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

      <button class="new-chat-btn" @click="startNewChat">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
        新对话
      </button>

      <div class="chat-list" v-if="history.length">
        <div
          v-for="(h, i) in history"
          :key="i"
          class="chat-item"
          :class="{ active: i === activeIndex }"
          @click="selectChat(i)"
        >{{ h }}</div>
      </div>
      <div class="chat-list-empty" v-else>
        <p>暂无对话记录</p>
      </div>

      <div class="sidebar-footer">
        <div class="user-info">
          <div class="user-avatar">{{ userInitial }}</div>
          <span class="user-name">{{ userName }}</span>
        </div>
      </div>
    </aside>

    <!-- 主聊天区 -->
    <main class="main-area">
      <ChatView ref="chatRef" :key="chatKey" />
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import ChatView from './views/ChatView.vue'

const chatKey = ref(0)
const history = ref<string[]>([])
const activeIndex = ref(0)
const userName = ref('用户')
const userInitial = ref('U')

onMounted(() => {
  // 登录检查
  const token = localStorage.getItem('kb_token')
  if (!token) {
    window.location.href = '/admin/login'
    return
  }
  // 读取用户信息
  const u = localStorage.getItem('kb_user')
  if (u) {
    try {
      const parsed = JSON.parse(u)
      userName.value = parsed.name || '用户'
      userInitial.value = (parsed.name || '用').charAt(0)
    } catch { /* ignore */ }
  }
})

function startNewChat() {
  chatKey.value++
  history.value.unshift('新对话')
  activeIndex.value = 0
}

function selectChat(i: number) {
  activeIndex.value = i
  // 后续可加载历史对话内容
}
</script>

<style>
/* ===== 全局重置 ===== */
* { margin: 0; padding: 0; box-sizing: border-box; }
body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif; background: #fff; }
</style>

<style scoped>
.app-layout { display: flex; height: 100vh; }

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
.new-chat-btn {
  display: flex; align-items: center; justify-content: center; gap: 6px;
  margin: 8px 12px; padding: 9px 0; border-radius: 8px;
  border: 1px solid #e5e7eb; background: #fff; cursor: pointer;
  font-size: 13px; color: #333; transition: all .15s; font-family: inherit;
}
.new-chat-btn:hover { background: #f3f4f6; }
.chat-list { flex: 1; overflow-y: auto; padding: 4px 8px; }
.chat-item {
  padding: 10px 12px; border-radius: 8px; cursor: pointer; font-size: 13px;
  color: #333; margin-bottom: 2px; overflow: hidden; white-space: nowrap;
  text-overflow: ellipsis; transition: background .12s;
}
.chat-item:hover { background: #e5e7eb; }
.chat-item.active { background: #e8f0fe; color: #1a1a1a; }
.chat-list-empty { flex: 1; display: flex; align-items: center; justify-content: center; }
.chat-list-empty p { font-size: 12px; color: #bbb; }

.sidebar-footer { padding: 12px 16px; border-top: 1px solid #e5e7eb; }
.user-info { display: flex; align-items: center; gap: 8px; }
.user-avatar {
  width: 28px; height: 28px; border-radius: 50%; background: #409EFF;
  color: #fff; display: flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: 600;
}
.user-name { font-size: 13px; color: #333; font-weight: 500; }

/* ===== 主区域 ===== */
.main-area { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
</style>
