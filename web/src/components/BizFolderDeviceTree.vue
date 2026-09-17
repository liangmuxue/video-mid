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
        <div
          v-for="d in filterDevices(n.devices)"
          :key="'d-' + d.deviceId"
          class="device-row"
          :class="{ disabled: !d.livePlayable }"
          :style="{ paddingLeft: pad + 22 + 'px' }"
        >
          <label class="dev-check" :title="checkTitle(d)">
            <input
              type="checkbox"
              :checked="checkedSet.has(d.deviceId)"
              :disabled="!d.livePlayable || loadingSet.has(d.deviceId)"
              @change="$emit('toggle-check', { device: d, checked: $event.target.checked })"
            />
          </label>
          <div class="dev-info">
            <span class="dev-name">{{ d.name || d.deviceId }}</span>
            <span class="mono sub">{{ d.deviceId }}</span>
            <span class="badge" :class="statusClass(d.status)">{{ statusLabel(d.status) }}</span>
          </div>
          <button
            v-if="d.playable"
            type="button"
            class="link"
            @click.stop="$emit('playback', d)"
          >回放</button>
        </div>
        <BizFolderDeviceTree
          v-if="n.children?.length"
          :nodes="n.children"
          :depth="depth + 1"
          :keyword="keyword"
          :checked-ids="checkedIds"
          :loading-ids="loadingIds"
          :open-folder-ids="openFolderIds"
          @toggle-folder="$emit('toggle-folder', $event)"
          @toggle-check="$emit('toggle-check', $event)"
          @playback="$emit('playback', $event)"
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
  checkedIds: { type: Array, default: () => [] },
  loadingIds: { type: Array, default: () => [] },
  openFolderIds: { type: Object, default: () => ({}) }
})

defineEmits(['toggle-folder', 'toggle-check', 'playback'])

const pad = computed(() => 8 + props.depth * 14)
const checkedSet = computed(() => new Set(props.checkedIds))
const loadingSet = computed(() => new Set(props.loadingIds))

function folderOpen(id) {
  return props.openFolderIds[id] !== false
}

function filterDevices(list) {
  const q = props.keyword.trim().toLowerCase()
  if (!q) return list || []
  return (list || []).filter((d) =>
    [d.deviceId, d.name, d.address].filter(Boolean).join(' ').toLowerCase().includes(q)
  )
}

function checkTitle(d) {
  if (!d.livePlayable) return '已停用或不可用，无法直播'
  return '勾选后在右侧显示直播'
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
  display: flex; align-items: center; gap: 8px;
  padding: 8px 8px 8px 0; border-radius: 8px;
}
.device-row:hover { background: rgba(61, 186, 122, 0.06); }
.device-row.disabled { opacity: 0.55; }
.dev-check { display: flex; align-items: center; cursor: pointer; flex-shrink: 0; }
.dev-check input { width: 16px; height: 16px; cursor: pointer; }
.dev-check input:disabled { cursor: not-allowed; }
.dev-info { flex: 1; min-width: 0; display: flex; flex-wrap: wrap; align-items: center; gap: 6px; }
.dev-name { font-weight: 500; }
.sub { font-size: 11px; color: var(--muted); }
.mono { font-family: ui-monospace, Menlo, Consolas, monospace; }
.badge {
  display: inline-block; padding: 1px 6px; border-radius: 999px;
  font-size: 11px; border: 1px solid var(--line);
}
.badge.on { color: var(--accent-2); }
.badge.off { color: var(--muted); }
.badge.unavailable { color: var(--danger); }
.link {
  border: 0; background: transparent; color: var(--accent-2);
  cursor: pointer; font-size: 12px; flex-shrink: 0; padding: 0 4px;
}
</style>
