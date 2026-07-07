<template>
  <div>
    <!-- 统计卡片 -->
    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-num">{{ stats.total }}</div>
        <div class="stat-label">总反馈</div>
      </div>
      <div class="stat-card useful">
        <div class="stat-num">{{ stats.useful }}</div>
        <div class="stat-label">有用 👍</div>
      </div>
      <div class="stat-card useless">
        <div class="stat-num">{{ stats.useless }}</div>
        <div class="stat-label">无用 👎</div>
      </div>
      <div class="stat-card">
        <div class="stat-num">{{ stats.usefulRate }}%</div>
        <div class="stat-label">有用率</div>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <el-select v-model="filters.rating" placeholder="评分筛选" clearable style="width:120px">
        <el-option label="👍 有用" value="useful" />
        <el-option label="👎 无用" value="useless" />
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
      <el-input v-model="filters.keyword" placeholder="搜索问题或答案关键词" clearable style="width:240px" />
      <el-button type="primary" @click="loadList">查询</el-button>
    </div>

    <!-- 反馈列表 -->
    <el-table :data="records" v-loading="loading" stripe style="width:100%">
      <el-table-column prop="question" label="用户问题" min-width="200" show-overflow-tooltip />
      <el-table-column prop="answer" label="AI 回答" min-width="280" show-overflow-tooltip>
        <template #default="{ row }">
          {{ truncate(row.answer, 80) }}
        </template>
      </el-table-column>
      <el-table-column label="评分" width="80">
        <template #default="{ row }">
          <el-tag :type="row.rating === 'useful' ? 'success' : 'danger'" size="small">
            {{ row.rating === 'useful' ? '👍 有用' : '👎 无用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="comment" label="用户评语" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.comment || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="时间" width="160">
        <template #default="{ row }">
          {{ row.createTime?.substring(0, 16) || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="viewDetail(row)">详情</el-button>
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

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="反馈详情" width="640px">
      <div class="detail-section">
        <div class="detail-label">用户问题</div>
        <div class="detail-text">{{ detail.question }}</div>
      </div>
      <div class="detail-section">
        <div class="detail-label">AI 回答</div>
        <div class="detail-text answer">{{ detail.answer }}</div>
      </div>
      <div class="detail-section">
        <div class="detail-label">用户评分</div>
        <div class="detail-text">
          <el-tag :type="detail.rating === 'useful' ? 'success' : 'danger'" size="small">
            {{ detail.rating === 'useful' ? '👍 有用' : '👎 无用' }}
          </el-tag>
        </div>
      </div>
      <div class="detail-section" v-if="detail.comment">
        <div class="detail-label">补充意见</div>
        <div class="detail-text">{{ detail.comment }}</div>
      </div>
      <div class="detail-section">
        <div class="detail-label">反馈时间</div>
        <div class="detail-text">{{ detail.createTime?.substring(0, 19) || '-' }}</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { getFeedbackStats, listFeedback } from '../api'

const loading = ref(false)

// 统计
const stats = reactive({ total: 0, useful: 0, useless: 0, usefulRate: 0 })

// 列表
const records = ref<any[]>([])
const pagination = reactive({ page: 1, size: 20, total: 0 })
const filters = reactive<Record<string, any>>({
  rating: '',
  dateRange: null as [string, string] | null,
  keyword: ''
})

// 详情
const detailVisible = ref(false)
const detail = reactive<Record<string, any>>({})

function truncate(text: string, maxLen: number) {
  if (!text) return ''
  return text.length > maxLen ? text.substring(0, maxLen) + '...' : text
}

async function loadStats() {
  const params: Record<string, string> = {}
  if (filters.dateRange) {
    params.startDate = filters.dateRange[0]
    params.endDate = filters.dateRange[1]
  }
  try {
    const { data } = await getFeedbackStats(params.startDate, params.endDate)
    if (data?.code === 200) {
      Object.assign(stats, data.data)
    }
  } catch { /* 统计加载失败不影响列表 */ }
}

async function loadList() {
  loading.value = true
  try {
    const params: Record<string, any> = { page: pagination.page, size: pagination.size }
    if (filters.rating) params.rating = filters.rating
    if (filters.keyword) params.keyword = filters.keyword
    if (filters.dateRange) {
      params.startDate = filters.dateRange[0]
      params.endDate = filters.dateRange[1]
    }
    const { data } = await listFeedback(params)
    if (data?.code === 200) {
      records.value = data.data.records || []
      pagination.total = data.data.total || 0
    }
  } finally {
    loading.value = false
  }
}

function viewDetail(row: any) {
  Object.assign(detail, row)
  detailVisible.value = true
}

onMounted(() => {
  loadStats()
  loadList()
})
</script>

<style scoped>
/* 统计卡片 */
.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}
.stat-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px 24px;
  box-shadow: 0 1px 3px rgba(0,0,0,.06);
}
.stat-num {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
}
.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}
.stat-card.useful .stat-num { color: #67c23a; }
.stat-card.useless .stat-num { color: #f56c6c; }

/* 筛选栏 */
.filter-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

/* 分页 */
.pager-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

/* 详情弹窗 */
.detail-section {
  margin-bottom: 16px;
}
.detail-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 6px;
}
.detail-text {
  font-size: 14px;
  color: #303133;
  line-height: 1.7;
}
.detail-text.answer {
  background: #f5f7fa;
  padding: 12px 16px;
  border-radius: 8px;
  white-space: pre-wrap;
}
</style>
