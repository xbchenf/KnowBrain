<template>
  <div class="evaluation-page">
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="数据集" name="datasets" />
      <el-tab-pane label="评测运行" name="runs" />
    </el-tabs>

    <!-- ============================================ Tab 1: 数据集 ============================================ -->
    <template v-if="activeTab === 'datasets'">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-select v-model="scenarioFilter" placeholder="全部场景" clearable style="width:140px" @change="loadDatasets">
            <el-option v-for="s in scenarioOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </div>
        <div class="toolbar-right">
          <el-button type="primary" :icon="Plus" @click="openDatasetDialog()">新建数据集</el-button>
        </div>
      </div>

      <el-table :data="datasets" v-loading="datasetLoading" stripe>
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column label="场景" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="scenarioTagType(row.scenario)">{{ scenarioLabel(row.scenario) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="questionCount" label="问题数" width="80" align="center" />
        <el-table-column prop="createTime" label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openQuestionsDialog(row)">问题</el-button>
            <el-button link type="success" size="small" @click="runFromDataset(row)">运行评测</el-button>
            <el-button link type="warning" size="small" @click="openDatasetDialog(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="confirmDeleteDataset(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无数据集，点击「新建数据集」创建第一个" />
        </template>
      </el-table>

      <el-pagination
        v-model:current-page="dsPage" :page-size="dsSize" :total="dsTotal"
        layout="prev, pager, next, total" @current-change="loadDatasets"
        style="margin-top:16px;justify-content:center"
      />
    </template>

    <!-- ============================================ Tab 2: 评测运行 ============================================ -->
    <template v-if="activeTab === 'runs'">
      <div class="toolbar">
        <div class="toolbar-left" />
        <div class="toolbar-right" style="display:flex;gap:8px">
          <el-select v-model="selectedDatasetId" placeholder="选择数据集" style="width:220px" @focus="loadDatasetOptions">
            <el-option v-for="ds in datasetOptions" :key="ds.id" :label="`${ds.name}（${ds.questionCount}题）`" :value="ds.id" />
          </el-select>
          <el-button type="primary" :icon="VideoPlay" :loading="startingRun" @click="startRun" :disabled="!selectedDatasetId">
            开始评测
          </el-button>
        </div>
      </div>

      <el-table :data="runs" v-loading="runLoading" stripe>
        <el-table-column type="index" label="#" width="50" />
        <el-table-column label="数据集" min-width="130">
          <template #default="{ row }">{{ getDatasetName(row.datasetId) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="runStatusType(row.status)">{{ runStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="忠实度" width="80" align="center">
          <template #default="{ row }">{{ formatScore(row.avgFaithfulness) }}</template>
        </el-table-column>
        <el-table-column label="相关性" width="80" align="center">
          <template #default="{ row }">{{ formatScore(row.avgRelevance) }}</template>
        </el-table-column>
        <el-table-column label="召回率" width="80" align="center">
          <template #default="{ row }">{{ formatScore(row.avgContextRecall) }}</template>
        </el-table-column>
        <el-table-column label="平均延迟" width="90" align="center">
          <template #default="{ row }">{{ row.avgLatencyMs != null ? row.avgLatencyMs + 'ms' : '-' }}</template>
        </el-table-column>
        <el-table-column label="进度" width="90" align="center">
          <template #default="{ row }">{{ row.completedQuestions }} / {{ row.totalQuestions }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="viewRunResults(row)">查看结果</el-button>
            <el-button link type="danger" size="small" @click="confirmDeleteRun(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无评测运行，选择数据集后点击「开始评测」" />
        </template>
      </el-table>

      <el-pagination
        v-model:current-page="runPage" :page-size="runSize" :total="runTotal"
        layout="prev, pager, next, total" @current-change="loadRuns"
        style="margin-top:16px;justify-content:center"
      />
      <!-- 定时刷新运行中任务 -->
      <div v-if="hasRunningTask" style="text-align:center;margin-top:8px;color:#909399;font-size:12px">
        评测进行中，每 5 秒自动刷新...
      </div>
    </template>

    <!-- ========== 对话框：新建/编辑数据集 ========== -->
    <el-dialog v-model="datasetDialogVisible" :title="editingDataset ? '编辑数据集' : '新建数据集'" width="440px" :close-on-click-modal="false" @closed="resetDatasetForm">
      <el-form :model="datasetForm" label-position="top">
        <el-form-item label="名称" required>
          <el-input v-model="datasetForm.name" placeholder="如：IT 运维基准测试" />
        </el-form-item>
        <el-form-item label="场景">
          <el-select v-model="datasetForm.scenario" style="width:100%">
            <el-option v-for="s in scenarioOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="datasetForm.description" type="textarea" :rows="3" placeholder="可选描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="datasetDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingDataset" @click="saveDataset">保存</el-button>
      </template>
    </el-dialog>

    <!-- ========== 对话框：问题管理 ========== -->
    <el-dialog v-model="questionsDialogVisible" :title="`${currentDataset?.name} — 问题管理`" width="720px" :close-on-click-modal="false">
      <div style="margin-bottom:12px;display:flex;gap:8px">
        <el-button size="small" :icon="Plus" @click="openQuestionForm(null)">添加问题</el-button>
        <el-button size="small" :icon="Upload" @click="showBatchImport = !showBatchImport">批量导入</el-button>
      </div>
      <!-- 批量导入区 -->
      <div v-if="showBatchImport" style="margin-bottom:12px;padding:12px;background:#f5f7fa;border-radius:8px">
        <div style="font-size:13px;color:#606266;margin-bottom:8px">粘贴 JSON 数组（每项含 question / expectedAnswer / expectedDocIds）：</div>
        <el-input v-model="batchJson" type="textarea" :rows="5" placeholder='[{"question": "VPN怎么配置？", "expectedAnswer": "先打开..."}]' />
        <el-button size="small" type="primary" style="margin-top:8px" :loading="importing" @click="doBatchImport">导入</el-button>
      </div>
      <el-table :data="questions" v-loading="questionLoading" stripe max-height="380">
        <el-table-column type="index" label="#" width="40" />
        <el-table-column prop="question" label="问题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="expectedAnswer" label="预期答案" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.expectedAnswer || '(未设置)' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openQuestionForm(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="confirmDeleteQuestion(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无问题，点击「添加问题」或「批量导入」" />
        </template>
      </el-table>
      <el-pagination
        v-model:current-page="qPage" :page-size="qSize" :total="qTotal"
        layout="prev, pager, next" @current-change="loadQuestions"
        style="margin-top:12px;justify-content:center"
      />
      <template #footer>
        <el-button @click="questionsDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- ========== 对话框：添加/编辑问题 ========== -->
    <el-dialog v-model="questionFormVisible" :title="editingQuestion ? '编辑问题' : '添加问题'" width="520px" :close-on-click-modal="false" @closed="resetQuestionForm">
      <el-form :model="questionForm" label-position="top">
        <el-form-item label="问题" required>
          <el-input v-model="questionForm.question" type="textarea" :rows="2" placeholder="用户可能问的问题，如：VPN怎么配置？" />
        </el-form-item>
        <el-form-item label="预期答案（可选）">
          <el-input v-model="questionForm.expectedAnswer" type="textarea" :rows="3" placeholder="人工标注的参考答案" />
        </el-form-item>
        <el-form-item label="预期文档 ID（可选，逗号分隔）">
          <el-input v-model="questionForm.expectedDocIds" placeholder="如：1,3,5" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="questionFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingQuestion" @click="saveQuestion">保存</el-button>
      </template>
    </el-dialog>

    <!-- ========== 抽屉：运行结果详情 ========== -->
    <el-drawer v-model="resultDrawerVisible" title="评测结果详情" size="70%">
      <template v-if="currentRun">
        <!-- 汇总指标卡片 -->
        <div class="stats-row">
          <div class="stat-card"><div class="stat-num">{{ formatScore(currentRun.avgFaithfulness) }}</div><div class="stat-label">忠实度</div></div>
          <div class="stat-card"><div class="stat-num">{{ formatScore(currentRun.avgRelevance) }}</div><div class="stat-label">相关性</div></div>
          <div class="stat-card"><div class="stat-num">{{ formatScore(currentRun.avgContextRecall) }}</div><div class="stat-label">召回率</div></div>
          <div class="stat-card"><div class="stat-num">{{ currentRun.avgLatencyMs ? currentRun.avgLatencyMs + 'ms' : '-' }}</div><div class="stat-label">平均延迟</div></div>
        </div>
        <div style="margin-bottom:12px;color:#909399;font-size:13px">
          数据集：{{ getDatasetName(currentRun.datasetId) }} &nbsp;|&nbsp;
          进度：{{ currentRun.completedQuestions }} / {{ currentRun.totalQuestions }} &nbsp;|&nbsp;
          状态：<el-tag size="small" :type="runStatusType(currentRun.status)">{{ runStatusLabel(currentRun.status) }}</el-tag>
        </div>
        <!-- 结果表格 -->
        <el-table :data="results" v-loading="resultLoading" stripe>
          <el-table-column type="index" label="#" width="40" />
          <el-table-column prop="questionText" label="问题" min-width="180" show-overflow-tooltip />
          <el-table-column label="答案" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ row.actualAnswer ? row.actualAnswer.substring(0, 100) + (row.actualAnswer.length > 100 ? '...' : '') : '' }}</template>
          </el-table-column>
          <el-table-column label="忠实度" width="75" align="center">
            <template #default="{ row }">
              <span :style="{ color: scoreColor(row.faithfulness) }">{{ formatScore(row.faithfulness) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="相关性" width="75" align="center">
            <template #default="{ row }">
              <span :style="{ color: scoreColor(row.answerRelevance) }">{{ formatScore(row.answerRelevance) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="召回率" width="75" align="center">
            <template #default="{ row }">
              <span :style="{ color: scoreColor(row.contextRecall) }">{{ formatScore(row.contextRecall) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="延迟" width="75" align="center">
            <template #default="{ row }">{{ row.latencyMs }}ms</template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="resPage" :page-size="resSize" :total="resTotal"
          layout="prev, pager, next" @current-change="loadResults"
          style="margin-top:12px;justify-content:center"
        />
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Upload, VideoPlay } from '@element-plus/icons-vue'
import {
  listEvaluationDatasets, createEvaluationDataset, updateEvaluationDataset, deleteEvaluationDataset,
  listEvaluationQuestions, addEvaluationQuestion, batchImportQuestions,
  updateEvaluationQuestion, deleteEvaluationQuestion,
  startEvaluationRun, listEvaluationRuns, getEvaluationRun, getEvaluationRunResults, deleteEvaluationRun
} from '../api'

// ===== Tab 状态 =====
const activeTab = ref('datasets')

function onTabChange(tab: string) {
  if (tab === 'datasets' && datasets.value.length === 0) loadDatasets()
  if (tab === 'runs' && runs.value.length === 0) loadRuns()
}

// ===== Tab 1: 数据集 =====
const datasets = ref<any[]>([])
const datasetLoading = ref(false)
const dsPage = ref(1); const dsSize = ref(20); const dsTotal = ref(0)
const scenarioFilter = ref('')

const datasetDialogVisible = ref(false)
const editingDataset = ref<any>(null)
const savingDataset = ref(false)
const datasetForm = reactive({ name: '', scenario: 'general', description: '' })
const scenarioOptions = ref<{ label: string; value: string }[]>([
  { label: 'IT 运维', value: 'it-helpdesk' },
  { label: 'HR 制度', value: 'hr-policy' },
  { label: '通用', value: 'general' },
])

async function loadDatasets() {
  datasetLoading.value = true
  try {
    const res = await listEvaluationDatasets({
      scenario: scenarioFilter.value || undefined,
      page: dsPage.value, size: dsSize.value
    })
    const data = res.data.data
    datasets.value = data?.records || []
    dsTotal.value = data?.total || 0
  } finally { datasetLoading.value = false }
}

function openDatasetDialog(row?: any) {
  editingDataset.value = row || null
  if (row) {
    datasetForm.name = row.name
    datasetForm.scenario = row.scenario || 'general'
    datasetForm.description = row.description || ''
  } else {
    datasetForm.name = ''
    datasetForm.scenario = 'general'
    datasetForm.description = ''
  }
  datasetDialogVisible.value = true
}

function resetDatasetForm() {
  datasetForm.name = ''
  datasetForm.scenario = 'general'
  datasetForm.description = ''
  editingDataset.value = null
}

async function saveDataset() {
  if (!datasetForm.name.trim()) { ElMessage.warning('请输入数据集名称'); return }
  savingDataset.value = true
  try {
    if (editingDataset.value) {
      await updateEvaluationDataset(editingDataset.value.id, { ...datasetForm })
      ElMessage.success('更新成功')
    } else {
      await createEvaluationDataset({ ...datasetForm })
      ElMessage.success('创建成功')
    }
    datasetDialogVisible.value = false
    await loadDatasets()
    await loadDatasetOptions()
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '操作失败')
  } finally { savingDataset.value = false }
}

async function confirmDeleteDataset(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除数据集「${row.name}」？将同时删除所有关联问题和评测结果。`, '确认删除', { type: 'warning' })
  } catch { return }
  await deleteEvaluationDataset(row.id)
  ElMessage.success('已删除')
  await loadDatasets()
}

// ===== Tab 1 子: 问题管理 =====
const questionsDialogVisible = ref(false)
const currentDataset = ref<any>(null)
const questions = ref<any[]>([])
const questionLoading = ref(false)
const qPage = ref(1); const qSize = ref(50); const qTotal = ref(0)
const showBatchImport = ref(false)
const batchJson = ref('')
const importing = ref(false)

const questionFormVisible = ref(false)
const editingQuestion = ref<any>(null)
const savingQuestion = ref(false)
const questionForm = reactive({ question: '', expectedAnswer: '', expectedDocIds: '' })

async function openQuestionsDialog(ds: any) {
  currentDataset.value = ds
  showBatchImport.value = false
  batchJson.value = ''
  qPage.value = 1
  questionsDialogVisible.value = true
  await loadQuestions()
}

async function loadQuestions() {
  if (!currentDataset.value) return
  questionLoading.value = true
  try {
    const res = await listEvaluationQuestions(currentDataset.value.id, { page: qPage.value, size: qSize.value })
    const data = res.data.data
    questions.value = data?.records || []
    qTotal.value = data?.total || 0
  } finally { questionLoading.value = false }
}

function openQuestionForm(row: any) {
  editingQuestion.value = row
  if (row) {
    questionForm.question = row.question
    questionForm.expectedAnswer = row.expectedAnswer || ''
    questionForm.expectedDocIds = row.expectedDocIds || ''
  } else {
    questionForm.question = ''
    questionForm.expectedAnswer = ''
    questionForm.expectedDocIds = ''
  }
  questionFormVisible.value = true
}

function resetQuestionForm() {
  questionForm.question = ''
  questionForm.expectedAnswer = ''
  questionForm.expectedDocIds = ''
  editingQuestion.value = null
}

async function saveQuestion() {
  if (!questionForm.question.trim()) { ElMessage.warning('请输入问题'); return }
  savingQuestion.value = true
  try {
    if (editingQuestion.value) {
      await updateEvaluationQuestion(currentDataset.value!.id, editingQuestion.value.id, { ...questionForm })
      ElMessage.success('更新成功')
    } else {
      await addEvaluationQuestion(currentDataset.value!.id, { ...questionForm })
      ElMessage.success('添加成功')
    }
    questionFormVisible.value = false
    await loadQuestions()
    await loadDatasets() // 更新 question_count
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '操作失败')
  } finally { savingQuestion.value = false }
}

async function confirmDeleteQuestion(row: any) {
  try {
    await ElMessageBox.confirm('确定删除此问题？', '确认删除', { type: 'warning' })
  } catch { return }
  await deleteEvaluationQuestion(currentDataset.value!.id, row.id)
  ElMessage.success('已删除')
  await loadQuestions()
  await loadDatasets()
}

async function doBatchImport() {
  if (!batchJson.value.trim()) { ElMessage.warning('请输入 JSON 数据'); return }
  let items: any[]
  try { items = JSON.parse(batchJson.value) } catch {
    ElMessage.error('JSON 格式错误'); return
  }
  if (!Array.isArray(items)) { ElMessage.error('需要 JSON 数组'); return }
  importing.value = true
  try {
    const res = await batchImportQuestions(currentDataset.value!.id, items)
    ElMessage.success(`导入完成，共 ${res.data.data?.imported || 0} 条`)
    batchJson.value = ''
    showBatchImport.value = false
    await loadQuestions()
    await loadDatasets()
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '导入失败')
  } finally { importing.value = false }
}

// ===== Tab 2: 评测运行 =====
const runs = ref<any[]>([])
const runLoading = ref(false)
const runPage = ref(1); const runSize = ref(20); const runTotal = ref(0)
const selectedDatasetId = ref<number | null>(null)
const startingRun = ref(false)
const datasetOptions = ref<any[]>([])
let refreshTimer: ReturnType<typeof setInterval> | null = null

const hasRunningTask = computed(() => runs.value.some((r: any) => r.status === 'RUNNING'))

async function loadRuns(silent = false) {
  if (!silent) runLoading.value = true
  try {
    const res = await listEvaluationRuns({ page: runPage.value, size: runSize.value })
    const data = res.data.data
    runs.value = data?.records || []
    runTotal.value = data?.total || 0
    // 有运行中任务时，5 秒后自动刷新（静默模式，保留当前页码）
    if (refreshTimer) clearInterval(refreshTimer)
    if (hasRunningTask.value) {
      refreshTimer = setInterval(() => { loadRuns(true) }, 5000)
    }
  } finally { runLoading.value = false }
}

// 页面卸载时清除定时器
onBeforeUnmount(() => { if (refreshTimer) clearInterval(refreshTimer) })

async function loadDatasetOptions() {
  if (datasetOptions.value.length > 0) return
  try {
    const res = await listEvaluationDatasets({ page: 1, size: 500 })
    datasetOptions.value = res.data.data?.records || []
  } catch { /* ignore */ }
}

async function startRun() {
  if (!selectedDatasetId.value) return
  startingRun.value = true
  try {
    await startEvaluationRun(selectedDatasetId.value)
    ElMessage.success('评测已启动')
    await loadRuns()
    selectedDatasetId.value = null
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '启动失败')
  } finally { startingRun.value = false }
}

// 从数据集 Tab 直接启动评测
async function runFromDataset(row: any) {
  try {
    await startEvaluationRun(row.id)
    ElMessage.success(`「${row.name}」评测已启动`)
    activeTab.value = 'runs'
    runPage.value = 1
    await loadRuns()
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '启动失败')
  }
}

async function confirmDeleteRun(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除此运行记录？关联的评测结果将一并删除。`, '确认删除', { type: 'warning' })
  } catch { return }
  await deleteEvaluationRun(row.id)
  ElMessage.success('已删除')
  await loadRuns()
}

// ===== 结果详情 =====
const resultDrawerVisible = ref(false)
const currentRun = ref<any>(null)
const results = ref<any[]>([])
const resultLoading = ref(false)
const resPage = ref(1); const resSize = ref(50); const resTotal = ref(0)

async function viewRunResults(row: any) {
  currentRun.value = row
  resPage.value = 1
  resultDrawerVisible.value = true
  await loadResults()
}

async function loadResults() {
  if (!currentRun.value) return
  resultLoading.value = true
  try {
    const res = await getEvaluationRunResults(currentRun.value.id, { page: resPage.value, size: resSize.value })
    const data = res.data.data
    results.value = data?.records || []
    resTotal.value = data?.total || 0
  } finally { resultLoading.value = false }
}

// ===== 辅助函数 =====
function formatTime(t: string) { return t ? t.replace('T', ' ').substring(0, 19) : '-' }
function formatScore(v: any): string { return v != null ? Number(v).toFixed(2) : '-' }

function scoreColor(v: any): string {
  if (v == null) return '#909399'
  const n = Number(v)
  if (n >= 0.8) return '#67c23a'
  if (n >= 0.6) return '#e6a23c'
  return '#f56c6c'
}

function scenarioLabel(s: string) {
  const map: Record<string, string> = { 'it-helpdesk': 'IT 运维', 'hr-policy': 'HR 制度', 'general': '通用' }
  return map[s] || s || '—'
}

function scenarioTagType(s: string): 'success' | 'warning' | 'info' {
  if (s === 'it-helpdesk') return 'success'
  if (s === 'hr-policy') return 'warning'
  return 'info'
}

function runStatusType(s: string): 'warning' | 'success' | 'danger' {
  if (s === 'RUNNING') return 'warning'
  if (s === 'COMPLETED') return 'success'
  return 'danger'
}

function runStatusLabel(s: string) {
  const map: Record<string, string> = { RUNNING: '运行中', COMPLETED: '已完成', FAILED: '失败' }
  return map[s] || s
}

function getDatasetName(id: number) {
  const ds = datasetOptions.value.find((d: any) => d.id === id)
  if (ds) return ds.name
  const runDs = runs.value.find((r: any) => r.datasetId === id)
  return runDs ? `ID:${id}` : `ID:${id}`
}

// ===== 初始化 =====
onMounted(() => {
  loadDatasets()
  loadDatasetOptions()
})
</script>

<style scoped>
.evaluation-page { padding: 0; }

.toolbar {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 12px;
}

.stats-row {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px;
  margin-bottom: 16px;
}
.stat-card {
  background: #fff; border-radius: 10px; padding: 16px 20px;
  box-shadow: 0 1px 3px rgba(0,0,0,.06); text-align: center;
}
.stat-num { font-size: 24px; font-weight: 700; color: #303133; }
.stat-label { font-size: 13px; color: #909399; margin-top: 4px; }
</style>
