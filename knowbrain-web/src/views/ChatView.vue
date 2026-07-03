<template>
  <div class="chat-container">
    <!-- 消息列表 -->
    <div class="chat-messages" ref="messagesRef">
      <div v-if="messages.length === 0" class="chat-empty">
        <el-icon :size="48"><ChatDotRound /></el-icon>
        <h3>欢迎使用 KnowBrain</h3>
        <p>上传文档后，输入问题即可获取 AI 精准回答</p>
      </div>

      <div v-for="(msg, i) in messages" :key="i" :class="['chat-message', msg.role]">
        <!-- 用户消息 -->
        <template v-if="msg.role === 'user'">
          <div class="message-avatar user-avatar">
            <el-icon :size="20"><User /></el-icon>
          </div>
          <div class="message-bubble user-bubble">{{ msg.content }}</div>
        </template>

        <!-- AI 回复 -->
        <template v-else-if="msg.role === 'assistant'">
          <div class="message-avatar ai-avatar">
            <el-icon :size="20"><Cpu /></el-icon>
          </div>
          <div class="message-content">
            <div :class="['message-bubble', 'ai-bubble', { streaming: loading && i === messages.length - 1 && msg.role === 'assistant' }]">
              <span v-html="formatAnswer(msg.content)"></span>
              <span v-if="loading && i === messages.length - 1 && msg.role === 'assistant'" class="stream-cursor">|</span>
            </div>
            <!-- 降级提示 -->
            <div v-if="msg.fallback" class="fallback-notice">
              <el-icon><WarningFilled /></el-icon>
              AI 生成暂不可用，以上为检索到的相关原文
            </div>
            <!-- 溯源面板 -->
            <div v-if="msg.sources && msg.sources.length" class="source-panel">
              <el-collapse>
                <el-collapse-item :title="`📎 参考来源 (${msg.sources.length})`">
                  <div v-for="(s, si) in msg.sources" :key="si" class="source-item">
                    <div class="source-title">
                      {{ s.title }}
                      <span class="source-chunk">· 第 {{ s.chunkIndex + 1 }} 片段</span>
                    </div>
                    <div class="source-text">{{ s.text }}</div>
                  </div>
                </el-collapse-item>
              </el-collapse>
            </div>
            <!-- 置信度 -->
            <div v-if="msg.confidence" class="confidence-tag">
              <el-tag :type="confidenceType(msg.confidence)" size="small">
                置信度: {{ confidenceLabel(msg.confidence) }}
              </el-tag>
            </div>
            <!-- 反馈按钮 -->
            <div v-if="msg.role === 'assistant' && msg.content" class="feedback-btns">
              <el-button
                :type="msg.feedback === 'useful' ? 'success' : 'default'"
                size="small" text
                :disabled="!!msg.feedback"
                @click="onFeedback(msg, 'useful')"
              >👍 有用</el-button>
              <el-button
                :type="msg.feedback === 'useless' ? 'danger' : 'default'"
                size="small" text
                :disabled="!!msg.feedback"
                @click="onFeedback(msg, 'useless')"
              >👎 无用</el-button>
              <span v-if="msg.feedback" class="feedback-done">感谢反馈</span>
            </div>
          </div>
        </template>

        <!-- 系统消息 -->
        <template v-else-if="msg.role === 'system'">
          <div class="system-message">
            <el-icon><InfoFilled /></el-icon>
            {{ msg.content }}
          </div>
        </template>
      </div>

      <!-- 加载状态（首 token 到达前） -->
      <div v-if="loading && (!messages.length || messages[messages.length-1]?.content !== '')" class="chat-message assistant">
        <div class="message-avatar ai-avatar">
          <el-icon :size="20"><Cpu /></el-icon>
        </div>
        <div class="message-bubble ai-bubble thinking">
          正在检索知识库...
        </div>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="chat-input-area">
      <el-input
        v-model="input"
        type="textarea"
        :rows="2"
        placeholder="输入你的问题，例如：VPN 怎么配置？"
        :disabled="loading"
        @keydown.enter.exact.prevent="send"
        resize="none"
      />
      <div class="input-actions">
        <span class="input-hint">Enter 发送</span>
        <el-button type="primary" :disabled="!input.trim() || loading" @click="send">
          <el-icon><Promotion /></el-icon>
          发送
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, watch } from 'vue'
import { ChatDotRound, User, Cpu, InfoFilled, Promotion, WarningFilled } from '@element-plus/icons-vue'
import { chatWithKnowledge, chatWithKnowledgeStream, submitFeedback } from '../api'

interface Message {
  role: 'user' | 'assistant' | 'system'
  content: string
  sources?: { title: string; documentId: number; chunkIndex: number; text: string }[]
  confidence?: string
  fallback?: boolean
  feedback?: string  // 'useful' | 'useless' | undefined
}

const messages = ref<Message[]>([])
const input = ref('')
const loading = ref(false)
const messagesRef = ref<HTMLElement>()

