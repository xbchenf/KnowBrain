<template>
  <div class="admin-layout">
    <el-header class="admin-header">
      <div class="header-left">
        <el-button text @click="$router.push('/dashboard')">
          <el-icon><ArrowLeft /></el-icon> 返回
        </el-button>
        <h2>用户管理</h2>
      </div>
    </el-header>
    <el-main>
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="搜索用户名/姓名" clearable style="width:200px" @change="load" />
        <el-select v-model="deptFilter" clearable placeholder="按部门过滤" @change="load" style="width:160px">
          <el-option v-for="d in departments" :key="d.id" :label="d.name" :value="d.id" />
        </el-select>
      </div>
      <el-table :data="users" v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="name" label="姓名" width="100" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column label="角色" width="140">
          <template #default="{ row }">
            <el-select v-model="row.role" @change="(v: string) => updateRole(row, v)" size="small">
              <el-option label="普通用户" value="USER" />
              <el-option label="知识管理员" value="MANAGER" />
              <el-option label="系统管理员" value="ADMIN" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="部门" width="120">
          <template #default="{ row }">{{ deptName(row.departmentId) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80" />
        <el-table-column prop="createTime" label="注册时间" min-width="160">
          <template #default="{ row }">{{ row.createTime?.substring(0, 16) }}</template>
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
    </el-main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { listUsers, updateUser, listDepartments } from '../api'

const users = ref<any[]>([])
const departments = ref<any[]>([])
const loading = ref(false)
const keyword = ref('')
const deptFilter = ref<number | null>(null)
const page = ref(1)
const size = ref(20)
const total = ref(0)

onMounted(async () => {
  const deptRes = await listDepartments()
  departments.value = flatten(deptRes.data?.data || [])
  load()
})

function flatten(nodes: any[]): any[] {
  let r: any[] = []
  for (const n of nodes) { r.push(n); if (n.children) r = r.concat(flatten(n.children)) }
  return r
}

async function load() {
  loading.value = true
  const res = await listUsers({ page: page.value, size: size.value, departmentId: deptFilter.value, keyword: keyword.value })
  const data = res.data?.data
  users.value = data?.records || []
  total.value = data?.total || 0
  loading.value = false
}

async function updateRole(row: any, role: string) {
  await updateUser(row.id, { role })
  ElMessage.success(`已将 ${row.name} 角色更新为 ${role}`)
}

function deptName(id: number) {
  return departments.value.find((d: any) => d.id === id)?.name || '-'
}
</script>
