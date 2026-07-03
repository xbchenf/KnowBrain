<template>
  <div class="admin-layout">
    <el-header class="admin-header">
      <div class="header-left">
        <el-button text @click="$router.back()">
          <el-icon><ArrowLeft /></el-icon> 返回
        </el-button>
        <h2>{{ doc.fileName || '文档预览' }}</h2>
      </div>
    </el-header>

    <el-main class="preview-main" v-loading="loading">
      <!-- 文档信息 -->
      <div class="doc-meta">
        <el-tag :type="doc.status === 'READY' ? 'success' : 'warning'">
          {{ doc.status === 'READY' ? '已解析' : doc.status }}
        </el-tag>
        <span>{{ doc.fileType?.toUpperCase() }}</span>
        <span>{{ formatSize(doc.fileSize) }}</span>
        <span v-if="doc.chunkCount">{{ doc.chunkCount }} 个切片</span>
      </div>

      <!-- 全文预览 -->
      <el-card v-if="doc.parsedContent" class="content-card">
        <template #header>文档全文</template>
        <div class="content-text" v-html="formattedContent"></div>
      </el-card>

      <el-empty v-else-if="!loading" description="文档尚未解析或解析失败" />
    </el-main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getDocument } from '../api'

const route = useRoute()
const docId = Number(route.params.id)

const doc = ref<any>({})
const loading = ref(false)

const formattedContent = computed(() => {
  const text = doc.value.parsedContent || ''
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\n/g, '<br>')
})

onMounted(async () => {
  loading.value = true
  try {
    const res = await getDocument(docId)
    doc.value = res.data?.data || res.data
  } catch { doc.value = {} }
  finally { loading.value = false }
})

function formatSize(b: number) {
  if (!b) return '0 B'
  if (b < 1024) return b + ' B'
  if (b < 1024 * 1024) return (b / 1024).toFixed(1) + ' KB'
  return (b / (1024 * 1024)).toFixed(1) + ' MB'
}
</script>

<style scoped>
.admin-layout { min-height: 100vh; background: #f5f7fa; }
.admin-header {
  display: flex; align-items: center; justify-content: space-between;
  background: #fff; border-bottom: 1px solid #e4e7ed; padding: 0 24px; height: 56px;
}
.header-left { display: flex; align-items: center; gap: 12px; }
.header-left h2 { font-size: 16px; color: #303133; }
.preview-main { max-width: 900px; margin: 24px auto; padding: 0 24px; }
.doc-meta {
  display: flex; align-items: center; gap: 16px;
  margin-bottom: 20px; color: #909399; font-size: 14px;
}
.content-card { margin-bottom: 24px; }
.content-text {
  line-height: 1.9; font-size: 15px; color: #303133;
  white-space: pre-wrap; word-break: break-all;
}
</style>
