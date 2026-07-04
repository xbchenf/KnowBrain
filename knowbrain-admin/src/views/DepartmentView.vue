<template>
  <div class="admin-layout">
    <el-header class="admin-header">
      <div class="header-left">
        <h2>KnowBrain 管理后台</h2>
      </div>
      <div class="header-right">
        <span class="user-name">{{ userName }}</span>
        <el-button text @click="logout">退出</el-button>
      </div>
    </el-header>

    <el-container>
      <el-aside width="220px" class="admin-sidebar">
        <el-menu :default-active="route.path" router>
          <el-menu-item index="/dashboard">
            <el-icon><HomeFilled /></el-icon> 工作台
          </el-menu-item>
          <el-menu-item v-if="isAdmin" index="/departments">
            <el-icon><OfficeBuilding /></el-icon> 部门管理
          </el-menu-item>
          <el-menu-item v-if="isAdmin" index="/users">
            <el-icon><User /></el-icon> 用户管理
          </el-menu-item>
        </el-menu>
      </el-aside>

      <el-main class="admin-main">
        <h3>部门管理</h3>
        <p style="color: #909399; margin-bottom: 16px;">管理组织部门结构</p>

        <el-table :data="departments" v-loading="loading" stripe style="width: 100%">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="name" label="部门名称" />
          <el-table-column prop="description" label="描述" />
          <el-table-column label="操作" width="200">
            <template #default="scope">
              <el-button size="small" text @click="edit(scope.row)">编辑</el-button>
              <el-button size="small" text type="danger" @click="remove(scope.row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { HomeFilled, OfficeBuilding, User } from '@element-plus/icons-vue'
import { listDepartments } from '../api'

const route = useRoute()
const router = useRouter()

const departments = ref<any[]>([])
const loading = ref(false)
const userName = ref('')
const isAdmin = ref(false)

onMounted(async () => {
  const u = localStorage.getItem('kb_user')
  if (u) {
    const parsed = JSON.parse(u)
    userName.value = parsed.name
    isAdmin.value = parsed.role === 'ADMIN'
  }
  await load()
})

async function load() {
  loading.value = true
  try {
    const res = await listDepartments()
    departments.value = res.data?.data || []
  } catch { departments.value = [] }
  finally { loading.value = false }
}

function edit(row: any) {
  // TODO: implement edit
}

function remove(row: any) {
  // TODO: implement remove
}

function logout() {
  localStorage.removeItem('kb_token')
  localStorage.removeItem('kb_user')
  router.push('/login')
}
</script>

<style scoped>
.admin-layout { min-height: 100vh; background: #f5f7fa; }
.admin-header {
  display: flex; align-items: center; justify-content: space-between;
  background: #fff; border-bottom: 1px solid #e4e7ed; padding: 0 24px; height: 56px;
}
.admin-header h2 { font-size: 18px; color: #303133; }
.header-right { display: flex; align-items: center; gap: 12px; }
.user-name { color: #606266; font-size: 14px; }
.admin-sidebar { background: #fff; border-right: 1px solid #e4e7ed; min-height: calc(100vh - 56px); }
.admin-main { padding: 24px; }
</style>
