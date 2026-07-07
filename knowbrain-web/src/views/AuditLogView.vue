<template>
  <div>
    <!-- 筛选栏 -->
    <div class="filter-bar">
      <el-select v-model="filters.operationType" placeholder="操作类型" clearable style="width:130px">
        <el-option label="创建 (CREATE)" value="CREATE" />
        <el-option label="更新 (UPDATE)" value="UPDATE" />
        <el-option label="删除 (DELETE)" value="DELETE" />
        <el-option label="其他 (OTHER)" value="OTHER" />
      </el-select>
      <el-select v-model="filters.resourceType" placeholder="资源类型" clearable style="width:150px">
        <el-option label="用户" value="USER" />
        <el-option label="部门" value="DEPARTMENT" />
        <el-option label="空间" value="SPACE" />
        <el-option label="空间成员" value="SPACE_MEMBER" />
        <el-option label="文档" value="DOCUMENT" />
        <el-option label="知识分类" value="SCENARIO_CATEGORY" />
        <el-option label="术语" value="SCENARIO_GLOSSARY" />
        <el-option label="FAQ" value="SCENARIO_FAQ" />
        <el-option label="认证" value="AUTH" />
      </el-select>
      <el-date-picker
        v-model="filters.dateRange"
        type="daterange"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        format="YYYY-MM-DD"
        value-format="YYYY-MM-DD"
        style="width:260px"
      />
      <el-input v-model="filters.keyword" placeholder="搜索操作人/资源/描述" clearable style="width:220px" />
      <el-button type="primary" @click="loadList">查询</el-button>
    </div>

    <!-- 审计日志表格 -->
    <el-table :data="records" v-loading="loading" stripe style="width:100%">
      <el-table-column label="操作人" width="120">
        <template #default="{ row }">
          {{ row.operatorName || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="操作类型" width="110">
        <template #default="{ row }">
          <el-tag :type="opTagType(row.operationType)" size="small">
            {{ opLabel(row.operationType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="资源类型" width="110">
        <template #default="{ row }">
          <el-tag :type="resTagType(row.resourceType)" size="small" effect="plain">
            {{ resLabel(row.resourceType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="resourceName" label="资源名称" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.resourceName || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="description" label="操作描述" min-width="180" show-overflow-tooltip />
      <el-table-column label="结果" width="80">
        <template #default="{ row }">
          <el-tag :type="row.success === 1 ? 'success' : 'danger'" size="small">
            {{ row.success === 1 ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="失败原因" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.errorMessage" style="color:#f56c6c">{{ row.errorMessage }}</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="ipAddress" label="IP 地址" width="140" />
      <el-table-column label="操作时间" width="170">
        <template #default="{ row }">
          {{ row.createTime?.substring(0, 19) || '-' }}
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pager-wrap">
      <el-pagination
        v-model:current-page="pagination.page"
        :page-size="pagination.size"
        :total="pagination.total"
        layout="total, prev, pager, next"
        @current-change="loadList"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { listAuditLogs } from '../api'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const records = ref<any[]>([])
const pagination = reactive({ page: 1, size: 20, total: 0 })
const filters = reactive({
  operationType: '',
  resourceType: '',
  keyword: '',
  dateRange: null as string[] | null
})

onMounted(() => { loadList() })

async function loadList() {
  loading.value = true
  try {
    const params: Record<string, any> = {
      page: pagination.page,
      size: pagination.size
    }
    if (filters.operationType) params.operationType = filters.operationType
    if (filters.resourceType) params.resourceType = filters.resourceType
    if (filters.keyword) params.keyword = filters.keyword
    if (filters.dateRange && filters.dateRange.length === 2) {
      params.startDate = filters.dateRange[0]
      params.endDate = filters.dateRange[1]
    }
    const res = await listAuditLogs(params)
    const data = res.data?.data || res.data
    records.value = data?.records || []
    pagination.total = data?.total || 0
  } catch {
    ElMessage.error('加载审计日志失败')
  } finally {
    loading.value = false
  }
}

function opLabel(type: string) {
  const map: Record<string, string> = {
    CREATE: '创建', UPDATE: '更新', DELETE: '删除', OTHER: '其他'
  }
  return map[type] || type
}

function opTagType(type: string) {
  const map: Record<string, string> = {
    CREATE: 'success', UPDATE: 'warning', DELETE: 'danger', OTHER: 'info'
  }
  return map[type] || 'info'
}

function resLabel(type: string) {
  const map: Record<string, string> = {
    USER: '用户', DEPARTMENT: '部门', SPACE: '空间', SPACE_MEMBER: '空间成员',
    DOCUMENT: '文档', SCENARIO_CATEGORY: '知识分类', SCENARIO_GLOSSARY: '术语',
    SCENARIO_FAQ: 'FAQ', AUTH: '认证'
  }
  return map[type] || type
}

function resTagType(type: string) {
  const map: Record<string, string> = {
    USER: '', DEPARTMENT: '', SPACE: 'success', SPACE_MEMBER: 'warning',
    DOCUMENT: 'primary', SCENARIO_CATEGORY: 'info', SCENARIO_GLOSSARY: 'info',
    SCENARIO_FAQ: 'info', AUTH: ''
  }
  return map[type] || ''
}
</script>

<style scoped>
.filter-bar {
  display: flex; gap: 12px; margin-bottom: 16px; flex-wrap: wrap;
}
.pager-wrap {
  display: flex; justify-content: flex-end; margin-top: 16px;
}
</style>
