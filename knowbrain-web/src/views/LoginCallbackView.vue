<template>
  <div class="callback-page">
    <div class="callback-card">
      <template v-if="error">
        <el-icon :size="48" color="#f56c6c"><CircleCloseFilled /></el-icon>
        <h2>登录失败</h2>
        <p class="msg">{{ error }}</p>
        <el-button type="primary" @click="goLogin">返回登录页</el-button>
      </template>
      <template v-else>
        <el-icon :size="48" class="loading-icon"><Loading /></el-icon>
        <h2>正在登录...</h2>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CircleCloseFilled, Loading } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const error = ref('')

onMounted(() => {
  const token = route.query.token as string
  const refreshToken = route.query.refreshToken as string
  const name = route.query.name as string
  const role = route.query.role as string
  const errMsg = route.query.error as string

  if (errMsg) {
    error.value = errMsg
    return
  }

  if (!token) {
    error.value = '缺少登录凭证，请重新登录'
    return
  }

  // 存储 token 到 localStorage（与现有登录逻辑一致）
  localStorage.setItem('kb_token', token)
  localStorage.setItem('kb_user', JSON.stringify({
    userId: null, name: name || '未知用户', role: role || 'USER'
  }))
  localStorage.setItem('kb_refreshToken', refreshToken || '')

  // 按角色跳转
  if (role === 'USER') {
    router.replace('/')
  } else {
    router.replace('/admin/dashboard')
  }
})

function goLogin() {
  router.replace('/login')
}
</script>

<style scoped>
.callback-page {
  display: flex; align-items: center; justify-content: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.callback-card {
  width: 360px; padding: 48px 36px; background: #fff;
  border-radius: 16px; box-shadow: 0 20px 60px rgba(0,0,0,0.12);
  text-align: center;
}
.callback-card h2 { font-size: 20px; margin: 16px 0 8px; color: #303133; }
.callback-card .msg { color: #909399; margin-bottom: 20px; font-size: 14px; }
.loading-icon { animation: spin 1s linear infinite; color: #409eff; }
@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
</style>
