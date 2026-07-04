<template>
  <div>
    <!-- 操作栏 -->
    <div class="toolbar">
      <h3>我的空间</h3>
      <el-button type="primary" @click="showCreate = true">
        <el-icon><Plus /></el-icon> 创建空间
      </el-button>
    </div>

    <!-- 空间卡片列表 -->
    <el-row :gutter="20" v-loading="loading">
      <el-col v-for="s in spaces" :key="s.id" :xs="24" :sm="12" :md="8" :lg="6">
        <el-card class="space-card" shadow="hover" @click="goSpace(s.id)">
          <div class="space-card-header">
            <el-icon :size="24"><Folder /></el-icon>
            <el-tag :type="visibilityType(s.visibility)" size="small">
              {{ visibilityLabel(s.visibility) }}
            </el-tag>
          </div>
          <h4>{{ s.name }}</h4>
          <p class="space-desc">{{ s.description || '暂无描述' }}</p>
          <div class="space-meta">
            <span>{{ s.createTime?.substring(0, 10) }}</span>
          </div>
        </el-card>
      </el-col>

      <!-- 空状态 -->
      <el-col :span="24" v-if="!loading && spaces.length === 0">
        <el-empty description="暂无空间，点击右上角创建第一个空间">
          <el-button type="primary" @click="showCreate = true">创建空间</el-button>
        </el-empty>
      </el-col>
    </el-row>

    <!-- 创建空间弹窗 -->
    <el-dialog v-model="showCreate" title="创建空间" width="480px">
      <el-form :model="createForm" label-position="top">
        <el-form-item label="空间名称" required>
          <el-input v-model="createForm.name" placeholder="例如：技术文档库" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="createForm.description" type="textarea" :rows="2"
                    placeholder="空间简介" />
        </el-form-item>
        <el-form-item label="可见性">
          <el-radio-group v-model="createForm.visibility">
            <el-radio value="PRIVATE">私有（仅自己）</el-radio>
            <el-radio value="TEAM">团队（成员可见）</el-radio>
            <el-radio value="PUBLIC">公开（所有人）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="createForm.visibility === 'TEAM'" label="可见部门">
          <el-select v-model="createForm.departmentScope" multiple placeholder="选择可见部门" style="width:100%">
            <el-option v-for="d in allDepartments" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="doCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Folder } from '@element-plus/icons-vue'
import { listSpaces, createSpace, listDepartments } from '../api'

const router = useRouter()

const loading = ref(false)
const spaces = ref<any[]>([])
const showCreate = ref(false)
const creating = ref(false)
const createForm = reactive({ name: '', description: '', visibility: 'PRIVATE', departmentScope: [] as number[] })
const allDepartments = ref<any[]>([])

onMounted(async () => {
  await loadSpaces()
  const deptRes = await listDepartments()
  allDepartments.value = flatten(deptRes.data?.data || [])
})

async function loadSpaces() {
  loading.value = true
  try {
    const res = await listSpaces()
    spaces.value = res.data?.data?.records || res.data?.data || []
  } catch { spaces.value = [] }
  finally { loading.value = false }
}

async function doCreate() {
  if (!createForm.name.trim()) return
  creating.value = true
  try {
    await createSpace(createForm.name, createForm.description, createForm.visibility, createForm.departmentScope)
    ElMessage.success('空间创建成功')
    showCreate.value = false
    createForm.name = ''
    createForm.description = ''
    createForm.visibility = 'PRIVATE'
    createForm.departmentScope = []
    await loadSpaces()
  } catch { ElMessage.error('创建失败') }
  finally { creating.value = false }
}

function goSpace(id: number) {
  router.push(`/spaces/${id}`)
}

function flatten(nodes: any[]): any[] {
  let r: any[] = []
  for (const n of nodes) { r.push(n); if (n.children) r = r.concat(flatten(n.children)) }
  return r
}

function visibilityType(v: string) {
  return v === 'PUBLIC' ? 'success' : v === 'TEAM' ? 'warning' : 'info'
}
function visibilityLabel(v: string) {
  return v === 'PUBLIC' ? '公开' : v === 'TEAM' ? '团队' : '私有'
}
</script>

<style scoped>
.toolbar {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 24px;
}
.toolbar h3 { font-size: 18px; color: #303133; }
.space-card {
  margin-bottom: 20px; cursor: pointer; transition: transform .2s;
}
.space-card:hover { transform: translateY(-2px); }
.space-card-header {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 12px;
}
.space-card h4 { font-size: 16px; color: #303133; margin-bottom: 6px; }
.space-desc { font-size: 13px; color: #909399; margin-bottom: 12px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.space-meta { font-size: 12px; color: #c0c4cc; }
</style>
