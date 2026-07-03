<template>
  <el-dialog
    v-model="dialogVisible"
    title="上传文档"
    width="520px"
    :close-on-click-modal="false"
    @close="reset"
  >
    <el-upload
      ref="uploadRef"
      drag
      :auto-upload="false"
      :limit="1"
      :on-change="onFileChange"
      :on-remove="onFileRemove"
      accept=".pdf,.docx,.doc,.txt,.md"
    >
      <el-icon :size="40"><UploadFilled /></el-icon>
      <div class="upload-text">
        <p>将文件拖到此处，或<em>点击选择</em></p>
        <p class="upload-hint">支持 PDF / Word / TXT / Markdown</p>
      </div>
    </el-upload>

    <div v-if="file" class="file-info">
      <el-icon><Document /></el-icon>
      <span class="file-name">{{ file.name }}</span>
      <span class="file-size">{{ formatSize(file.size) }}</span>
    </div>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :disabled="!file || uploading" :loading="uploading" @click="doUpload">
        {{ uploading ? '上传中...' : '开始上传' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled, Document } from '@element-plus/icons-vue'
import { uploadDocument } from '../api'
import type { UploadFile } from 'element-plus'

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{ (e: 'update:visible', v: boolean): void; (e: 'uploaded', doc: any): void }>()

const dialogVisible = computed({
  get: () => props.visible,
  set: (v) => emit('update:visible', v)
})

const file = ref<File | null>(null)
const uploading = ref(false)
const uploadRef = ref()

function onFileChange(uploadFile: UploadFile) {
  file.value = uploadFile.raw || null
}

function onFileRemove() {
  file.value = null
}

async function doUpload() {
  if (!file.value) return
  uploading.value = true
  try {
    const res = await uploadDocument(file.value)
    emit('uploaded', res.data)
    ElMessage.success('文档上传成功')
    dialogVisible.value = false
  } catch {
    ElMessage.error('上传失败，请重试')
  } finally {
    uploading.value = false
  }
}

function reset() {
  file.value = null
  uploadRef.value?.clearFiles()
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}
</script>

<style scoped>
.upload-text p {
  margin: 8px 0;
  color: #606266;
}

.upload-text em {
  color: #409eff;
  font-style: normal;
}

.upload-hint {
  font-size: 13px;
  color: #c0c4cc;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 8px;
}

.file-name {
  flex: 1;
  font-size: 14px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-size {
  font-size: 13px;
  color: #909399;
}
</style>