async function send() {
  const question = input.value.trim()
  if (!question || loading.value) return

  messages.value.push({ role: 'user', content: question })
  input.value = ''
  loading.value = true

  // 先插入一个占位的 assistant 消息，token 到达时逐步更新
  const aiMsg: Message = { role: 'assistant', content: '' }
  messages.value.push(aiMsg)
  await scrollToBottom()

  await chatWithKnowledgeStream(question, {
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
      if (!aiMsg.content) {
        aiMsg.content = '未能生成回答，请重试。'
      }
      loading.value = false
      scrollToBottom()
    },
    onError(message: string) {
      aiMsg.content = aiMsg.content || message || '服务暂时不可用，请稍后重试。'
      aiMsg.confidence = 'low'
      loading.value = false
      scrollToBottom()
    }
  })
}

function addSystemMessage(content: string) {
  messages.value.push({ role: 'system', content })
}

function formatAnswer(text: string): string {
  if (!text) return ''
  // 先转义 HTML 防止 XSS，再做格式化
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/\[来源:\s*(.*?)\]/g, '<span class="citation">📎 $1</span>')
    .replace(/\n/g, '<br>')
}

function confidenceType(level: string): string {
  return level === 'high' ? 'success' : level === 'medium' ? 'warning' : 'info'
}

function confidenceLabel(level: string): string {
  return level === 'high' ? '高' : level === 'medium' ? '中' : '低'
}

async function scrollToBottom() {
  await nextTick()
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

function onFeedback(msg: Message, rating: 'useful' | 'useless') {
  msg.feedback = rating
  const q = messages.value.find(m => m.role === 'user' && messages.value.indexOf(m) < messages.value.indexOf(msg))
  const question = q ? q.content : ''
  submitFeedback({ question, answer: msg.content, rating }).catch(() => {})
}

defineExpose({ addSystemMessage })
</script>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 56px);
  max-width: 900px;
  margin: 0 auto;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

.chat-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #909399;
  gap: 12px;
}

.chat-empty h3 {
  color: #606266;
  font-weight: 600;
}

.chat-message {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
}

.chat-message.user { flex-direction: row-reverse; }

.message-avatar {
  width: 36px; height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.user-avatar { background: #ecf5ff; color: #409eff; }
.ai-avatar { background: #f0f9eb; color: #67c23a; }

.message-bubble {
  max-width: 75%;
  padding: 12px 18px;
  border-radius: 12px;
  line-height: 1.7;
  font-size: 15px;
}

.user-bubble {
  background: #409eff;
  color: #fff;
  border-bottom-right-radius: 4px;
}

.ai-bubble {
  background: #fff;
  color: #303133;
  border: 1px solid #e4e7ed;
  border-bottom-left-radius: 4px;
}

.thinking {
  opacity: 0.6;
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 0.6; }
  50% { opacity: 1; }
}

.streaming {
  /* streaming bubble is interactive */
}

.stream-cursor {
  display: inline-block;
  animation: blink 0.7s infinite;
  color: #409eff;
  font-weight: bold;
  margin-left: 1px;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

.message-content {
  max-width: 75%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.source-panel {
  background: #fafafa;
  border-radius: 8px;
  border: 1px solid #ebeef5;
  overflow: hidden;
}

.source-panel :deep(.el-collapse-item__header) {
  padding: 8px 12px;
  font-size: 13px;
  background: #f5f7fa;
  border: none;
}

.source-panel :deep(.el-collapse-item__content) {
  padding: 12px;
}

.source-item {
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px dashed #ebeef5;
}

.source-item:last-child { margin-bottom: 0; padding-bottom: 0; border: none; }

.source-title {
  font-weight: 600;
  font-size: 13px;
  color: #409eff;
  margin-bottom: 4px;
}

.source-chunk {
  font-weight: 400;
  font-size: 12px;
  color: #909399;
  margin-left: 4px;
}

.source-text {
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
  max-height: 80px;
  overflow-y: auto;
}

.confidence-tag { margin-top: 4px; }

.feedback-btns {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
}

.feedback-done {
  font-size: 12px;
  color: #909399;
  margin-left: 8px;
}

.fallback-notice {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #e6a23c;
  background: #fdf6ec;
  border: 1px solid #faecd8;
  border-radius: 6px;
  padding: 6px 12px;
}

.system-message {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 13px;
  color: #909399;
  background: #f5f7fa;
  border-radius: 8px;
  padding: 8px 16px;
  margin: 8px auto;
}

:deep(.citation) {
  color: #409eff;
  font-weight: 500;
}

.chat-input-area {
  padding: 16px 24px;
  background: #fff;
  border-top: 1px solid #e4e7ed;
}

.chat-input-area :deep(.el-textarea__inner) {
  border-radius: 8px;
  font-size: 15px;
}

.input-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
}

.input-hint {
  font-size: 12px;
  color: #c0c4cc;
}
</style>
