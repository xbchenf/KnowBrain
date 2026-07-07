<template>
  <div class="doc-browse">
    <!-- 无选中空间 — 提示 -->
    <div class="doc-empty" v-if="!spaceId">
      <el-empty description="请从左侧选择一个空间" :image-size="64" />
    </div>

    <!-- 文档区 -->
    <template v-else>
      <div class="doc-toolbar">
        <h3>{{ spaceName || '文档列表' }}</h3>
      </div>

      <el-table :data="documents" v-loading="docLoading" stripe>
        <el-table-column prop="title" label="文件名" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="doc-title-link" @click="previewDoc(row)">{{ row.title || row.fileName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ (row.fileType || '').toUpperCase() }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="90">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="上传时间" width="160">
          <template #default="{ row }">{{ row.createTime?.substring(0, 16) || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="previewDoc(row)">预览</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager-wrap" v-if="totalDocs > pagination.size">
        <el-pagination
          v-model:current-page="pagination.page"
          :page-size="pagination.size"
          :total="totalDocs"
          layout="total, prev, pager, next"
          size="small"
          @current-change="loadDocuments"
        />
      </div>
    </template>

    <!-- 预览弹窗 -->
    <el-dialog v-model="previewVisible" :title="previewDocTitle" width="720px" top="5vh">
      <div class="preview-content" v-html="previewHtml"></div>
      <template #footer>
        <el-button @click="previewVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch, computed } from 'vue'
import { useRoute } from 'vue-router'
import { listDocuments, getDocument } from '../api'

const route = useRoute()
const spaceId = computed(() => {
  const id = route.query.spaceId
  return id ? Number(id) : null
})
const spaceName = computed(() => (route.query.spaceName as string) || '')

const documents = ref<any[]>([])
const docLoading = ref(false)
const totalDocs = ref(0)
const pagination = reactive({ page: 1, size: 20 })

const previewVisible = ref(false)
const previewDocTitle = ref('')
const previewHtml = ref('')

watch(spaceId, (id) => {
  if (id) {
    pagination.page = 1
    loadDocuments()
  }
})

async function loadDocuments() {
  if (!spaceId.value) return
  docLoading.value = true
  try {
    const { data } = await listDocuments(spaceId.value, pagination.page, pagination.size)
    if (data?.code === 200) {
      documents.value = data.data?.records || []
      totalDocs.value = data.data?.total || 0
    }
  } catch { documents.value = [] }
  finally { docLoading.value = false }
}

async function previewDoc(row: any) {
  previewDocTitle.value = row.title || row.fileName || '文档预览'
  previewVisible.value = true
  previewHtml.value = '<p style="color:#909399">加载中...</p>'
  try {
    const { data } = await getDocument(row.id)
    if (data?.code === 200) {
      const content = data.data?.parsedContent || '暂无内容'
      previewHtml.value = content
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/\n/g, '<br>')
    } else {
      previewHtml.value = '<p style="color:#909399">无法加载文档内容</p>'
    }
  } catch {
    previewHtml.value = '<p style="color:#f56c6c">加载失败</p>'
  }
}

function formatSize(bytes: number): string {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + 'B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + 'KB'
  return (bytes / (1024 * 1024)).toFixed(1) + 'MB'
}
</script>

<style scoped>
.doc-browse {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #fff;
}
.doc-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}
.doc-toolbar {
  padding: 20px 24px 16px;
  border-bottom: 1px solid #f0f0f0;
}
.doc-toolbar h3 { font-size: 17px; color: #303133; }
.doc-title-link { color: #409EFF; cursor: pointer; }
.doc-title-link:hover { text-decoration: underline; }
.pager-wrap {
  display: flex;
  justify-content: flex-end;
  padding: 16px 24px;
}

/* 预览 */
.preview-content {
  max-height: 70vh;
  overflow-y: auto;
  font-size: 14px;
  line-height: 1.8;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
