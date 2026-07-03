<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-header">
        <h1>KnowBrain</h1>
        <p>企业知识大脑 · 管理后台</p>
      </div>

      <el-tabs v-model="mode" class="login-tabs">
        <el-tab-pane label="登录" name="login" />
        <el-tab-pane label="注册" name="register" />
      </el-tabs>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>

        <el-form-item v-if="mode === 'register'" label="昵称" prop="name">
          <el-input v-model="form.name" placeholder="请输入显示名称" />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password
                    placeholder="请输入密码" @keydown.enter="submit" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="loading" style="width:100%"
                     @click="submit">
            {{ mode === 'login' ? '登 录' : '注 册' }}
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login, register } from '../api'

const router = useRouter()
const mode = ref('login')
const loading = ref(false)

const form = reactive({ username: '', password: '', name: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, min: 6, message: '密码至少6位', trigger: 'blur' }]
}

async function submit() {
  loading.value = true
  try {
    const api = mode.value === 'login'
      ? login(form.username, form.password)
      : register(form.username, form.password, form.name || form.username)

    const res = await api
    const data = res.data?.data || res.data

    localStorage.setItem('kb_token', data.token)
    localStorage.setItem('kb_user', JSON.stringify({
      userId: data.userId, name: data.name, role: data.role
    }))

    ElMessage.success(mode.value === 'login' ? '登录成功' : '注册成功')
    router.push('/dashboard')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '操作失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: flex; align-items: center; justify-content: center;
  min-height: 100vh; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-card {
  width: 420px; padding: 40px; background: #fff; border-radius: 12px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.15);
}
.login-header { text-align: center; margin-bottom: 24px; }
.login-header h1 { font-size: 28px; color: #409eff; }
.login-header p { color: #909399; margin-top: 6px; font-size: 14px; }
.login-tabs { margin-bottom: 8px; }
</style>
