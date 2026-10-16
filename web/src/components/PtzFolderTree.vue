<template>
  <ul class="tree">
    <li v-for="n in nodes" :key="'f-' + n.id" class="tree-node">
      <div class="folder-row" :style="{ paddingLeft: pad + 'px' }">
        <button type="button" class="toggle" @click="$emit('toggle-folder', n.id)">
          {{ folderOpen(n.id) ? '▾' : '▸' }}
        </button>
        <span class="folder-name">{{ n.name }}</span>
        <span class="cnt">({{ n.totalDeviceCount ?? n.deviceCount ?? 0 }})</span>
      </div>
      <template v-if="folderOpen(n.id)">
        <button
          v-for="d in filterDevices(n.devices)"
          :key="'d-' + d.deviceId"
          type="button"
          class="device-row"
          :class="{ active: selectedId === d.deviceId, disabled: !d.livePlayable }"
          :style="{ paddingLeft: pad + 22 + 'px' }"
          :disabled="!d.livePlayable"
          :title="d.livePlayable ? '点击后在右侧打开这一台' : '已停用或不可用'"
          @click="$emit('select', d)"
        >
          <span class="dev-name">{{ d.name || d.deviceId }}</span>
          <span class="mono sub">{{ d.deviceId }}</span>
          <span v-if="d.vendorLabel" class="vendor">{{ d.vendorLabel }}</span>
          <span class="badge" :class="statusClass(d.status)">{{ statusLabel(d.status) }}</span>
        </button>
        <PtzFolderTree
          v-if="n.children?.length"
          :nodes="n.children"
          :depth="depth + 1"
          :keyword="keyword"
          :selected-id="selectedId"
          :open-folder-ids="openFolderIds"
          @toggle-folder="$emit('toggle-folder', $event)"
          @select="$emit('select', $event)"
        />
      </template>
    </li>
  </ul>
</template>

<script setup>
import { computed } from 'vue'
import { statusClass, statusLabel } from '../utils/deviceStatus'

const props = defineProps({
  nodes: { type: Array, default: () => [] },
  depth: { type: Number, default: 0 },
  keyword: { type: String, default: '' },
  selectedId: { type: String, default: '' },
  openFolderIds: { type: Object, default: () => ({}) }
})

defineEmits(['toggle-folder', 'select'])

const pad = computed(() => 8 + props.depth * 14)

function folderOpen(id) {
  return props.openFolderIds[id] !== false
}

function filterDevices(list) {
  const q = props.keyword.trim().toLowerCase()
  if (!q) return list || []
  return (list || []).filter((d) =>
    [d.deviceId, d.name, d.address, d.vendorLabel].filter(Boolean).join(' ').toLowerCase().includes(q)
  )
}
</script>

<style scoped>
.tree { list-style: none; margin: 0; padding: 0; }
.folder-row {
  display: flex; align-items: center; gap: 4px;
  padding: 6px 8px 6px 0; font-size: 13px;
}
.toggle {
  width: 18px; border: 0; background: transparent;
  color: var(--muted); cursor: pointer; flex-shrink: 0;
}
.folder-name { font-weight: 600; }
.cnt { color: var(--muted); font-size: 12px; }
.device-row {
  width: 100%; box-sizing: border-box; text-align: left;
  display: flex; flex-wrap: wrap; align-items: center; gap: 6px;
  padding: 8px 8px 8px 0; border: 0; border-radius: 8px;
  background: transparent; color: var(--text); cursor: pointer;
}
.device-row:hover { background: rgba(61, 186, 122, 0.06); }
.device-row.active { background: rgba(61, 186, 122, 0.16); }
.device-row.disabled,
.device-row:disabled { opacity: 0.55; cursor: not-allowed; }
.dev-name { font-weight: 500; font-size: 13px; }
.sub { font-size: 11px; color: var(--muted); }
.mono { font-family: ui-monospace, Menlo, Consolas, monospace; }
.vendor {
  font-size: 11px; color: var(--accent-2);
  border: 1px solid var(--line); border-radius: 999px; padding: 0 6px;
}
.badge {
  display: inline-block; padding: 1px 6px; border-radius: 999px;
  font-size: 11px; border: 1px solid var(--line);
}
.badge.on { color: var(--accent-2); }
.badge.off { color: var(--muted); }
.badge.unavailable { color: var(--danger); }
</style>
