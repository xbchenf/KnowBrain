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
          <el-menu-item index="members" v-if="space.visibility === 'PRIVATE'">
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
            <el-tree-select
              v-model="uploadCategory"
              :data="categoryTreeData"
              :props="{ label: 'name', value: 'key', children: 'children' }"
              placeholder="选择分类（可选）"
              clearable
              check-strictly
              filterable
              size="small"
              style="width:200px"
            />
            <el-button type="success" size="small" :loading="uploading" @click="doUpload">
              确认上传
            </el-button>
            <el-button size="small" @click="file = null; uploadCategory = ''">取消</el-button>
          </div>

          <!-- 编辑文档弹窗 -->
          <el-dialog v-model="editVisible" title="编辑文档" width="480px">
            <el-form label-position="top" v-if="editDoc">
              <el-form-item label="文档标题">
                <el-input v-model="editDoc.title" />
              </el-form-item>
              <el-form-item label="分类">
                <el-tree-select
                  v-model="editDoc.category"
                  :data="categoryTreeData"
                  :props="{ label: 'name', value: 'key', children: 'children' }"
                  placeholder="选择分类（可选）"
                  clearable
                  check-strictly
                  filterable
                  style="width:100%"
                />
              </el-form-item>
            </el-form>
            <template #footer>
              <el-button @click="editVisible = false">取消</el-button>
              <el-button type="primary" :loading="savingEdit" @click="doEdit">保存</el-button>
            </template>
          </el-dialog>

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
            <el-table-column label="上传人" width="100">
              <template #default="{ row }">{{ row.uploaderName || '未知' }}</template>
            </el-table-column>
            <el-table-column label="分类" width="110">
              <template #default="{ row }">{{ categoryNameMap[row.category] || '-' }}</template>
            </el-table-column>
            <el-table-column label="上传时间" width="160">
              <template #default="{ row }">{{ row.createTime?.substring(0, 16) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="200">
              <template #default="{ row }">
                <el-button text size="small" @click="openEdit(row)">
                  编辑
                </el-button>
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
            <el-table-column label="用户" min-width="200">
              <template #default="{ row }">{{ userNameWithDept(row.userId) }}</template>
            </el-table-column>
            <el-table-column prop="createTime" label="加入时间" width="180" />
            <el-table-column label="操作" v-if="isOwner">
              <template #default="{ row }">
                <el-button text size="small" type="danger"
                           @click="doRemoveMember(row.userId)">移除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <!-- 添加成员弹窗 -->
          <el-dialog v-model="showAddMember" title="添加成员" width="520px" @opened="userKeyword = ''">
            <el-form label-position="top">
              <el-form-item label="搜索用户" required>
                <el-input v-model="userKeyword" placeholder="输入姓名或账号搜索，支持模糊匹配"
                  clearable prefix-icon="Search" />
              </el-form-item>
            </el-form>
            <div class="user-select-list" v-if="filteredUsers.length">
              <div v-for="u in filteredUsers" :key="u.id"
                   :class="['user-select-item', { selected: newMember.userId === u.id }]"
                   @click="newMember.userId = u.id">
                <div class="user-select-name">{{ u.name }} <span class="user-select-account">@{{ u.username }}</span></div>
                <div class="user-select-dept">{{ deptNameMap[u.departmentId] || '未分配部门' }}</div>
                <el-icon v-if="newMember.userId === u.id" class="user-select-check" color="#409EFF"><Check /></el-icon>
              </div>
            </div>
            <div v-else class="user-select-empty">输入关键词搜索用户</div>
            <template #footer>
              <el-button @click="showAddMember = false">取消</el-button>
              <el-button type="primary" :loading="addingMember" :disabled="!newMember.userId" @click="doAddMember">
                添加成员
              </el-button>
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
                <el-radio value="PRIVATE">私有（仅指定成员）</el-radio>
                <el-radio value="TEAM">团队（按部门）</el-radio>
                <el-radio value="PUBLIC">公开（所有人）</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item v-if="editForm.visibility === 'TEAM'" label="可见部门">
              <el-select v-model="editForm.departmentScope" multiple placeholder="选择可见部门" style="width:100%">
                <el-option v-for="d in allDepartments" :key="d.id" :label="d.name" :value="d.id" />
              </el-select>
              <div style="font-size:12px;color:#909399;margin-top:4px">选择后，仅这些部门的成员可以访问此空间</div>
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
  ArrowLeft, Document, User, Setting, Upload, Plus, Check, Search
} from '@element-plus/icons-vue'
import {
  getSpace, listMembers, addMember, removeMember,
  updateSpace, deleteSpace, uploadDocument, listDocuments, updateDocument, deleteDocument,
  listDepartments, listUsers, listPublicCategories
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
const currentUser = JSON.parse(localStorage.getItem('kb_user') || '{}')

// 文档
const documents = ref<any[]>([])
const file = ref<File | null>(null)
const uploading = ref(false)

// 编辑文档
const editVisible = ref(false)
const editDoc = ref<{ id: number; title: string; category: string } | null>(null)
const savingEdit = ref(false)

// 成员
const members = ref<any[]>([])
const memberLoading = ref(false)
const showAddMember = ref(false)
const newMember = reactive({ userId: null as number | null })
const addingMember = ref(false)

// 设置
const editForm = reactive({ name: '', description: '', visibility: '', departmentScope: [] as number[] })
const saving = ref(false)

// 部门列表（TEAM 模式用）
const allDepartments = ref<any[]>([])
const deptNameMap = ref<Record<number, string>>({})

// 分类（上传文档时选择 + 文档列表显示）
const categoryTreeData = ref<any[]>([])
const categoryNameMap = ref<Record<string, string>>({})
const uploadCategory = ref('')

// 用户列表（添加成员用）
const allUsers = ref<any[]>([])
const userKeyword = ref('')
const filteredUsers = computed(() => {
  if (!userKeyword.value) return allUsers.value.slice(0, 20)
  const kw = userKeyword.value.toLowerCase()
  return allUsers.value.filter((u: any) =>
    u.name?.toLowerCase().includes(kw) || u.username?.toLowerCase().includes(kw)
  ).slice(0, 20)
})

/** 根据 userId 获取用户展示名称（含部门） */
function userNameWithDept(uid: number): string {
  const u = allUsers.value.find((x: any) => x.id === uid)
  if (!u) return `用户 #${uid}`
  const dept = u.departmentId ? (deptNameMap.value[u.departmentId] || '') : ''
  return dept ? `${u.name} (${u.username}) — ${dept}` : `${u.name} (${u.username})`
}

onMounted(async () => {
  userName.value = currentUser.name || ''

  await loadSpace()
  await loadDocuments()
  await loadMembers()

  // 加载部门树（TEAM 模式 + 用户部门名展示）
  try {
    const deptRes = await listDepartments()
    allDepartments.value = flatten(deptRes.data?.data || [])
    const map: Record<number, string> = {}
    for (const d of allDepartments.value) {
      map[d.id] = d.name
    }
    deptNameMap.value = map
  } catch (e) {
    console.error('加载部门列表失败:', e)
  }

  // 加载用户列表（成员管理使用）
  try {
    const userRes = await listUsers({ page: 1, size: 200 })
    allUsers.value = userRes.data?.data?.records || userRes.data?.data || []
  } catch (e) {
    console.error('加载用户列表失败:', e)
  }

  // 加载分类树（上传文档选择分类 + 列表显示分类名）
  try {
    const catRes = await listPublicCategories()
    const tree = catRes.data?.data || []
    categoryTreeData.value = tree
    const flattened = flatten(tree)
    const map: Record<string, string> = {}
    for (const c of flattened) map[c.key] = c.name
    categoryNameMap.value = map
  } catch (e) {
    console.error('加载分类列表失败:', e)
  }
})

async function loadSpace() {
  try {
    const res = await getSpace(spaceId)
    space.value = res.data?.data || res.data
    // isOwner 由服务端判断（ADMIN 全局 + 空间创建者），前端不自行授权
    isOwner.value = space.value.isOwner === true
    editForm.name = space.value.name
    editForm.description = space.value.description || ''
    editForm.visibility = space.value.visibility
    // 解析已保存的 departmentScope（逗号分隔 → 数字数组）
    const scope = space.value.departmentScope
    editForm.departmentScope = scope ? scope.split(',').map(Number).filter((n: number) => !isNaN(n)) : []
  } catch { router.push('/dashboard') }
}

async function loadDocuments() {
  loading.value = true
  try {
    const res = await listDocuments(spaceId, 1, 100)
    documents.value = res.data?.data?.records || []
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
    await uploadDocument(file.value, spaceId, uploadCategory.value || undefined)
    ElMessage.success('上传成功')
    file.value = null
    uploadCategory.value = ''
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

function openEdit(row: any) {
  editDoc.value = {
    id: row.id,
    title: row.title || row.fileName || '',
    category: row.category || ''
  }
  editVisible.value = true
}

async function doEdit() {
  if (!editDoc.value) return
  savingEdit.value = true
  try {
    await updateDocument(editDoc.value.id, {
      title: editDoc.value.title,
      category: editDoc.value.category || null
    })
    ElMessage.success('已更新')
    editVisible.value = false
    await loadDocuments()
  } catch { ElMessage.error('更新失败') }
  finally { savingEdit.value = false }
}

async function doAddMember() {
  if (!newMember.userId) return
  addingMember.value = true
  try {
    await addMember(spaceId, newMember.userId, 'VIEWER')
    ElMessage.success('添加成功')
    showAddMember.value = false
    newMember.userId = null
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
      visibility: editForm.visibility,
      departmentScope: editForm.departmentScope
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

function flatten(nodes: any[]): any[] {
  let r: any[] = []
  for (const n of nodes) { r.push(n); if (n.children) r = r.concat(flatten(n.children)) }
  return r
}

function logout() {
  localStorage.removeItem('kb_token')
  localStorage.removeItem('kb_user')
  router.push('/login')
}

function onTabSelect(tab: string) { activeTab.value = tab }

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

/* 用户选择列表 */
.user-select-list { max-height: 300px; overflow-y: auto; border: 1px solid #e4e7ed; border-radius: 8px; }
.user-select-item {
  display: flex; align-items: center; padding: 10px 12px; cursor: pointer;
  border-bottom: 1px solid #f0f0f0; transition: background .1s;
}
.user-select-item:last-child { border-bottom: none; }
.user-select-item:hover { background: #f5f7fa; }
.user-select-item.selected { background: #ecf5ff; }
.user-select-name { flex: 1; font-size: 14px; font-weight: 500; color: #303133; }
.user-select-account { font-weight: 400; font-size: 12px; color: #909399; }
.user-select-dept { font-size: 12px; color: #909399; margin-right: 12px; }
.user-select-check { flex-shrink: 0; }
.user-select-empty { padding: 40px 16px; text-align: center; font-size: 13px; color: #c0c4cc; }
</style>
