<template>
  <div class="im-page">
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="部门映射" name="mappings" />
      <el-tab-pane label="IM 身份" name="identities" />
    </el-tabs>

    <!-- ==================== 部门映射 ==================== -->
    <template v-if="activeTab === 'mappings'">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-select v-model="mappingPlatformFilter" clearable placeholder="按平台筛选" @change="loadMappings" style="width:140px">
            <el-option label="企微" value="wecom" />
            <el-option label="钉钉" value="dingtalk" />
            <el-option label="飞书" value="feishu" />
          </el-select>
        </div>
        <div class="toolbar-right">
          <el-button @click="openFetchDeptDialog">
            <el-icon><Search /></el-icon> 拉取平台部门
          </el-button>
          <el-button type="primary" @click="openMappingDialog(null)">
            <el-icon><Plus /></el-icon> 新增映射
          </el-button>
        </div>
      </div>

      <el-table :data="mappings" v-loading="mappingLoading" stripe>
        <el-table-column type="index" label="#" width="50" />
        <el-table-column label="IM 平台" width="90">
          <template #default="{ row }">
            <el-tag :type="platformTag(row.platform)" size="small">{{ platformLabel(row.platform) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="externalDeptId" label="IM 部门 ID" width="120" />
        <el-table-column label="KB 部门" min-width="160">
          <template #default="{ row }">{{ row.kbDeptName }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160">
          <template #default="{ row }">{{ row.createTime?.substring(0, 16) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openMappingDialog(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="confirmDeleteMapping(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="mappingPage" :page-size="mappingSize" :total="mappingTotal"
        layout="prev, pager, next, total" @current-change="loadMappings"
        style="margin-top:16px;justify-content:center"
      />

      <!-- 映射弹窗 -->
      <el-dialog v-model="mappingDialog" :title="editingMapping ? '编辑映射' : '新增映射'" width="440px" :close-on-click-modal="false">
        <el-form :model="mappingForm" label-position="top">
          <el-form-item label="IM 平台" required>
            <el-select v-model="mappingForm.platform" placeholder="选择平台" style="width:100%">
              <el-option label="企业微信" value="wecom" />
              <el-option label="钉钉" value="dingtalk" />
              <el-option label="飞书" value="feishu" />
            </el-select>
          </el-form-item>
          <el-form-item label="IM 部门 ID" required>
            <el-input v-model="mappingForm.externalDeptId" placeholder="企微/钉钉/飞书侧部门ID" />
          </el-form-item>
          <el-form-item label="KB 部门" required>
            <el-select v-model="mappingForm.kbDeptId" placeholder="选择 KB 内部部门" style="width:100%">
              <el-option v-for="d in kbDepartments" :key="d.id" :label="d.name" :value="d.id" />
            </el-select>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="mappingDialog = false">取消</el-button>
          <el-button type="primary" :loading="mappingSaving" @click="saveMapping">
            {{ editingMapping ? '保存' : '创建' }}
          </el-button>
        </template>
      </el-dialog>

      <!-- 拉取平台部门弹窗 -->
      <el-dialog v-model="fetchDeptDialog" title="拉取平台部门" width="640px" :close-on-click-modal="false">
        <div class="fetch-dept-toolbar">
          <el-select v-model="fetchPlatform" placeholder="选择平台" style="width:160px">
            <el-option label="企业微信" value="wecom" />
            <el-option label="钉钉" value="dingtalk" />
            <el-option label="飞书" value="feishu" />
          </el-select>
          <el-button type="primary" :loading="fetchDeptLoading" @click="doFetchPlatformDepts">
            查询
          </el-button>
          <span v-if="platformDeptList.length" class="fetch-summary">
            共 {{ platformDeptList.length }} 个部门，已映射 {{ mappedCount }} 个，未映射 {{ unmappedCount }} 个
          </span>
        </div>

        <el-table :data="platformDeptList" v-loading="fetchDeptLoading" stripe max-height="400" style="margin-top:12px">
          <template #empty>
            <div v-if="fetchDeptLoaded && !fetchDeptLoading" style="padding:24px;color:#909399">
              <p v-if="fetchDeptError">
                <el-icon style="vertical-align:middle"><WarningFilled /></el-icon>
                {{ fetchDeptError }}
              </p>
              <p v-else>未获取到部门数据。<br/>请确认 IM 平台配置（环境变量）是否正确设置。</p>
            </div>
            <span v-else>—</span>
          </template>
          <el-table-column prop="id" label="平台部门 ID" width="110" />
          <el-table-column prop="name" label="部门名称" min-width="140" show-overflow-tooltip />
          <el-table-column label="映射状态" width="140">
            <template #default="{ row }">
              <el-tag v-if="row._mapped" type="success" size="small">已映射</el-tag>
              <el-tag v-else type="info" size="small">未映射</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="KB 部门" min-width="130">
            <template #default="{ row }">
              <span v-if="row._mapped" class="text-muted">{{ row._mappedKbDeptName }}</span>
              <span v-else class="text-muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button v-if="!row._mapped" link type="primary" size="small" @click="quickMap(row)">快速映射</el-button>
              <el-button v-else link type="primary" size="small" @click="editMapped(row)">修改</el-button>
            </template>
          </el-table-column>
        </el-table>

        <template #footer>
          <el-button @click="fetchDeptDialog = false">关闭</el-button>
        </template>
      </el-dialog>
    </template>

    <!-- ==================== IM 身份 ==================== -->
    <template v-if="activeTab === 'identities'">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-select v-model="identityPlatformFilter" clearable placeholder="按平台筛选" @change="loadIdentities" style="width:140px">
            <el-option label="企微" value="wecom" />
            <el-option label="钉钉" value="dingtalk" />
            <el-option label="飞书" value="feishu" />
          </el-select>
          <el-input v-model="identityKeyword" placeholder="搜索用户名/姓名/IM用户ID/手机号" clearable style="width:260px" @change="loadIdentities" />
        </div>
        <div class="toolbar-right">
          <el-button :type="unboundOnly ? 'warning' : 'default'" @click="toggleUnboundOnly">
            <el-icon><WarningFilled v-if="unboundOnly" /><User v-else /></el-icon>
            {{ unboundOnly ? '查看全部' : '查看未绑定用户' }}
          </el-button>
          <el-button type="primary" @click="openBindDialog">
            <el-icon><Plus /></el-icon> 绑定用户
          </el-button>
        </div>
      </div>

      <el-table :data="identities" v-loading="identityLoading" stripe>
        <el-table-column type="index" label="#" width="50" />
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag v-if="isAutoCreated(row)" type="warning" size="small">自动创建</el-tag>
            <el-tag v-else type="success" size="small">已绑定</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="kbName" label="KB 用户" width="100">
          <template #default="{ row }">
            <span v-if="row.kbName">{{ row.kbName }}</span>
            <span v-else class="text-muted">{{ row.kbUsername }}</span>
          </template>
        </el-table-column>
        <el-table-column label="IM 平台" width="90">
          <template #default="{ row }">
            <el-tag :type="platformTag(row.platform)" size="small">{{ platformLabel(row.platform) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="platformUid" label="IM 用户 ID" width="160" show-overflow-tooltip />
        <el-table-column prop="platformName" label="平台显示名" width="110" />
        <el-table-column prop="mobile" label="手机号" width="130" />
        <el-table-column prop="linkedAt" label="关联时间" min-width="150">
          <template #default="{ row }">{{ row.linkedAt?.substring(0, 16) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-if="isAutoCreated(row)" link type="primary" size="small" @click="rebindAutoUser(row)">绑定到正式用户</el-button>
            <el-button v-else link type="danger" size="small" @click="confirmUnbind(row)">解绑</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="identityPage" :page-size="identitySize" :total="identityTotal"
        layout="prev, pager, next, total" @current-change="loadIdentities"
        style="margin-top:16px;justify-content:center"
      />

      <!-- 绑定弹窗 -->
      <el-dialog v-model="bindDialog" title="绑定 IM 身份" width="440px" :close-on-click-modal="false">
        <el-form :model="bindForm" label-position="top">
          <el-form-item label="KB 用户" required>
            <el-select
              v-model="bindForm.kbUserId"
              filterable
              remote
              :remote-method="searchUsers"
              :loading="userSearchLoading"
              placeholder="搜索用户名或姓名"
              style="width:100%"
              clearable
            >
              <el-option
                v-for="u in userSearchResults"
                :key="u.id"
                :label="`${u.name || u.username} (ID:${u.id}, ${u.username})`"
                :value="u.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="IM 平台" required>
            <el-select v-model="bindForm.platform" placeholder="选择平台" style="width:100%">
              <el-option label="企业微信" value="wecom" />
              <el-option label="钉钉" value="dingtalk" />
              <el-option label="飞书" value="feishu" />
            </el-select>
          </el-form-item>
          <el-form-item label="IM 用户 ID" required>
            <el-input v-model="bindForm.platformUid" placeholder="平台侧用户唯一标识" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="bindDialog = false">取消</el-button>
          <el-button type="primary" :loading="bindSaving" @click="doBind">绑定</el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, User, WarningFilled } from '@element-plus/icons-vue'
import {
  listImDeptMappings, createImDeptMapping, updateImDeptMapping, deleteImDeptMapping,
  listImIdentities, bindImIdentity, unbindImIdentity,
  getImDepartmentsInfo, listUsers
} from '../api'

// ==================== 共用 ====================
const activeTab = ref('mappings')

const platformTag = (p: string) => p === 'wecom' ? 'success' : p === 'dingtalk' ? '' : 'info'
const platformLabel = (p: string) => p === 'wecom' ? '企微' : p === 'dingtalk' ? '钉钉' : p === 'feishu' ? '飞书' : p

interface Dept {
  id: number
  name: string
}

const kbDepartments = ref<Dept[]>([])

async function loadDeptInfo() {
  try {
    const res = await getImDepartmentsInfo()
    kbDepartments.value = res.data.data?.kbDepartments || []
  } catch { /* ignore */ }
}

// ==================== 拉取平台部门 ====================
const fetchDeptDialog = ref(false)
const fetchPlatform = ref('wecom')
const fetchDeptLoading = ref(false)
const fetchDeptLoaded = ref(false)
const fetchDeptError = ref('')
const platformDeptList = ref<any[]>([])
const platformMappedIds = ref<Set<string>>(new Set())

const mappedCount = computed(() => platformDeptList.value.filter(d => d._mapped).length)
const unmappedCount = computed(() => platformDeptList.value.filter(d => !d._mapped).length)

function openFetchDeptDialog() {
  fetchPlatform.value = 'wecom'
  platformDeptList.value = []
  platformMappedIds.value = new Set()
  fetchDeptLoaded.value = false
  fetchDeptError.value = ''
  fetchDeptDialog.value = true
}

async function doFetchPlatformDepts() {
  fetchDeptLoading.value = true
  try {
    const res = await getImDepartmentsInfo(fetchPlatform.value)
    const data = res.data.data
    const depts = data?.platformDepartments || []
    const mappedIds: Set<string> = new Set(data?.mappedExternalIds || [])
    platformMappedIds.value = mappedIds

    // 查询现有映射关系
    const mappingsRes = await listImDeptMappings({ platform: fetchPlatform.value, page: 1, size: 200 })
    const mappings = mappingsRes.data.data?.records || []
    const extIdToMapping: Record<string, { id: number; kbDeptId: number; kbDeptName: string }> = {}
    for (const m of mappings) {
      extIdToMapping[m.externalDeptId] = { id: m.id, kbDeptId: m.kbDeptId, kbDeptName: m.kbDeptName }
    }

    platformDeptList.value = depts.map((d: any) => {
      const m = extIdToMapping[String(d.id)]
      return {
        ...d,
        _mapped: !!m,
        _mappingId: m?.id || null,
        _mappedKbDeptId: m?.kbDeptId || null,
        _mappedKbDeptName: m?.kbDeptName || ''
      }
    })
  } catch (err: any) {
    fetchDeptError.value = err?.response?.data?.message || '拉取平台部门失败，请检查 IM 平台配置是否正确'
    ElMessage.error(fetchDeptError.value)
  } finally {
    fetchDeptLoading.value = false
    fetchDeptLoaded.value = true
  }
}

function quickMap(row: any) {
  // 保持拉取弹窗打开，在上面叠加映射弹窗
  openMappingDialog(null)
  mappingForm.platform = fetchPlatform.value
  mappingForm.externalDeptId = String(row.id)
}

function editMapped(row: any) {
  openMappingDialog({
    id: row._mappingId,
    platform: fetchPlatform.value,
    externalDeptId: String(row.id),
    kbDeptId: row._mappedKbDeptId,
  })
}

// ==================== 部门映射 ====================
const mappings = ref<any[]>([])
const mappingLoading = ref(false)
const mappingPage = ref(1)
const mappingSize = ref(20)
const mappingTotal = ref(0)
const mappingPlatformFilter = ref('')

const mappingDialog = ref(false)
const mappingSaving = ref(false)
const editingMapping = ref<any>(null)
const mappingForm = reactive({ platform: 'wecom', externalDeptId: '', kbDeptId: null as number | null })

async function loadMappings() {
  mappingLoading.value = true
  try {
    const res = await listImDeptMappings({
      platform: mappingPlatformFilter.value || undefined,
      page: mappingPage.value,
      size: mappingSize.value
    })
    const data = res.data.data
    mappings.value = data?.records || []
    mappingTotal.value = data?.total || 0
  } finally { mappingLoading.value = false }
}

function openMappingDialog(row: any) {
  editingMapping.value = row
  if (row) {
    mappingForm.platform = row.platform
    mappingForm.externalDeptId = row.externalDeptId
    mappingForm.kbDeptId = row.kbDeptId
  } else {
    mappingForm.platform = 'wecom'
    mappingForm.externalDeptId = ''
    mappingForm.kbDeptId = null
  }
  mappingDialog.value = true
}

async function saveMapping() {
  if (!mappingForm.platform || !mappingForm.externalDeptId || !mappingForm.kbDeptId) {
    ElMessage.warning('请填写完整信息')
    return
  }
  mappingSaving.value = true
  try {
    if (editingMapping.value) {
      await updateImDeptMapping(editingMapping.value.id, mappingForm)
      ElMessage.success('更新成功')
    } else {
      await createImDeptMapping(mappingForm)
      ElMessage.success('创建成功')
    }
    mappingDialog.value = false
    await loadMappings()
    // 如果拉取弹窗还开着，刷新平台部门列表的映射状态
    if (fetchDeptDialog.value) {
      await doFetchPlatformDepts()
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '操作失败')
  } finally { mappingSaving.value = false }
}

async function confirmDeleteMapping(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除此映射？`, '确认删除', { type: 'warning' })
  } catch { return }
  await deleteImDeptMapping(row.id)
  ElMessage.success('已删除')
  await loadMappings()
}

// ==================== IM 身份 ====================
const identities = ref<any[]>([])
const identityLoading = ref(false)
const identityPage = ref(1)
const identitySize = ref(20)
const identityTotal = ref(0)
const identityPlatformFilter = ref('')
const identityKeyword = ref('')
const unboundOnly = ref(false)

function isAutoCreated(row: any) {
  return row.kbUsername && row.kbUsername.startsWith('im_')
}

function toggleUnboundOnly() {
  unboundOnly.value = !unboundOnly.value
  loadIdentities()
}

const bindDialog = ref(false)
const bindSaving = ref(false)
const bindForm = reactive({ kbUserId: null as number | null, platform: 'wecom', platformUid: '' })

// 用户搜索（远程下拉）
const userSearchResults = ref<any[]>([])
const userSearchLoading = ref(false)
async function searchUsers(query: string) {
  if (!query || query.length < 1) { userSearchResults.value = []; return }
  userSearchLoading.value = true
  try {
    const res = await listUsers({ keyword: query, page: 1, size: 20, status: 'ACTIVE' })
    userSearchResults.value = res.data.data?.records || []
  } catch { userSearchResults.value = [] }
  finally { userSearchLoading.value = false }
}

async function loadIdentities() {
  identityLoading.value = true
  try {
    const res = await listImIdentities({
      platform: identityPlatformFilter.value || undefined,
      keyword: identityKeyword.value || undefined,
      page: identityPage.value,
      size: identitySize.value,
      unboundOnly: unboundOnly.value || undefined
    })
    const data = res.data.data
    identities.value = data?.records || []
    identityTotal.value = data?.total || 0
  } finally { identityLoading.value = false }
}

function openBindDialog() {
  bindForm.kbUserId = null
  bindForm.platform = 'wecom'
  bindForm.platformUid = ''
  bindDialog.value = true
}

async function doBind() {
  if (!bindForm.kbUserId || !bindForm.platform || !bindForm.platformUid) {
    ElMessage.warning('请填写完整信息')
    return
  }
  bindSaving.value = true
  try {
    await bindImIdentity(bindForm)
    ElMessage.success('绑定成功')
    bindDialog.value = false
    await loadIdentities()
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '操作失败')
  } finally { bindSaving.value = false }
}

function rebindAutoUser(row: any) {
  // 打开绑定弹窗，预填 IM 平台和用户 ID
  bindForm.kbUserId = null
  bindForm.platform = row.platform
  bindForm.platformUid = row.platformUid
  bindDialog.value = true
}

async function confirmUnbind(row: any) {
  try {
    await ElMessageBox.confirm(
      `确定解除「${row.kbName || row.kbUsername}」与 ${platformLabel(row.platform)} 身份「${row.platformUid}」的绑定？`,
      '确认解绑', { type: 'warning' }
    )
  } catch { return }
  await unbindImIdentity(row.id)
  ElMessage.success('已解绑')
  await loadIdentities()
}

// ==================== Tab 切换 ====================
function onTabChange(tab: string) {
  if (tab === 'mappings' && mappings.value.length === 0) loadMappings()
  if (tab === 'identities' && identities.value.length === 0) loadIdentities()
}

// ==================== 初始化 ====================
onMounted(() => {
  loadDeptInfo()
  loadMappings()
  loadIdentities()
})
</script>

<style scoped>
.im-page { padding: 0; }

.toolbar {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 16px; gap: 12px; flex-wrap: wrap;
}
.toolbar-left, .toolbar-right { display: flex; align-items: center; gap: 12px; }

.text-muted { color: #909399; }

.fetch-dept-toolbar {
  display: flex; align-items: center; gap: 12px; flex-wrap: wrap;
}
.fetch-summary {
  color: #606266; font-size: 13px; margin-left: 8px;
}
</style>
