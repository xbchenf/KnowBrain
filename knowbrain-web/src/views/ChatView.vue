<template>
  <div class="chat-root">
    <!-- 消息区 -->
    <div class="chat-messages" ref="messagesRef">
      <!-- 欢迎页（无消息时） -->
      <div v-if="messages.length === 0" class="welcome">
        <div class="welcome-logo">
          <svg width="48" height="48" viewBox="0 0 48 48" fill="none">
            <rect width="48" height="48" rx="12" fill="#409EFF"/>
            <path d="M14 20c0-3.3 2.7-6 6-6h2c1.1 0 2 .9 2 2s-.9 2-2 2h-2c-1.1 0-2 .9-2 2s.9 2 2 2h8c3.3 0 6 2.7 6 6s-2.7 6-6 6h-2c-1.1 0-2-.9-2-2s.9-2 2-2h2c1.1 0 2-.9 2-2s-.9-2-2-2h-8c-3.3 0-6-2.7-6-6z" fill="white"/>
          </svg>
        </div>
        <h2>今天有什么可以帮你的？</h2>
        <p class="welcome-sub">向公司知识库提问，AI 精准回答并溯源原文</p>
        <div class="suggestions">
          <button
            v-for="s in suggestions"
            :key="s"
            class="suggestion-chip"
            @click="quickAsk(s)"
          >{{ s }}</button>
        </div>
      </div>

      <!-- 消息列表 -->
      <div v-for="(msg, i) in messages" :key="i" :class="['msg-row', msg.role]">
        <!-- 用户消息 -->
        <template v-if="msg.role === 'user'">
          <div class="msg-user-bubble">{{ msg.content }}</div>
        </template>

        <!-- AI 回复 -->
        <template v-else-if="msg.role === 'assistant'">
          <div class="msg-ai-wrap">
            <div class="msg-ai-avatar">
              <svg width="20" height="20" viewBox="0 0 48 48" fill="none">
                <rect width="48" height="48" rx="12" fill="#409EFF"/>
                <path d="M14 20c0-3.3 2.7-6 6-6h2c1.1 0 2 .9 2 2s-.9 2-2 2h-2c-1.1 0-2 .9-2 2s.9 2 2 2h8c3.3 0 6 2.7 6 6s-2.7 6-6 6h-2c-1.1 0-2-.9-2-2s.9-2 2-2h2c1.1 0 2-.9 2-2s-.9-2-2-2h-8c-3.3 0-6-2.7-6-6z" fill="white"/>
              </svg>
            </div>
            <div class="msg-ai-content">
              <!-- 思考链可视化 -->
              <div v-if="msg.thinkingSteps && msg.thinkingSteps.length" class="thinking-chain">
                <div class="thinking-steps">
                  <div v-for="(step, si) in msg.thinkingSteps" :key="si" class="thinking-step">
                    <span class="thinking-step-icon">{{ stepIcon(step.type) }}</span>
                    <span class="thinking-step-text">{{ step.text }}</span>
                    <span v-if="step.hits !== undefined" class="thinking-step-hits">→ {{ step.hits }} 篇</span>
                  </div>
                </div>
              </div>
              <div class="msg-ai-text" v-html="formatAnswer(msg.content)"></div>
              <span v-if="loading && i === messages.length - 1 && msg.role === 'assistant'" class="cursor-blink">|</span>

              <!-- 降级提示 -->
              <div v-if="msg.fallback" class="fallback-badge">
                <span>⚠️</span> AI 生成暂不可用，以上为检索到的相关原文
              </div>

              <!-- 来源引用卡片 -->
              <div v-if="msg.sources && msg.sources.length" class="source-cards">
                <div v-for="(s, si) in msg.sources" :key="si" class="source-card">
                  <div class="source-card-icon">📄</div>
                  <div class="source-card-body">
                    <div class="source-card-title">{{ s.title }}</div>
                    <div class="source-card-text">{{ s.text }}</div>
                  </div>
                </div>
              </div>

              <!-- 底部操作栏 -->
              <div v-if="msg.content" class="msg-actions">
                <div class="msg-actions-left">
                  <span v-if="msg.confidence" class="confidence" :class="'conf-' + msg.confidence">
                    {{ confidenceLabel(msg.confidence) }}
                  </span>
                </div>
                <div class="msg-actions-right">
                  <button
                    :class="['action-btn', { active: msg.feedback === 'useful' }]"
                    :disabled="!!msg.feedback"
                    @click="onFeedback(msg, 'useful')"
                    title="有帮助"
                  >👍</button>
                  <button
                    :class="['action-btn', { active: msg.feedback === 'useless' }]"
                    :disabled="!!msg.feedback"
                    @click="onFeedback(msg, 'useless')"
                    title="没帮助"
                  >👎</button>
                </div>
              </div>
            </div>
          </div>
        </template>

        <!-- 系统消息 -->
        <template v-else-if="msg.role === 'system'">
          <div class="system-msg">{{ msg.content }}</div>
        </template>
      </div>

      <!-- 加载状态 -->
      <div v-if="loading && (!messages.length || messages[messages.length-1]?.role !== 'assistant')" class="msg-row assistant">
        <div class="msg-ai-wrap">
          <div class="msg-ai-avatar">
            <svg width="20" height="20" viewBox="0 0 48 48" fill="none">
              <rect width="48" height="48" rx="12" fill="#409EFF"/>
              <path d="M14 20c0-3.3 2.7-6 6-6h2c1.1 0 2 .9 2 2s-.9 2-2 2h-2c-1.1 0-2 .9-2 2s.9 2 2 2h8c3.3 0 6 2.7 6 6s-2.7 6-6 6h-2c-1.1 0-2-.9-2-2s.9-2 2-2h2c1.1 0 2-.9 2-2s-.9-2-2-2h-8c-3.3 0-6-2.7-6-6z" fill="white"/>
            </svg>
          </div>
          <div class="msg-ai-content">
            <div class="thinking-dots"><span></span><span></span><span></span></div>
          </div>
        </div>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="input-area">
      <div class="input-wrap">
        <div class="filter-bar" v-if="spaces.length || categoryTree.length">
          <el-select
            v-model="selectedSpaceIds"
            :data="spaces"
            multiple
            placeholder="全部空间"
            clearable
            collapse-tags
            collapse-tags-tooltip
            size="small"
            class="space-select"
            v-if="spaces.length"
          >
            <el-option
              v-for="s in spaces"
              :key="s.id"
              :label="s.name"
              :value="s.id"
            />
          </el-select>
          <el-tree-select
            v-model="selectedCategory"
            :data="categoryTree"
            :props="{ label: 'name', value: 'key', children: 'children' }"
            placeholder="全部分类"
            clearable
            check-strictly
            filterable
            size="small"
            class="category-select"
            v-if="categoryTree.length"
          />
        </div>
        <textarea
          v-model="input"
          class="chat-input"
          placeholder="向 KnowBrain 提问..."
          :rows="1"
          :disabled="loading"
          @keydown.enter.exact.prevent="send"
          @input="autoResize"
          ref="inputRef"
        ></textarea>
        <button
          class="send-btn"
          :class="{ visible: input.trim() && !loading }"
          :disabled="!input.trim() || loading"
          @click="send"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <line x1="12" y1="5" x2="12" y2="19"/><polyline points="19 12 12 19 5 12"/>
          </svg>
        </button>
      </div>
      <p class="input-footer">KnowBrain 基于公司文档库生成答案，请核实关键信息</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import { chatWithKnowledgeStream, submitFeedback, listPublicCategories, listPublicFaq, listSpaces, type HistoryMessage, type ThinkingEvent } from '../api'

