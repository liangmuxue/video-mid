<template>
  <li class="tree-node">
    <div class="tree-row" :class="{ active: selectedId === node.id }" :style="{ paddingLeft: pad + 'px' }">
      <button type="button" class="tree-toggle" @click.stop="open = !open">
        {{ hasChildren ? (open ? '▾' : '▸') : '·' }}
      </button>
      <button type="button" class="tree-name" @click="$emit('select', node.id)">
        {{ node.name }}
        <span class="cnt">({{ node.totalDeviceCount ?? node.deviceCount ?? 0 }})</span>
      </button>
      <span v-if="!readonly" class="tree-ops">
        <button type="button" class="link" title="在此下新建" @click.stop="$emit('add', node)">＋</button>
        <button type="button" class="link" title="编辑" @click.stop="$emit('edit', node)">改</button>
        <button type="button" class="link danger" title="删除" @click.stop="$emit('remove', node)">删</button>
      </span>
    </div>
    <ul v-if="open && hasChildren" class="tree">
      <DeviceFolderNode
        v-for="c in node.children"
        :key="c.id"
        :node="c"
        :selected-id="selectedId"
        :depth="depth + 1"
        :readonly="readonly"
        @select="$emit('select', $event)"
        @add="$emit('add', $event)"
        @edit="$emit('edit', $event)"
        @remove="$emit('remove', $event)"
      />
    </ul>
  </li>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  node: { type: Object, required: true },
  selectedId: { type: [Number, String], default: null },
  depth: { type: Number, default: 0 },
  readonly: { type: Boolean, default: false }
})

defineEmits(['select', 'add', 'edit', 'remove'])

const open = ref(true)
const hasChildren = computed(() => (props.node.children || []).length > 0)
const pad = computed(() => 8 + props.depth * 14)
</script>

<style scoped>
.tree { list-style: none; margin: 0; padding: 0; }
.tree-row {
  display: flex; align-items: center; gap: 4px;
  border-radius: 8px; padding: 4px 6px 4px 0;
}
.tree-row.active { background: rgba(61, 186, 122, 0.14); }
.tree-toggle, .tree-name {
  border: 0; background: transparent; color: var(--text); cursor: pointer; font-size: 13px;
}
.tree-toggle { width: 18px; color: var(--muted); flex-shrink: 0; }
.tree-name { flex: 1; text-align: left; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cnt { color: var(--muted); font-size: 12px; margin-left: 2px; }
.tree-ops { display: flex; gap: 4px; opacity: 0.55; }
.tree-row:hover .tree-ops { opacity: 1; }
.link { border: 0; background: transparent; color: var(--accent-2); cursor: pointer; padding: 0 2px; font-size: 12px; }
.link.danger { color: var(--danger); }
</style>
