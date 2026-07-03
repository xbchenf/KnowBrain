<template>
  <div class="admin-layout">
    <el-header class="admin-header">
      <div class="header-left">
        <el-button text @click="$router.push('/dashboard')">
          <el-icon><ArrowLeft /></el-icon> 返回
        </el-button>
        <h2>{{ space.name || '空间详情' }}</h2>
      </div>
      <div class="header-right">
        <span class="user-name">{{ userName }}</span>
        <el-button text @click="logout">退出</el-button>
      </div>
    </el-header>

    <el-container>
      <el-aside width="220px" class="admin-sidebar">
        <el-menu :default-active="activeTab" @select="onTabSelect">
          <el-menu-item index="docs">
            <el-icon><Document /></el-icon> 文档列表
          </el-menu-item>
          <el-menu-item index="members">
            <el-icon><User /></el-icon> 成员管理
          </el-menu-item>
          <el-menu-item index="settings" v-if="isOwner">
            <el-icon><Setting /></el-icon> 空间设置
          </el-menu-item>
        </el-menu>
      </el-aside>

      <el-main class="admin-main">
        <!-- ========== 文档列表 ========== -->
        <template v-if="activeTab === 'docs'">
          <div class="toolbar">
            <h3>文档列表</h3>
            <el-upload
              :auto-upload="false"
              :show-file-list="false"
              :on-change="onFileSelect"
              accept=".pdf,.docx,.doc,.txt,.md"
            >
              <el-button type="primary">
                <el-icon><Upload /></el-icon> 上传文档
              </el-button>
            </el-upload>
          </div>

          <!-- 上传进度 -->
          <div v-if="file" class="upload-bar">
            <el-icon><Document /></el-icon>
            <span>{{ file.name }}</span>
            <el-button type="success" size="small" :loading="uploading" @click="doUpload">
              确认上传
            </el-button>
            <el-button size="small" @click="file = null">取消</el-button>
          </div>

          <el-table :data="documents" v-loading="loading" stripe>
            <el-table-column prop="fileName" label="文件名" min-width="200" />
            <el-table-column prop="fileType" label="类型" width="70" />
            <el-table-column label="大小" width="90">
              <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 'READY' ? 'success' : 'warning'" size="small">
                  {{ row.status === 'READY' ? '就绪' : row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="上传时间" width="160">
              <template #default="{ row }">{{ row.createTime?.substring(0, 16) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="160">
              <template #default="{ row }">
                <el-button text size="small" @click="$router.push(`/documents/${row.id}`)">
                  预览
                </el-button>
                <el-button text size="small" type="danger" @click="confirmDelete(row)">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </template>

        <!-- ========== 成员管理 ========== -->
        <template v-if="activeTab === 'members'">
          <div class="toolbar">
            <h3>成员管理</h3>
            <el-button type="primary" @click="showAddMember = true" v-if="isOwner">
              <el-icon><Plus /></el-icon> 添加成员
            </el-button>
          </div>

          <el-table :data="members" v-loading="memberLoading" stripe>
            <el-table-column prop="userId" label="用户 ID" width="100" />
            <el-table-column label="角色" width="120">
              <template #default="{ row }">
                <el-tag :type="roleType(row.role)" size="small">{{ row.role }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="加入时间" width="160" />
            <el-table-column label="操作" v-if="isOwner">
              <template #default="{ row }">
                <el-button text size="small" type="danger"
                           @click="doRemoveMember(row.userId)">移除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <!-- 添加成员弹窗 -->
          <el-dialog v-model="showAddMember" title="添加成员" width="400px">
            <el-form label-position="top">
              <el-form-item label="用户 ID" required>
                <el-input v-model.number="newMember.userId" placeholder="请输入用户 ID" />
              </el-form-item>
              <el-form-item label="角色">
                <el-select v-model="newMember.role" style="width:100%">
                  <el-option label="编辑者 (EDITOR)" value="EDITOR" />
                  <el-option label="阅读者 (VIEWER)" value="VIEWER" />
                </el-select>
              </el-form-item>
            </el-form>
            <template #footer>
              <el-button @click="showAddMember = false">取消</el-button>
              <el-button type="primary" :loading="addingMember" @click="doAddMember">添加</el-button>
            </template>
          </el-dialog>
        </template>

        <!-- ========== 空间设置 ========== -->
        <template v-if="activeTab === 'settings' && isOwner">
          <h3>空间设置</h3>
          <el-form :model="editForm" label-position="top" style="max-width:480px; margin-top:20px">
            <el-form-item label="空间名称">
              <el-input v-model="editForm.name" />
            </el-form-item>
            <el-form-item label="描述">
              <el-input v-model="editForm.description" type="textarea" :rows="3" />
            </el-form-item>
            <el-form-item label="可见性">
              <el-radio-group v-model="editForm.visibility">
                <el-radio value="PRIVATE">私有</el-radio>
                <el-radio value="TEAM">团队</el-radio>
                <el-radio value="PUBLIC">公开</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" @click="doUpdate">保存</el-button>
              <el-button type="danger" plain @click="doDeleteSpace" style="margin-left:24px">
                删除空间
              </el-button>
            </el-form-item>
          </el-form>
        </template>
      </el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft, Document, User, Setting, Upload, Plus
} from '@element-plus/icons-vue'
import {
  getSpace, listMembers, addMember, removeMember,
  updateSpace, deleteSpace, uploadDocument, getDocument, deleteDocument
} from '../api'

const route = useRoute()
const router = useRouter()
const spaceId = Number(route.params.id)

const activeTab = ref('docs')
const loading = ref(false)
const userName = ref(JSON.parse(localStorage.getItem('kb_user') || '{}').name || '')

// 空间信息
const space = ref<any>({})
const isOwner = ref(false)

// 文档
const documents = ref<any[]>([])
const file = ref<File | null>(null)
const uploading = ref(false)

// 成员
const members = ref<any[]>([])
const memberLoading = ref(false)
const showAddMember = ref(false)
const newMember = reactive({ userId: null as number | null, role: 'VIEWER' })
const addingMember = ref(false)

// 设置
const editForm = reactive({ name: '', description: '', visibility: '' })
const saving = ref(false)

onMounted(async () => {
  const u = localStorage.getItem('kb_user')
  if (u) userName.value = JSON.parse(u).name

  await loadSpace()
  await loadDocuments()
  await loadMembers()
})

async function loadSpace() {
  try {
    const res = await getSpace(spaceId)
    space.value = res.data?.data || res.data
    isOwner.value = space.value.ownerId === JSON.parse(localStorage.getItem('kb_user') || '{}').userId
    editForm.name = space.value.name
    editForm.description = space.value.description || ''
    editForm.visibility = space.value.visibility
  } catch { router.push('/dashboard') }
}

async function loadDocuments() {
  loading.value = true
  try {
    const res = await getSpace(spaceId)
    // 文档通过空间详情获取，简化处理
    documents.value = []
  } catch { documents.value = [] }
  finally { loading.value = false }
}

async function loadMembers() {
  memberLoading.value = true
  try {
    const res = await listMembers(spaceId)
    members.value = res.data?.data || []
  } catch { members.value = [] }
  finally { memberLoading.value = false }
}

function onFileSelect(uploadFile: any) {
  file.value = uploadFile.raw || null
}

async function doUpload() {
  if (!file.value) return
  uploading.value = true
  try {
    await uploadDocument(file.value, spaceId)
    ElMessage.success('上传成功')
    file.value = null
    await loadDocuments()
  } catch { ElMessage.error('上传失败') }
  finally { uploading.value = false }
}

async function confirmDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.fileName}」？`, '删除确认', { type: 'warning' })
    await deleteDocument(row.id)
    ElMessage.success('已删除')
    await loadDocuments()
  } catch { /* cancelled */ }
}

async function doAddMember() {
  if (!newMember.userId) return
  addingMember.value = true
  try {
    await addMember(spaceId, newMember.userId, newMember.role)
    ElMessage.success('添加成功')
    showAddMember.value = false
    newMember.userId = null
    newMember.role = 'VIEWER'
    await loadMembers()
  } catch { ElMessage.error('添加失败') }
  finally { addingMember.value = false }
}

async function doRemoveMember(userId: number) {
  try {
    await ElMessageBox.confirm('确认移除该成员？', '移除确认', { type: 'warning' })
    await removeMember(spaceId, userId)
    ElMessage.success('已移除')
    await loadMembers()
  } catch { /* cancelled */ }
}

async function doUpdate() {
  saving.value = true
  try {
    await updateSpace(spaceId, {
      name: editForm.name,
      description: editForm.description,
      visibility: editForm.visibility
    })
    ElMessage.success('保存成功')
    await loadSpace()
  } catch { ElMessage.error('保存失败') }
  finally { saving.value = false }
}

async function doDeleteSpace() {
  try {
    await ElMessageBox.confirm('删除空间将同时删除其中所有文档，此操作不可恢复。确认继续？',
      '危险操作', { type: 'error', confirmButtonText: '确认删除' })
    await deleteSpace(spaceId)
    ElMessage.success('空间已删除')
    router.push('/dashboard')
  } catch { /* cancelled */ }
}

function logout() {
  localStorage.removeItem('kb_token')
  localStorage.removeItem('kb_user')
  router.push('/login')
}

function onTabSelect(tab: string) { activeTab.value = tab }

function roleType(role: string) {
  return role === 'OWNER' ? 'danger' : role === 'EDITOR' ? 'warning' : 'info'
}

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
.header-right { display: flex; align-items: center; gap: 12px; }
.user-name { color: #606266; font-size: 14px; }
.admin-sidebar { background: #fff; border-right: 1px solid #e4e7ed; min-height: calc(100vh - 56px); }
.admin-main { padding: 24px; }
.toolbar {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 20px;
}
.toolbar h3 { font-size: 18px; color: #303133; }
.upload-bar {
  display: flex; align-items: center; gap: 12px;
  padding: 12px 16px; background: #ecf5ff; border-radius: 8px; margin-bottom: 16px;
}
</style>
