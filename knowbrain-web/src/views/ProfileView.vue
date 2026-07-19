<template>
  <div class="profile-page">
    <h2>个人设置</h2>

    <!-- 基本信息 -->
    <el-card class="section-card">
      <template #header><span>基本信息</span></template>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="用户名">{{ profile.username }}</el-descriptions-item>
        <el-descriptions-item label="角色">
          <el-tag :type="profile.role === 'ADMIN' ? 'danger' : profile.role === 'MANAGER' ? 'warning' : 'info'">
            {{ profile.role }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="注册时间">{{ profile.createTime }}</el-descriptions-item>
      </el-descriptions>

      <el-form :model="infoForm" :rules="infoRules" ref="infoFormRef" label-position="top"
               style="margin-top: 20px">
        <el-form-item label="显示名称" prop="name">
          <el-input v-model="infoForm.name" placeholder="请输入显示名称" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="infoForm.phone" placeholder="请输入手机号" maxlength="11" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="saveInfo">保存修改</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 关联的第三方账号 -->
    <el-card class="section-card">
      <template #header><span>关联的第三方账号</span></template>
      <div v-if="identities.length === 0" style="color: #909399; padding: 16px 0">
        暂未关联第三方账号
      </div>
      <el-table v-else :data="identities" style="width: 100%">
        <el-table-column label="平台" width="100">
          <template #default="{ row }">
            <el-tag :type="row.platform === 'feishu' ? 'success' : 'primary'" size="small">
              {{ row.platform === 'feishu' ? '飞书' : row.platform === 'dingtalk' ? '钉钉' : row.platform }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="platformName" label="平台昵称" />
        <el-table-column prop="linkedAt" label="关联时间" width="180" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-popconfirm title="确定要解除关联吗？" @confirm="unlink(row.id)">
              <template #reference>
                <el-button type="danger" link size="small">解绑</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div class="link-buttons" style="margin-top: 16px">
        <el-button v-if="!hasFeishu" @click="linkAccount('feishu')">
          <el-icon style="margin-right: 4px"><Link /></el-icon>关联飞书账号
        </el-button>
        <el-button v-if="!hasDingtalk" @click="linkAccount('dingtalk')">
          <el-icon style="margin-right: 4px"><Link /></el-icon>关联钉钉账号
        </el-button>
        <span v-if="hasFeishu && hasDingtalk" style="color: #909399; font-size: 13px">
          已关联全部可用平台
        </span>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Link } from '@element-plus/icons-vue'
import { getProfile, updateProfile, listMyIdentities, unbindMyIdentity, prepareLink } from '../api'

const route = useRoute()
const profile = ref<any>({})
const identities = ref<any[]>([])
const infoForm = reactive({ name: '', phone: '' })
const infoFormRef = ref()
const saving = ref(false)

const infoRules = {
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ]
}

const hasFeishu = computed(() => identities.value.some(i => i.platform === 'feishu'))
const hasDingtalk = computed(() => identities.value.some(i => i.platform === 'dingtalk'))

onMounted(async () => {
  await loadProfile()
  await loadIdentities()

  // 关联成功回调
  const linked = route.query.linked as string
  if (linked) {
    const name = linked === 'feishu' ? '飞书' : linked === 'dingtalk' ? '钉钉' : linked
    ElMessage.success(`已成功关联${name}账号`)
    // 清除 query 参数
    window.history.replaceState({}, '', '/settings')
  }
  const linkError = route.query.link_error as string
  if (linkError) {
    ElMessage.error('账号关联失败，请重试')
    window.history.replaceState({}, '', '/settings')
  }
})

async function loadProfile() {
  try {
    const res = await getProfile()
    profile.value = res.data.data
    infoForm.name = profile.value.name || ''
    infoForm.phone = profile.value.phone || ''
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '加载个人资料失败')
  }
}

async function loadIdentities() {
  try {
    const res = await listMyIdentities()
    identities.value = res.data.data || []
  } catch { /* ignore */ }
}

async function saveInfo() {
  if (!infoFormRef.value) return
  const valid = await infoFormRef.value.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    await updateProfile({ name: infoForm.name, phone: infoForm.phone })
    ElMessage.success('修改成功')
    await loadProfile()
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '修改失败')
  } finally {
    saving.value = false
  }
}

async function linkAccount(platform: string) {
  try {
    await prepareLink(platform)
    window.location.href = '/oauth2/authorization/' + platform
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '操作失败')
  }
}

async function unlink(id: number) {
  try {
    await unbindMyIdentity(id)
    ElMessage.success('已解除关联')
    await loadIdentities()
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '解绑失败')
  }
}
</script>

<style scoped>
.profile-page {
  max-width: 720px;
  margin: 0 auto;
  padding: 24px;
}
.profile-page h2 {
  margin: 0 0 20px 0;
  font-size: 20px;
}
.section-card {
  margin-bottom: 20px;
}
</style>
