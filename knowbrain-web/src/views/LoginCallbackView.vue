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
import { oidcExchange } from '../api'

const route = useRoute()
const router = useRouter()
const error = ref('')

onMounted(async () => {
  const code = route.query.code as string
  const errMsg = route.query.error as string

  if (errMsg) {
    error.value = errMsg
    return
  }

  if (!code) {
    error.value = '缺少登录凭证，请重新登录'
    return
  }

  try {
    // 用一次性 code 向服务器换取 JWT（token 不会出现在 URL 中）
    const res = await oidcExchange(code)
    const data = res.data?.data || res.data

    const token = data.token as string
    const refreshToken = data.refreshToken as string
    const name = (data.name as string) || '未知用户'

    // 从 JWT payload 中解析 role（服务器签名，不可伪造），而非信任任何 URL 参数
    let role = 'USER'
    let userId: number | null = null
    try {
      const payload = JSON.parse(atob(token.split('.')[1]))
      role = payload.role || 'USER'
      userId = payload.userId || null
    } catch {
      // 退路：使用服务器响应中的 role（通过安全 POST 返回，非 URL 参数）
      role = (data.role as string) || 'USER'
    }

    localStorage.setItem('kb_token', token)
    localStorage.setItem('kb_refreshToken', refreshToken || '')
    localStorage.setItem('kb_user', JSON.stringify({ userId, name, role }))

    // 按角色跳转
    if (role === 'USER') {
      router.replace('/')
    } else {
      router.replace('/admin/dashboard')
    }
  } catch (e: any) {
    error.value = e.response?.data?.message || '登录失败，请重试'
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