interface ThinkingStep {
  type: 'analyze' | 'search' | 'synthesize'
  text: string
  hits?: number
}

interface Message {
  role: 'user' | 'assistant' | 'system'
  content: string
  sources?: { title: string; documentId: number; chunkIndex: number; text: string }[]
  confidence?: string
  fallback?: boolean
  feedback?: string
  thinkingSteps?: ThinkingStep[]
}

const defaultSuggestions = [
  'VPN 怎么配置？',
  '年假有几天？',
  '报销流程是什么？',
  '入职设备怎么申请？'
]
const suggestions = ref<string[]>([...defaultSuggestions])

const messages = ref<Message[]>([])
const input = ref('')
const loading = ref(false)
const messagesRef = ref<HTMLElement>()
const inputRef = ref<HTMLTextAreaElement>()

// 空间 + 分类选择
const spaces = ref<any[]>([])
const selectedSpaceIds = ref<number[]>([])
const categoryTree = ref<any[]>([])
const selectedCategory = ref('')

onMounted(async () => {
  try {
    const res = await listPublicCategories()
    categoryTree.value = res.data?.data || []
  } catch { /* 非关键，忽略 */ }

  try {
    const faqRes = await listPublicFaq()
    const faqList = faqRes.data?.data || []
    if (faqList.length) {
      suggestions.value = faqList.slice(0, 5).map((f: any) => f.question)
    }
  } catch { /* 取不到 FAQ 就用默认值 */ }

  try {
    const spaceRes = await listSpaces()
    spaces.value = spaceRes.data?.data?.records || spaceRes.data?.data || []
  } catch { /* 非关键，忽略 */ }
})

