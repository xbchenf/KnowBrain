<template>
  <div class="user-page">
      <!-- 工具栏：筛选 + 新建 -->
      <div class="toolbar">
        <div class="toolbar-left">
          <el-input v-model="keyword" placeholder="搜索用户名/姓名" clearable style="width:200px" @change="load" />
          <el-select v-model="deptFilter" clearable placeholder="按部门过滤" @change="load" style="width:160px">
            <el-option v-for="d in departments" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
          <el-select v-model="roleFilter" clearable placeholder="按角色过滤" @change="load" style="width:140px">
            <el-option label="普通用户" value="USER" />
            <el-option label="知识管理员" value="MANAGER" />
            <el-option label="系统管理员" value="ADMIN" />
          </el-select>
          <el-select v-model="statusFilter" clearable placeholder="按状态过滤" @change="load" style="width:120px">
            <el-option label="正常" value="ACTIVE" />
            <el-option label="已禁用" value="DISABLED" />
          </el-select>
        </div>
        <div class="toolbar-right">
          <el-button type="primary" @click="openCreate">
            <el-icon><Plus /></el-icon> 新建用户
          </el-button>
        </div>
      </div>

      <!-- 用户表格 -->
      <el-table :data="users" v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="name" label="姓名" width="100" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column label="角色" width="110">
          <template #default="{ row }">
            <el-tag :type="roleTag(row.role)" size="small">{{ roleLabel(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="部门" width="120">
          <template #default="{ row }">{{ deptName(row.departmentId) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" size="small">
              {{ row.status === 'ACTIVE' ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="注册时间" min-width="150">
          <template #default="{ row }">{{ row.createTime?.substring(0, 16) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="warning" size="small" @click="openResetPwd(row)">重置密码</el-button>
            <el-button
              link
              :type="row.status === 'ACTIVE' ? 'danger' : 'success'"
              size="small"
              @click="toggleStatus(row)"
            >
              {{ row.status === 'ACTIVE' ? '禁用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        :page-size="size"
        :total="total"
        layout="prev, pager, next, total"
        @current-change="load"
        style="margin-top:16px;justify-content:center"
      />

      <!-- 新建用户弹窗 -->
      <el-dialog v-model="createVisible" title="新建用户" width="480px" :close-on-click-modal="false">
        <el-form :model="createForm" label-width="80px">
          <el-form-item label="用户名" required>
            <el-input v-model="createForm.username" placeholder="登录账号，3-50 字符" maxlength="50" />
          </el-form-item>
          <el-form-item label="密码" required>
            <el-input v-model="createForm.password" placeholder="至少 6 位" show-password maxlength="50" />
          </el-form-item>
          <el-form-item label="姓名">
            <el-input v-model="createForm.name" placeholder="显示名称，默认同用户名" maxlength="50" />
          </el-form-item>
          <el-form-item label="手机号" required>
            <el-input v-model="createForm.phone" placeholder="请输入手机号" maxlength="11"
              @input="createForm.phone = filterPhoneInput(createForm.phone)" />
          </el-form-item>
          <el-form-item label="部门">
            <el-select v-model="createForm.departmentId" clearable placeholder="选择部门" style="width:100%">
              <el-option v-for="d in departments" :key="d.id" :label="d.name" :value="d.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="角色">
            <el-select v-model="createForm.role" style="width:100%">
              <el-option label="普通用户" value="USER" />
              <el-option label="知识管理员" value="MANAGER" />
              <el-option label="系统管理员" value="ADMIN" />
            </el-select>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="createVisible = false">取消</el-button>
          <el-button type="primary" @click="doCreate" :loading="submitting">创建</el-button>
        </template>
      </el-dialog>

      <!-- 编辑用户弹窗 -->
      <el-dialog v-model="editVisible" title="编辑用户" width="480px" :close-on-click-modal="false">
        <el-form :model="editForm" label-width="80px">
          <el-form-item label="用户名">
            <el-input :model-value="editForm.username" disabled />
          </el-form-item>
          <el-form-item label="姓名">
            <el-input v-model="editForm.name" placeholder="显示名称" maxlength="50" />
          </el-form-item>
          <el-form-item label="手机号">
            <el-input v-model="editForm.phone" placeholder="请输入手机号" maxlength="11"
              @input="editForm.phone = filterPhoneInput(editForm.phone)" />
          </el-form-item>
          <el-form-item label="部门">
            <el-select v-model="editForm.departmentId" clearable placeholder="选择部门" style="width:100%">
              <el-option v-for="d in departments" :key="d.id" :label="d.name" :value="d.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="角色">
            <el-select v-model="editForm.role" style="width:100%">
              <el-option label="普通用户" value="USER" />
              <el-option label="知识管理员" value="MANAGER" />
              <el-option label="系统管理员" value="ADMIN" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-radio-group v-model="editForm.status">
              <el-radio value="ACTIVE">正常</el-radio>
              <el-radio value="DISABLED">禁用</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="editVisible = false">取消</el-button>
          <el-button type="primary" @click="doEdit" :loading="submitting">保存</el-button>
        </template>
      </el-dialog>

      <!-- 重置密码弹窗 -->
      <el-dialog v-model="resetVisible" title="重置密码" width="400px" :close-on-click-modal="false">
        <p style="margin-bottom:12px;color:#606266">
          为用户 <b>{{ resetTarget?.name }}</b>（{{ resetTarget?.username }}）重置登录密码
        </p>
        <el-form :model="resetForm" label-width="80px">
          <el-form-item label="新密码" required>
            <el-input v-model="resetForm.password" placeholder="至少 6 位" show-password maxlength="50" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="resetVisible = false">取消</el-button>
          <el-button type="primary" @click="doReset" :loading="submitting">确认重置</el-button>
        </template>
      </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { listUsers, createUser, updateUser, resetPassword, listDepartments } from '../api'

// ================== 数据 ==================
const users = ref<any[]>([])
const departments = ref<any[]>([])
const loading = ref(false)
const submitting = ref(false)
const keyword = ref('')
const deptFilter = ref<number | null>(null)
const roleFilter = ref('')
const statusFilter = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)

// ================== 弹窗状态 ==================
const createVisible = ref(false)
const editVisible = ref(false)
const resetVisible = ref(false)
const editTarget = ref<any>(null)
const resetTarget = ref<any>(null)

const createForm = ref({ username: '', password: '', name: '', phone: '', departmentId: null as number | null, role: 'USER' })
const editForm = ref({ id: 0, username: '', name: '', phone: '', departmentId: null as number | null, role: '', status: '' })
const resetForm = ref({ password: '' })

// ================== 初始化 ==================
onMounted(async () => {
  try {
    const deptRes = await listDepartments()
    departments.value = flatten(deptRes.data?.data || [])
  } catch (e) {
    console.error('加载部门列表失败:', e)
  }
  load()
})

function flatten(nodes: any[]): any[] {
  let r: any[] = []
  for (const n of nodes) { r.push(n); if (n.children) r = r.concat(flatten(n.children)) }
  return r
}

// ================== 列表加载 ==================
async function load() {
  loading.value = true
  const params: Record<string, any> = { page: page.value, size: size.value }
  if (deptFilter.value) params.departmentId = deptFilter.value
  if (roleFilter.value) params.role = roleFilter.value
  if (statusFilter.value) params.status = statusFilter.value
  if (keyword.value) params.keyword = keyword.value

  const res = await listUsers(params)
  const data = res.data?.data
  users.value = data?.records || []
  total.value = data?.total || 0
  loading.value = false
}

// ================== 角色/状态标签 ==================
function roleLabel(role: string) {
  const map: Record<string, string> = { USER: '普通用户', MANAGER: '知识管理员', ADMIN: '系统管理员' }
  return map[role] || role
}
function roleTag(role: string) {
  const map: Record<string, string> = { USER: 'info', MANAGER: 'warning', ADMIN: 'danger' }
  return map[role] || 'info'
}
function deptName(id: number) {
  return departments.value.find((d: any) => d.id === id)?.name || '-'
}

/** 校验中国大陆手机号（11 位，1 开头，可选） */
function validPhone(phone: string): boolean {
  return /^1[3-9]\d{9}$/.test(phone)
}

/** 输入时仅保留数字 */
function filterPhoneInput(val: string) {
  return val.replace(/\D/g, '').slice(0, 11)
}

// ================== 新建用户 ==================
function openCreate() {
  createForm.value = { username: '', password: '', name: '', phone: '', departmentId: null, role: 'USER' }
  createVisible.value = true
}

async function doCreate() {
  const f = createForm.value
  if (!f.username.trim()) return ElMessage.warning('请输入用户名')
  if (f.username.trim().length < 3) return ElMessage.warning('用户名至少 3 个字符')
  if (!f.password || f.password.length < 6) return ElMessage.warning('密码至少 6 位')
  if (!f.phone.trim()) return ElMessage.warning('请输入手机号')
  if (!validPhone(f.phone.trim())) return ElMessage.warning('手机号格式不正确')

  submitting.value = true
  try {
    await createUser({
      username: f.username.trim(),
      password: f.password,
      name: f.name.trim() || f.username.trim(),
      phone: f.phone.trim(),
      departmentId: f.departmentId,
      role: f.role
    })
    ElMessage.success('用户创建成功')
    createVisible.value = false
    load()
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '创建失败')
  } finally {
    submitting.value = false
  }
}

// ================== 编辑用户 ==================
function openEdit(row: any) {
  editTarget.value = row
  editForm.value = {
    id: row.id,
    username: row.username,
    name: row.name,
    phone: row.phone || '',
    departmentId: row.departmentId,
    role: row.role,
    status: row.status
  }
  editVisible.value = true
}

async function doEdit() {
  const f = editForm.value
  if (!f.phone.trim()) return ElMessage.warning('请输入手机号')
  if (!validPhone(f.phone.trim())) return ElMessage.warning('手机号格式不正确')
  submitting.value = true
  try {
    await updateUser(f.id, {
      name: f.name,
      phone: f.phone,
      departmentId: f.departmentId,
      role: f.role,
      status: f.status
    })
    ElMessage.success('用户信息已更新')
    editVisible.value = false
    load()
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '更新失败')
  } finally {
    submitting.value = false
  }
}

// ================== 禁用/启用 ==================
async function toggleStatus(row: any) {
  const newStatus = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  const action = newStatus === 'DISABLED' ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(`确定${action}用户「${row.name}」吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  await updateUser(row.id, { status: newStatus })
  ElMessage.success(`已${action}用户 ${row.name}`)
  load()
}

// ================== 重置密码 ==================
function openResetPwd(row: any) {
  resetTarget.value = row
  resetForm.value = { password: '' }
  resetVisible.value = true
}

async function doReset() {
  if (!resetForm.value.password || resetForm.value.password.length < 6) {
    return ElMessage.warning('新密码至少 6 位')
  }
  submitting.value = true
  try {
    await resetPassword(resetTarget.value.id, resetForm.value.password)
    ElMessage.success(`已重置 ${resetTarget.value.name} 的密码`)
    resetVisible.value = false
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.user-page { background: #fff; border-radius: 6px; padding: 16px; }
.toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.toolbar-left { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
</style>
