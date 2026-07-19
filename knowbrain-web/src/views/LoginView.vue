<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-header">
        <h1>KnowBrain</h1>
        <p>企业知识大脑 · 智能知识库</p>
      </div>

      <el-tabs v-model="mode" class="login-tabs">
        <el-tab-pane label="登录" name="login" />
        <el-tab-pane label="注册" name="register" />
      </el-tabs>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top"
               hide-required-asterisk size="large" class="login-form">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名"
                    :prefix-icon="UserIcon" />
        </el-form-item>

        <el-form-item v-if="mode === 'register'" label="昵称" prop="name">
          <el-input v-model="form.name" placeholder="请输入显示名称" />
        </el-form-item>

        <el-form-item v-if="mode === 'register'" label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" maxlength="11" />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password
                    placeholder="请输入密码" :prefix-icon="LockIcon"
                    @keydown.enter="submit" />
        </el-form-item>

        <el-form-item class="submit-item">
          <el-button type="primary" :loading="loading" class="submit-btn"
                     @click="submit">
            {{ mode === 'login' ? '登 录' : '注 册' }}
          </el-button>
        </el-form-item>
      </el-form>

      <!-- OIDC 企业登录 -->
      <div v-if="providers.length > 0" class="oidc-section">
        <div class="oidc-divider">
          <span class="oidc-divider-text">其他登录方式</span>
        </div>
        <div class="oidc-providers">
          <el-button v-for="p in providers" :key="p.id"
                     class="oidc-btn" @click="oidcLogin(p.url)">
            {{ p.name }}
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { login, register, getOidcProviders } from '../api'

const router = useRouter()
const UserIcon = User
const LockIcon = Lock
const mode = ref('login')
const loading = ref(false)
const providers = ref<Array<{id: string, name: string, url: string}>>([])

const form = reactive({ username: '', password: '', name: '', phone: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, min: 6, message: '密码至少6位', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' },
          { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }]
}

onMounted(async () => {
  try {
    const res = await getOidcProviders()
    providers.value = res.data?.data || res.data || []
  } catch {
    // 获取 OIDC 提供商失败，忽略（可能是后端未启用）
  }
})

async function submit() {
  loading.value = true
  try {
    const api = mode.value === 'login'
      ? login(form.username, form.password)
      : register(form.username, form.password, form.name || form.username, form.phone)

    const res = await api
    const data = res.data?.data || res.data

    localStorage.setItem('kb_token', data.token)
    localStorage.setItem('kb_user', JSON.stringify({
      userId: data.userId, name: data.name, role: data.role
    }))

    ElMessage.success(mode.value === 'login' ? '登录成功' : '注册成功')
    // 按角色跳转：普通用户 → 问答页，管理员 → 管理后台
    if (data.role === 'USER') {
      router.push('/')
    } else {
      router.push('/admin/dashboard')
    }
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '操作失败')
  } finally {
    loading.value = false
  }
}

function oidcLogin(url: string) {
  window.location.href = url
}
</script>

<style scoped>
.login-page {
  display: flex; align-items: center; justify-content: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-card {
  width: 400px; padding: 40px 36px; background: #fff;
  border-radius: 16px; box-shadow: 0 20px 60px rgba(0,0,0,0.12);
}
.login-header { text-align: center; margin-bottom: 28px; }
.login-header h1 { font-size: 26px; font-weight: 700; color: #303133; letter-spacing: -0.5px; }
.login-header p { color: #909399; margin-top: 6px; font-size: 13px; }

.login-tabs { margin-bottom: 4px; }

/* 表单微调 */
.login-form :deep(.el-form-item__label) {
  font-size: 13px; font-weight: 500; color: #606266;
  padding-bottom: 2px;
}
.login-form :deep(.el-input__wrapper) {
  border-radius: 8px; box-shadow: 0 0 0 1px #dcdfe6 inset;
  transition: box-shadow .2s;
}
.login-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #c0c4cc inset;
}
.login-form :deep(.el-input.is-focus .el-input__wrapper) {
  box-shadow: 0 0 0 2px rgba(64,158,255,0.2), 0 0 0 1px #409eff inset;
}
.login-form :deep(.el-input__prefix) {
  color: #a8abb2;
}

.submit-item { margin-top: 8px; }
.submit-btn {
  width: 100%; height: 44px; font-size: 15px; font-weight: 600;
  border-radius: 8px; letter-spacing: 2px;
}

/* OIDC 企业登录 */
.oidc-section { margin-top: 20px; }
.oidc-divider {
  display: flex; align-items: center;
  margin-bottom: 12px;
}
.oidc-divider::before, .oidc-divider::after {
  content: ''; flex: 1;
  border-top: 1px solid #e4e7ed;
}
.oidc-divider-text {
  padding: 0 12px; font-size: 12px; color: #b0b3bb;
}
.oidc-providers {
  display: flex; justify-content: center; gap: 12px;
}
.oidc-btn {
  border-radius: 8px; min-width: 100px;
}
</style>