function quickAsk(q: string) {
  input.value = q
  send()
}

function autoResize() {
  const el = inputRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 160) + 'px'
}

async function send() {
  const question = input.value.trim()
  if (!question || loading.value) return

  // 取最近 5 轮对话（10 条消息）作为历史，排除当前轮
  const MAX_HISTORY = 10
  const recentHistory: HistoryMessage[] = messages.value
    .filter(m => m.role === 'user' || m.role === 'assistant')
    .slice(-MAX_HISTORY)
    .map(m => ({ role: m.role as 'user' | 'assistant', content: m.content }))

  messages.value.push({ role: 'user', content: question })
  input.value = ''
  if (inputRef.value) { inputRef.value.style.height = 'auto' }
  loading.value = true

  const aiMsg: Message = { role: 'assistant', content: '' }
  messages.value.push(aiMsg)
  await scrollToBottom()

  const category = selectedCategory.value || undefined
  const spaceIds = selectedSpaceIds.value.length ? selectedSpaceIds.value : undefined

  await chatWithKnowledgeStream(question, {
    onThinking(event: ThinkingEvent) {
      if (!aiMsg.thinkingSteps) aiMsg.thinkingSteps = []
      aiMsg.thinkingSteps.push(event)
    },
    onToken(token: string) {
      aiMsg.content += token
      scrollToBottom()
    },
    onSources(sources: any[]) {
      aiMsg.sources = sources.map((s: any) => ({
        title: s.documentTitle || s.title || '',
        documentId: s.documentId || 0,
        chunkIndex: s.chunkIndex ?? 0,
        text: s.content || ''
      }))
    },
    onDone(meta: { confidence: string; fallback: boolean }) {
      aiMsg.confidence = meta.confidence || 'low'
      aiMsg.fallback = meta.fallback || false
      if (!aiMsg.content) aiMsg.content = '未能生成回答，请重试。'
      loading.value = false
      scrollToBottom()
    },
    onError(message: string) {
      aiMsg.content = aiMsg.content || message || '服务暂时不可用，请稍后重试。'
      aiMsg.confidence = 'low'
      loading.value = false
      scrollToBottom()
    }
  }, recentHistory, category, spaceIds)
}

function addSystemMessage(content: string) {
  messages.value.push({ role: 'system', content })
}

function formatAnswer(text: string): string {
  if (!text) return ''
  return text
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
    .replace(/\[来源:\s*(.*?)\]/g, '<span class="inline-cite">📎 $1</span>')
    .replace(/\n/g, '<br>')
}

function confidenceLabel(level: string): string {
  return level === 'high' ? '高置信度' : level === 'medium' ? '中置信度' : '低置信度'
}

