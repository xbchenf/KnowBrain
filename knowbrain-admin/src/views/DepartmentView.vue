<template>
  <div class="dept-layout">
    <!-- 左侧部门树 -->
    <div class="tree-panel">
      <div class="tree-header">
        <h3>部门架构</h3>
        <button class="btn-add" @click="addRoot">+ 新增</button>
      </div>
      <div class="tree-body">
        <template v-for="node in tree" :key="node.id">
          <TreeNodeRenderer
            :node="node"
            :depth="0"
            :selected-id="selected?.id"
            :expanded-ids="expandedIds"
            @select="onSelect"
            @toggle="toggleExpand"
            @add-child="addChild"
          />
        </template>
        <div v-if="!tree.length" class="tree-empty">暂无部门数据</div>
      </div>
    </div>

    <!-- 右侧详情 -->
    <div class="detail-panel">
      <template v-if="selected">
        <h3>{{ selected.id ? selected.name : '新建部门' }}</h3>
        <p class="detail-meta">{{ selected.id ? '部门详情编辑' : '填写信息创建新部门' }}</p>

        <div v-if="selected.id" class="stat-row">
          <div class="stat-item">
            <div class="stat-value">{{ selected.childCount ?? 0 }}</div>
            <div class="stat-label">子部门</div>
          </div>
          <div class="stat-item">
            <div class="stat-value">{{ selected.memberCount ?? 0 }}</div>
            <div class="stat-label">成员</div>
          </div>
        </div>

        <div class="form-group">
          <label>部门名称</label>
          <input v-model="form.name" type="text" placeholder="输入部门名称" />
        </div>
        <div class="form-group">
          <label>上级部门</label>
          <select v-model="form.parentId">
            <option :value="null">顶级部门（无上级）</option>
            <option v-for="d in flatList" :key="d.id" :value="d.id">{{ d.name }}</option>
          </select>
        </div>
        <div class="form-group">
          <label>排序序号</label>
          <input v-model.number="form.sortOrder" type="number" min="0" style="width:100px" />
        </div>

        <div class="detail-actions">
          <button class="btn-save" @click="save">保存</button>
          <button v-if="selected.id" class="btn-delete" @click="remove">删除部门</button>
        </div>
      </template>
      <div v-else class="empty-state">
        <div class="empty-icon">📂</div>
        <p>从左侧选择部门查看详情</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listDepartments, createDepartment, updateDepartment, deleteDepartment } from '../api'
import TreeNodeRenderer from './TreeNodeRenderer.vue'

const tree = ref<any[]>([])
const flatList = ref<any[]>([])
const selected = ref<any>(null)
const expandedIds = ref<Set<number>>(new Set())
const form = reactive({ name: '', parentId: null as number | null, sortOrder: 0 })

onMounted(refresh)

async function refresh() {
  const res = await listDepartments()
  tree.value = res.data?.data || []
  flatList.value = flatten(tree.value)
  const ids = new Set<number>()
  collectIds(tree.value, ids)
  expandedIds.value = ids
}

function collectIds(nodes: any[], set: Set<number>) {
  for (const n of nodes) {
    set.add(n.id)
    if (n.children) collectIds(n.children, set)
  }
}

function flatten(nodes: any[]): any[] {
  let r: any[] = []
  for (const n of nodes) { r.push(n); if (n.children) r = r.concat(flatten(n.children)) }
  return r
}

function toggleExpand(id: number) {
  const next = new Set(expandedIds.value)
  next.has(id) ? next.delete(id) : next.add(id)
  expandedIds.value = next
}

function onSelect(node: any) {
  selected.value = node
  form.name = node.name
  form.parentId = node.parentId && node.parentId !== 0 ? node.parentId : null
  form.sortOrder = node.sortOrder || 0
}

function addRoot() {
  selected.value = { id: null }
  form.name = ''
  form.parentId = null
  form.sortOrder = 0
}

