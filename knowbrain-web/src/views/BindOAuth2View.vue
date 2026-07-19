<template>
  <div class="bind-page">
    <div class="bind-card">
      <div class="bind-header">
        <h1>完善账号信息</h1>
        <p>请绑定手机号以完成登录</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="显示名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入显示名称" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="submit" style="width: 100%">
            完成绑定
          </el-button>
        </el-form-item>
      </el-form>

      <div v-if="error" class="bind-error">
        <el-icon :size="20"><CircleCloseFilled /></el-icon>
        <p>{{ error }}</p>
        <el-button type="primary" @click="$router.push('/login')" style="margin-top: 12px">
          返回登录页
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CircleCloseFilled } from '@element-plus/icons-vue'
import { bindOAuth2 } from '../api'

const route = useRoute()
const router = useRouter()
const formRef = ref()
const loading = ref(false)
const error = ref('')

const form = reactive({
  phone: '',
  name: ''
})

const rules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  name: [
    { required: true, message: '请输入显示名称', trigger: 'blur' }
  ]
}

onMounted(() => {
  const code = route.query.code as string
  if (!code) {
    error.value = '缺少绑定凭证，请重新登录'
  }
})

async function submit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  const code = route.query.code as string
  if (!code) {
    error.value = '缺少绑定凭证'
    return
  }

  loading.value = true
  error.value = ''
  try {
    const res = await bindOAuth2(code, form.phone, form.name)
    const data = res.data.data
    localStorage.setItem('kb_token', data.token)
    localStorage.setItem('kb_refresh_token', data.refreshToken)
    // 从 JWT payload 提取 userId 和 role（与 LoginCallbackView 一致）
    let userId = data.userId, role = data.role
    try {
      const payload = JSON.parse(atob(data.token.split('.')[1]))
      userId = payload.userId || userId
      role = payload.role || role
    } catch { /* fallback */ }
    localStorage.setItem('kb_user', JSON.stringify({ userId, name: data.name, role }))
    ElMessage.success('绑定成功')
    if (role === 'ADMIN' || role === 'MANAGER') {
      router.push('/admin/dashboard')
    } else {
      router.push('/')
    }
  } catch (err: any) {
    error.value = err?.response?.data?.message || '绑定失败，请重试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.bind-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.bind-card {
  background: #fff;
  border-radius: 12px;
  padding: 40px;
  width: 400px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
}
.bind-header {
  text-align: center;
  margin-bottom: 24px;
}
.bind-header h1 {
  font-size: 22px;
  color: #303133;
  margin: 0 0 8px 0;
}
.bind-header p {
  color: #909399;
  font-size: 14px;
  margin: 0;
}
.bind-error {
  text-align: center;
  color: #f56c6c;
  margin-top: 16px;
}
.bind-error p {
  margin: 8px 0;
}
</style>
