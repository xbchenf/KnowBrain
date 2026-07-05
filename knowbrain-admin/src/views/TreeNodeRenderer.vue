<template>
  <div>
    <div
      :class="['tree-node', { active: isSelected }]"
      :style="{ paddingLeft: `calc(12px + ${indent})` }"
      @click="$emit('select', node)"
    >
      <span class="expand" @click.stop="hasChildren && $emit('toggle', node.id)">
        {{ hasChildren ? (isExpanded ? '▼' : '▶') : '' }}
      </span>
      <span class="node-icon">{{ icon }}</span>
      <span class="node-name">{{ node.name }}</span>
      <span class="node-actions">
        <button class="act" title="添加子部门" @click.stop="$emit('addChild', node)">+</button>
        <button class="act" title="更多" @click.stop="$emit('select', node)">⋯</button>
      </span>
    </div>
    <template v-if="hasChildren && isExpanded">
      <TreeNodeRenderer
        v-for="child in node.children"
        :key="child.id"
        :node="child"
        :depth="depth + 1"
        :selected-id="selectedId"
        :expanded-ids="expandedIds"
        @select="(n: any) => $emit('select', n)"
        @toggle="(id: number) => $emit('toggle', id)"
        @add-child="(n: any) => $emit('addChild', n)"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  node: any
  depth: number
  selectedId?: number
  expandedIds: Set<number>
}>()

defineEmits<{
  select: [node: any]
  toggle: [id: number]
  addChild: [node: any]
}>()

const hasChildren = computed(() => props.node.children?.length > 0)
const isExpanded = computed(() => props.expandedIds.has(props.node.id))
const isSelected = computed(() => props.selectedId === props.node.id)
const indent = computed(() => (props.depth || 0) * 20 + 'px')
const icons = ['💻','📦','👥','💰','📢','📊','🏢','⚙️','📋','🔧']
const icon = computed(() => icons[props.node.id % icons.length])
</script>

<style scoped>
.tree-node {
  display: flex; align-items: center; gap: 6px; padding: 7px 12px;
  margin: 0 8px 1px; border-radius: 6px; cursor: pointer; transition: background .1s; font-size: 13px;
}
.tree-node:hover { background: #f5f7fa; }
.tree-node:hover .node-actions { opacity: 1; }
.tree-node.active { background: #ecf5ff; color: #409EFF; font-weight: 500; }
.expand { width: 16px; text-align: center; font-size: 10px; color: #909399; flex-shrink: 0; user-select: none; }
.node-icon { width: 18px; text-align: center; flex-shrink: 0; font-size: 12px; }
.node-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.node-actions { display: flex; gap: 1px; opacity: 0; transition: opacity .1s; }
.act {
  width: 20px; height: 20px; border-radius: 4px; border: none; background: transparent;
  cursor: pointer; font-size: 12px; display: flex; align-items: center; justify-content: center;
  color: #909399; font-family: inherit;
}
.act:hover { background: #e4e7ed; color: #303133; }
</style>