function addChild(parent: any) {
  selected.value = { id: null, parentId: parent.id }
  form.name = ''
  form.parentId = parent.id
  form.sortOrder = 0
}

async function save() {
  if (!form.name.trim()) return ElMessage.warning('请输入部门名称')
  const data = { name: form.name.trim(), parentId: form.parentId || 0, sortOrder: form.sortOrder }
  if (selected.value?.id) {
    await updateDepartment(selected.value.id, data)
    ElMessage.success('更新成功')
  } else {
    await createDepartment(data)
    ElMessage.success('创建成功')
  }
  selected.value = null
  refresh()
}

async function remove() {
  try { await ElMessageBox.confirm('确定删除该部门？', '提示', { type: 'warning' }) }
  catch { return }
  try {
    await deleteDepartment(selected.value!.id)
    ElMessage.success('删除成功')
    selected.value = null
    refresh()
  } catch (e: any) {
    const msg = e?.response?.data?.message || '删除失败'
    ElMessage.error(msg)
  }
}
</script>

<style scoped>
.dept-layout { display: flex; gap: 16px; }

.tree-panel {
  width: 280px; flex-shrink: 0; background: #fff;
  border: 1px solid #e4e7ed; border-radius: 8px; overflow: hidden;
}
.tree-header {
  padding: 12px 16px; border-bottom: 1px solid #f0f0f0;
  display: flex; align-items: center; justify-content: space-between;
}
.tree-header h3 { font-size: 14px; font-weight: 600; }
.btn-add {
  padding: 3px 10px; border-radius: 4px; font-size: 11px; border: 1px solid #409EFF;
  background: #409EFF; color: #fff; cursor: pointer; font-family: inherit; font-weight: 500;
}
.btn-add:hover { opacity: .85; }
.tree-body { padding: 6px 0; max-height: calc(100vh - 200px); overflow-y: auto; }
.tree-empty { padding: 32px 16px; text-align: center; font-size: 13px; color: #909399; }

.detail-panel {
  flex: 1; background: #fff; border: 1px solid #e4e7ed;
  border-radius: 8px; padding: 28px; min-height: 400px;
}
.detail-panel h3 { font-size: 18px; font-weight: 600; margin-bottom: 4px; }
.detail-meta { font-size: 12px; color: #909399; margin-bottom: 24px; }

.stat-row { display: flex; gap: 16px; margin-bottom: 24px; }
.stat-item { text-align: center; padding: 14px 24px; background: #fafafa; border-radius: 8px; min-width: 80px; }
.stat-value { font-size: 22px; font-weight: 700; color: #409EFF; }
.stat-label { font-size: 11px; color: #909399; margin-top: 2px; }

.form-group { margin-bottom: 16px; }
.form-group label { display: block; font-size: 13px; color: #606266; margin-bottom: 4px; font-weight: 500; }
.form-group input, .form-group select {
  width: 100%; max-width: 320px; padding: 8px 12px; border: 1px solid #e4e7ed;
  border-radius: 6px; font-size: 13px; outline: none; font-family: inherit; background: #fff;
}
.form-group input:focus, .form-group select:focus { border-color: #409EFF; box-shadow: 0 0 0 2px rgba(64,158,255,.1); }

.detail-actions { display: flex; gap: 8px; margin-top: 28px; }
.btn-save {
  padding: 8px 20px; border-radius: 6px; border: none; background: #409EFF; color: #fff;
  cursor: pointer; font-size: 13px; font-family: inherit; font-weight: 500;
}
.btn-save:hover { opacity: .85; }
.btn-delete {
  padding: 8px 20px; border-radius: 6px; border: 1px solid #fde2e2; background: #fff; color: #f56c6c;
  cursor: pointer; font-size: 13px; font-family: inherit;
}
.btn-delete:hover { background: #fef0f0; border-color: #f56c6c; }

.empty-state {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  height: 300px; color: #909399;
}
.empty-icon { font-size: 40px; margin-bottom: 12px; opacity: .4; }
.empty-state p { font-size: 13px; }
</style>
