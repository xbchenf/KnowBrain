<template>
  <div class="dept-page">
    <el-container>
      <el-aside width="280px" class="dept-sidebar">
        <div class="dept-tree-header">
          <h4>部门树</h4>
          <el-button type="primary" size="small" @click="addRoot">新增</el-button>
        </div>
        <el-tree
          :data="tree"
          :props="{ children: 'children', label: 'name' }"
          node-key="id"
          highlight-current
          @node-click="onSelect"
        />
      </el-aside>
      <el-main>
        <el-form v-if="selected" :model="form" label-width="100px">
          <el-form-item label="部门名称" required>
            <el-input v-model="form.name" />
          </el-form-item>
          <el-form-item label="上级部门">
            <el-select v-model="form.parentId" clearable placeholder="顶级部门">
              <el-option v-for="d in flatList" :key="d.id" :label="d.name" :value="d.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="排序">
            <el-input-number v-model="form.sortOrder" :min="0" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="save">保存</el-button>
            <el-button type="danger" @click="remove" :disabled="!selected.id">删除</el-button>
          </el-form-item>
        </el-form>
        <el-empty v-else description="请从左侧选择部门" />
      </el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listDepartments, createDepartment, updateDepartment, deleteDepartment } from '../api'

const tree = ref<any[]>([])
const flatList = ref<any[]>([])
const selected = ref<any>(null)
const form = ref({ name: '', parentId: null as number | null, sortOrder: 0 })

onMounted(refresh)

async function refresh() {
  const res = await listDepartments()
  tree.value = res.data?.data || []
  flatList.value = flatten(tree.value)
}

function flatten(nodes: any[]): any[] {
  let result: any[] = []
  for (const n of nodes) {
    result.push({ id: n.id, name: n.name })
    if (n.children) result = result.concat(flatten(n.children))
  }
  return result
}

function onSelect(node: any) {
  selected.value = node
  form.value = { name: node.name, parentId: node.parentId || null, sortOrder: node.sortOrder || 0 }
}

function addRoot() {
  selected.value = { id: null }
  form.value = { name: '', parentId: null, sortOrder: 0 }
}

async function save() {
  if (!form.value.name.trim()) return ElMessage.warning('请输入部门名称')
  const data = { ...form.value, parentId: form.value.parentId || 0 }
  if (selected.value.id) {
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
  await ElMessageBox.confirm('确定删除该部门？', '提示', { type: 'warning' })
  await deleteDepartment(selected.value.id)
  ElMessage.success('删除成功')
  selected.value = null
  refresh()
}
</script>

<style scoped>
.dept-page { background: #fff; border-radius: 6px; padding: 0; }
.dept-sidebar { background: #fafafa; border-right: 1px solid #e4e7ed; min-height: calc(100vh - 160px); padding: 16px; }
.dept-tree-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.dept-tree-header h4 { font-size: 14px; color: #303133; }
</style>