function stepIcon(type: string): string {
  switch (type) {
    case 'analyze': return '🤔'
    case 'search': return '🔍'
    case 'synthesize': return '✅'
    default: return '💭'
  }
}

async function scrollToBottom() {
  await nextTick()
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

function onFeedback(msg: Message, rating: 'useful' | 'useless') {
  msg.feedback = rating
  const idx = messages.value.indexOf(msg)
  const prev = messages.value.slice(0, idx).filter(m => m.role === 'user')
  const q = prev.length ? prev[prev.length - 1] : null
  submitFeedback({ question: q?.content || '', answer: msg.content, rating }).catch(() => {})
}

defineExpose({ addSystemMessage })
</script>

<style scoped>
/* ===== 根容器 ===== */
.chat-root {
  display: flex; flex-direction: column; height: 100%;
  background: #fff;
}

/* ===== 消息区 ===== */
.chat-messages {
  flex: 1; overflow-y: auto; padding: 0 16px;
  scroll-behavior: smooth;
}

/* ===== 欢迎页 ===== */
.welcome {
  display: flex; flex-direction: column; align-items: center;
  padding: 10vh 24px 0; text-align: center;
}
.welcome-logo { margin-bottom: 20px; }
.welcome h2 { font-size: 24px; font-weight: 600; color: #1a1a1a; margin-bottom: 8px; letter-spacing: -.3px; }
.welcome-sub { font-size: 14px; color: #999; margin-bottom: 28px; }
.suggestions { display: flex; gap: 8px; flex-wrap: wrap; justify-content: center; max-width: 520px; }
.suggestion-chip {
  padding: 8px 16px; border: 1px solid #e5e5e5; border-radius: 20px; font-size: 13px;
  color: #666; cursor: pointer; background: #fff; transition: all .15s; font-family: inherit;
}
.suggestion-chip:hover { border-color: #409EFF; color: #409EFF; background: #f0fdf6; }

/* ===== 消息行 ===== */
.msg-row { max-width: 800px; margin: 0 auto; padding: 16px 0; }
.msg-row.user { display: flex; justify-content: flex-end; }
.msg-user-bubble {
  background: #f4f4f5; color: #1a1a1a; padding: 10px 16px; border-radius: 14px;
  font-size: 14px; line-height: 1.6; max-width: 80%;
}

/* ===== AI 回复 ===== */
.msg-ai-wrap { display: flex; gap: 12px; }
.msg-ai-avatar { width: 30px; height: 30px; flex-shrink: 0; margin-top: 2px; }
.msg-ai-content { flex: 1; min-width: 0; }
.msg-ai-text { font-size: 14px; line-height: 1.75; color: #1a1a1a; word-break: break-word; }
.msg-ai-text :deep(p) { margin-bottom: 8px; }
.msg-ai-text :deep(.inline-cite) { color: #409EFF; font-weight: 500; font-size: 12px; }

.cursor-blink { display: inline; animation: blink .7s infinite; color: #409EFF; font-weight: 700; }
@keyframes blink { 0%,100% { opacity: 1; } 50% { opacity: 0; } }

/* ===== 加载动画 ===== */
.thinking-dots { display: flex; gap: 4px; padding: 8px 0; }
.thinking-dots span { width: 6px; height: 6px; border-radius: 50%; background: #ccc; animation: dotPulse 1.2s infinite; }
.thinking-dots span:nth-child(2) { animation-delay: .2s; }
.thinking-dots span:nth-child(3) { animation-delay: .4s; }
@keyframes dotPulse { 0%,60%,100% { opacity: .3; } 30% { opacity: 1; } }

/* ===== 思考链 ===== */
.thinking-chain {
  margin-bottom: 12px;
  background: linear-gradient(135deg, #f0f9ff 0%, #faf5ff 100%);
  border: 1px solid #e0e7ff;
  border-radius: 10px;
  padding: 12px 16px;
}
.thinking-steps { display: flex; flex-direction: column; gap: 8px; }
.thinking-step { display: flex; align-items: baseline; gap: 6px; line-height: 1.5; }
.thinking-step-icon { flex-shrink: 0; font-size: 14px; }
.thinking-step-text { font-size: 13px; color: #374151; }
.thinking-step-hits { font-size: 12px; color: #6b7280; white-space: nowrap; }

/* ===== 降级提示 ===== */
.fallback-badge {
  display: inline-flex; align-items: center; gap: 4px; margin-top: 10px;
  font-size: 12px; color: #b45309; background: #fffbeb; border: 1px solid #fde68a;
  border-radius: 6px; padding: 6px 12px;
}

/* ===== 来源卡片 ===== */
.source-cards { display: flex; flex-direction: column; gap: 6px; margin-top: 12px; }
.source-card {
  display: flex; gap: 10px; padding: 10px 12px; background: #f9fafb;
  border: 1px solid #e5e7eb; border-radius: 8px; cursor: pointer; transition: border-color .15s;
}
.source-card:hover { border-color: #409EFF; }
.source-card-icon { font-size: 14px; flex-shrink: 0; margin-top: 1px; }
.source-card-body { min-width: 0; }
.source-card-title { font-size: 12px; font-weight: 600; color: #409EFF; margin-bottom: 2px; }
.source-card-text { font-size: 12px; color: #666; line-height: 1.5; overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }

/* ===== 消息操作栏 ===== */
.msg-actions { display: flex; align-items: center; justify-content: space-between; margin-top: 10px; }
.msg-actions-left { display: flex; align-items: center; }
.confidence { font-size: 11px; padding: 2px 8px; border-radius: 10px; font-weight: 500; }
.conf-high { background: #ecf5ff; color: #059669; }
.conf-medium { background: #fffbeb; color: #b45309; }
.conf-low { background: #f3f4f6; color: #6b7280; }
.msg-actions-right { display: flex; gap: 4px; }
.action-btn {
  width: 28px; height: 28px; border-radius: 6px; border: none; background: transparent;
  cursor: pointer; font-size: 13px; display: flex; align-items: center; justify-content: center;
  transition: background .12s; opacity: .4;
}
.action-btn:hover { background: #f3f4f6; opacity: .8; }
.action-btn.active { opacity: 1; background: #ecf5ff; }
.action-btn:disabled { cursor: default; }

/* ===== 系统消息 ===== */
.system-msg {
  text-align: center; font-size: 12px; color: #999;
  padding: 6px 16px; max-width: 400px; margin: 0 auto;
}

/* ===== 输入区 ===== */
.input-area { padding: 12px 16px 20px; background: #fff; }
.input-wrap { max-width: 800px; margin: 0 auto; position: relative; }
.filter-bar { display: flex; gap: 8px; margin-bottom: 8px; flex-wrap: wrap; }
.space-select { width: 200px; }
.category-select { width: 200px; }

.chat-input {
  width: 100%; padding: 10px 44px 10px 16px; border: 1px solid #e5e5e5; border-radius: 12px;
  font-size: 14px; outline: none; resize: none; font-family: inherit; line-height: 1.5;
  background: #f9fafb; transition: all .2s; box-shadow: 0 1px 3px rgba(0,0,0,.03);
}
.chat-input:focus { border-color: #409EFF; background: #fff; box-shadow: 0 0 0 3px rgba(64,158,255,.15); }
.chat-input:disabled { opacity: .6; }
.send-btn {
  position: absolute; right: 6px; bottom: 6px; width: 32px; height: 32px; border-radius: 8px;
  background: #409EFF; color: #fff; border: none; cursor: pointer; display: flex;
  align-items: center; justify-content: center; opacity: 0; transform: scale(.8);
  transition: all .15s; pointer-events: none;
}
.send-btn.visible { opacity: 1; transform: scale(1); pointer-events: auto; }
.send-btn:hover { background: #3370cc; }
.send-btn svg { transform: rotate(-90deg); }
.input-footer { text-align: center; font-size: 11px; color: #bbb; margin-top: 8px; }
</style>
