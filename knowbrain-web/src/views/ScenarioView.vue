<template>
  <div class="scenario-page">
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="知识分类" name="categories" />
      <el-tab-pane label="术语词典" name="glossary" />
      <el-tab-pane label="预设问答" name="faq" />
    </el-tabs>

    <!-- ==================== 知识分类 ==================== -->
    <template v-if="activeTab === 'categories'">
      <div class="toolbar">
        <span class="tip">分类用于文档上传时归类，以及检索时缩小范围</span>
        <el-button type="primary" @click="openCategoryCreate(null)">
          <el-icon><Plus /></el-icon> 新增分类
        </el-button>
      </div>

      <div class="cat-tree-panel" v-loading="catLoading">
        <el-tree
          :data="categoryTree"
          node-key="id"
          default-expand-all
          :props="{ children: 'children', label: 'name' }"
        >
          <template #default="{ node, data }">
            <span class="cat-node">
              <span class="cat-name">{{ data.name }}</span>
              <span class="cat-key">{{ data.key }}</span>
              <span class="cat-actions">
                <el-button link type="primary" size="small" @click.stop="openCategoryCreate(data)">
                  添加子分类
                </el-button>
                <el-button link type="danger" size="small" @click.stop="confirmDeleteCategory(data)">
                  删除
                </el-button>
              </span>
            </span>
          </template>
        </el-tree>
      </div>

      <el-dialog v-model="catDialog" :title="catParent ? '新增子分类' : '新增分类'" width="420px">
        <el-form :model="catForm" label-position="top">
          <el-form-item label="分类名称" required>
            <el-input v-model="catForm.name" placeholder="如：网络与连接" />
          </el-form-item>
          <el-form-item label="标识 Key" required>
            <el-input v-model="catForm.key" placeholder="如：network（英文小写，连字符分隔）" />
          </el-form-item>
          <el-form-item v-if="catParent" label="上级分类">
            <el-input :model-value="catParent.name" disabled />
          </el-form-item>
          <el-form-item label="排序">
            <el-input-number v-model="catForm.sortOrder" :min="0" :max="999" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="catDialog = false">取消</el-button>
          <el-button type="primary" :loading="catSaving" @click="doCreateCategory">确定</el-button>
        </template>
      </el-dialog>
    </template>

    <!-- ==================== 术语词典 ==================== -->
    <template v-if="activeTab === 'glossary'">
      <div class="toolbar">
        <span class="tip">用户口语自动改写为正式术语，提升检索精度</span>
        <el-button type="primary" @click="openGlossaryCreate">
          <el-icon><Plus /></el-icon> 新增术语
        </el-button>
      </div>

      <el-table :data="glossaryList" v-loading="glsLoading" stripe>
        <el-table-column prop="term" label="用户口语" width="140" />
        <el-table-column prop="formal" label="正式术语" width="160" />
        <el-table-column label="同义词" min-width="200">
          <template #default="{ row }">
            <el-tag v-for="s in row.synonyms" :key="s" size="small" style="margin-right:4px">{{ s }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160">
          <template #default="{ row }">{{ row.createTime?.substring(0, 16) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button link type="danger" size="small" @click="confirmDeleteGlossary(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-dialog v-model="glsDialog" title="新增术语" width="460px">
        <el-form :model="glsForm" label-position="top">
          <el-form-item label="用户口语" required>
            <el-input v-model="glsForm.term" placeholder="如：电脑卡" />
          </el-form-item>
          <el-form-item label="正式术语" required>
            <el-input v-model="glsForm.formal" placeholder="如：系统运行缓慢" />
          </el-form-item>
          <el-form-item label="同义词（逗号分隔）">
            <el-input v-model="glsForm.synonymsStr" placeholder="如：很卡,反应慢,速度慢" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="glsDialog = false">取消</el-button>
          <el-button type="primary" :loading="glsSaving" @click="doCreateGlossary">确定</el-button>
        </template>
      </el-dialog>
    </template>

    <!-- ==================== 预设问答 ==================== -->
    <template v-if="activeTab === 'faq'">
      <div class="toolbar">
        <span class="tip">命中 ≥ 2 个关键词时直接返回预设答案，跳过 LLM</span>
        <el-button type="primary" @click="openFaqCreate">
          <el-icon><Plus /></el-icon> 新增问答
        </el-button>
      </div>

      <el-table :data="faqList" v-loading="faqLoading" stripe>
        <el-table-column prop="question" label="标准问题" min-width="160" show-overflow-tooltip />
        <el-table-column label="关键词" width="160">
          <template #default="{ row }">
            <el-tag v-for="k in row.keywords" :key="k" size="small" style="margin-right:2px">{{ k }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="分类" width="100">
          <template #default="{ row }">{{ categoryNameMap[row.category] || '-' }}</template>
        </el-table-column>
        <el-table-column label="启用" width="70">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled"
                       @change="(val: boolean) => toggleFaq(row, val)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openFaqEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="confirmDeleteFaq(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- FAQ 新增/编辑弹窗 -->
      <el-dialog v-model="faqDialog" :title="faqEditing ? '编辑问答' : '新增问答'" width="560px">
        <el-form :model="faqForm" label-position="top">
          <el-form-item label="关键词（逗号分隔）" required>
            <el-input v-model="faqForm.keywordsStr" placeholder="如：年假,休假,几天,PTO" />
          </el-form-item>
          <el-form-item label="标准问题" required>
            <el-input v-model="faqForm.question" placeholder="如：我有多少天年假？" />
          </el-form-item>
          <el-form-item label="预设答案" required>
            <el-input v-model="faqForm.answer" type="textarea" :rows="6"
                      placeholder="输入完整的预设答案..." />
          </el-form-item>
          <el-form-item label="所属分类">
            <el-select v-model="faqForm.category" clearable placeholder="选择分类" style="width:100%">
              <el-option v-for="c in flatCategories" :key="c.key" :label="c.name" :value="c.key" />
            </el-select>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="faqDialog = false">取消</el-button>
          <el-button type="primary" :loading="faqSaving" @click="doSaveFaq">保存</el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  listCategories, createCategory, deleteCategory,
  listGlossary, createGlossary, deleteGlossary,
  listFaq, createFaq, updateFaq, deleteFaq
} from '../api'

// ==================== Tab ====================
const activeTab = ref('categories')

// ==================== 分类数据 ====================
const catLoading = ref(false)
const categoryTree = ref<any[]>([])
const flatCategories = ref<any[]>([])
const categoryNameMap = ref<Record<string, string>>({})

const catDialog = ref(false)
const catParent = ref<any>(null)
const catSaving = ref(false)
const catForm = reactive({ name: '', key: '', sortOrder: 0 })

async function loadCategories() {
  catLoading.value = true
  try {
    const res = await listCategories()
    const data = res.data?.data || []
    categoryTree.value = data
    flatCategories.value = flattenTree(data)
    const map: Record<string, string> = {}
    for (const c of flatCategories.value) map[c.key] = c.name
    categoryNameMap.value = map
  } finally { catLoading.value = false }
}

function flattenTree(nodes: any[]): any[] {
  let r: any[] = []
  for (const n of nodes) { r.push(n); if (n.children) r = r.concat(flattenTree(n.children)) }
  return r
}

function openCategoryCreate(parent: any) {
  catParent.value = parent
  catForm.name = ''
  catForm.key = ''
  catForm.sortOrder = 0
  catDialog.value = true
}

async function doCreateCategory() {
  if (!catForm.name.trim() || !catForm.key.trim()) return
  catSaving.value = true
  try {
    await createCategory({
      name: catForm.name.trim(),
      key: catForm.key.trim().toLowerCase().replace(/\s+/g, '-'),
      parentKey: catParent.value?.key || null,
      sortOrder: catForm.sortOrder
    })
    ElMessage.success('分类创建成功')
    catDialog.value = false
    await loadCategories()
  } finally { catSaving.value = false }
}

async function confirmDeleteCategory(row: any) {
  try {
    await ElMessageBox.confirm(`删除分类「${row.name}」？如果该分类下有子分类，也需一并删除。`, '确认删除', { type: 'warning' })
  } catch { return }
  await deleteCategory(row.id)
  ElMessage.success('已删除')
  await loadCategories()
}

// ==================== 术语数据 ====================
const glsLoading = ref(false)
const glossaryList = ref<any[]>([])

const glsDialog = ref(false)
const glsSaving = ref(false)
const glsForm = reactive({ term: '', formal: '', synonymsStr: '' })

async function loadGlossary() {
  glsLoading.value = true
  try {
    const res = await listGlossary()
    glossaryList.value = res.data?.data || []
  } finally { glsLoading.value = false }
}

function openGlossaryCreate() {
  glsForm.term = ''
  glsForm.formal = ''
  glsForm.synonymsStr = ''
  glsDialog.value = true
}

async function doCreateGlossary() {
  if (!glsForm.term.trim() || !glsForm.formal.trim()) return
  glsSaving.value = true
  try {
    await createGlossary({
      term: glsForm.term.trim(),
      formal: glsForm.formal.trim(),
      synonyms: glsForm.synonymsStr.trim()
    })
    ElMessage.success('术语添加成功')
    glsDialog.value = false
    await loadGlossary()
  } finally { glsSaving.value = false }
}

async function confirmDeleteGlossary(row: any) {
  try {
    await ElMessageBox.confirm(`删除术语「${row.term} → ${row.formal}」？`, '确认删除', { type: 'warning' })
  } catch { return }
  await deleteGlossary(row.id)
  ElMessage.success('已删除')
  await loadGlossary()
}

// ==================== FAQ 数据 ====================
const faqLoading = ref(false)
const faqList = ref<any[]>([])

const faqDialog = ref(false)
const faqEditing = ref<any>(null)
const faqSaving = ref(false)
const faqForm = reactive({ keywordsStr: '', question: '', answer: '', category: '' })

async function loadFaq() {
  faqLoading.value = true
  try {
    const res = await listFaq()
    faqList.value = res.data?.data || []
  } finally { faqLoading.value = false }
}

function openFaqCreate() {
  faqEditing.value = null
  faqForm.keywordsStr = ''
  faqForm.question = ''
  faqForm.answer = ''
  faqForm.category = ''
  faqDialog.value = true
}

function openFaqEdit(row: any) {
  faqEditing.value = row
  faqForm.keywordsStr = Array.isArray(row.keywords) ? row.keywords.join(',') : (row.keywords || '')
  faqForm.question = row.question || ''
  faqForm.answer = row.answer || ''
  faqForm.category = row.category || ''
  faqDialog.value = true
}

async function doSaveFaq() {
  if (!faqForm.keywordsStr.trim() || !faqForm.question.trim() || !faqForm.answer.trim()) return
  faqSaving.value = true
  try {
    const payload = {
      keywords: faqForm.keywordsStr.trim(),
      question: faqForm.question.trim(),
      answer: faqForm.answer.trim(),
      category: faqForm.category || null
    }
    if (faqEditing.value) {
      await updateFaq(faqEditing.value.id, payload)
      ElMessage.success('FAQ 已更新')
    } else {
      await createFaq(payload)
      ElMessage.success('FAQ 创建成功')
    }
    faqDialog.value = false
    await loadFaq()
  } finally { faqSaving.value = false }
}

async function toggleFaq(row: any, enabled: boolean) {
  await updateFaq(row.id, { enabled })
  ElMessage.success(enabled ? '已启用' : '已禁用')
  await loadFaq()
}

async function confirmDeleteFaq(row: any) {
  try {
    await ElMessageBox.confirm(`删除问答「${row.question}」？`, '确认删除', { type: 'warning' })
  } catch { return }
  await deleteFaq(row.id)
  ElMessage.success('已删除')
  await loadFaq()
}

// ==================== 初始化 ====================
function onTabChange(tab: any) {
  if (tab === 'categories') loadCategories()
  else if (tab === 'glossary') loadGlossary()
  else if (tab === 'faq') { loadFaq(); loadCategories() /* for category dropdown */ }
}

onMounted(() => loadCategories())
</script>

<style scoped>
.scenario-page { background: #fff; border-radius: 6px; padding: 16px; }
.toolbar {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 16px;
}
.tip { font-size: 13px; color: #909399; }

.cat-tree-panel { max-width: 520px; }
.cat-node { display: flex; align-items: center; gap: 8px; flex: 1; }
.cat-name { font-size: 14px; color: #303133; }
.cat-key { font-size: 12px; color: #a8abb2; font-family: monospace; }
.cat-actions { margin-left: auto; }
</style>
