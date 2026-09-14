<template>
  <AppShell>
    <section class="head">
      <div>
        <h1>业务端</h1>
        <p>通过目录查看设备；直播与录像回放。已停用设备灰色可见但不可播。</p>
      </div>
    </section>

    <p v-if="error" class="error">{{ error }}</p>

    <div class="layout">
      <aside class="tree-panel">
        <div class="tree-head"><strong>设备目录</strong></div>
        <button
          type="button"
          class="tree-root"
          :class="{ active: selectedFolderId == null }"
          @click="selectFolder(null)"
        >
          全部设备
        </button>
        <ul class="tree">
          <DeviceFolderNode
            v-for="n in folderTree"
            :key="n.id"
            :node="n"
            :selected-id="selectedFolderId"
            :depth="0"
            readonly
            @select="selectFolder"
          />
        </ul>
      </aside>

      <div class="main-panel">
        <section class="toolbar">
          <div class="crumb">{{ currentFolderLabel }}</div>
          <input v-model.trim="keyword" placeholder="搜索设备" />
          <label class="check">
            <input v-model="includeChildren" type="checkbox" @change="loadDevices" />
            含下级
          </label>
          <button type="button" class="ghost" :disabled="loading" @click="reloadAll">刷新</button>
        </section>

        <div class="split">
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>设备</th>
                  <th>状态</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="d in filtered"
                  :key="d.id"
                  :class="{ disabled: !d.playable, active: selectedDeviceId === d.deviceId }"
                  @click="selectDevice(d)"
                >
                  <td>
                    <div class="name">{{ d.name || d.deviceId }}</div>
                    <div class="mono sub">{{ d.deviceId }}</div>
                  </td>
                  <td>
                    <span
                      class="badge"
                      :class="{
                        on: d.status === '已启用',
                        off: d.status === '已停用',
                        unavailable: d.status === '不可用'
                      }"
                    >{{ d.status }}</span>
                  </td>
                  <td class="actions">
                    <button type="button" class="link" @click.stop="selectDevice(d)">查看</button>
                  </td>
                </tr>
                <tr v-if="!filtered.length">
                  <td colspan="3" class="empty">暂无设备</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="detail-panel" v-if="detail">
            <header class="detail-head">
              <div>
                <h2>{{ detail.name || detail.deviceId }}</h2>
                <p class="mono muted">{{ detail.deviceId }} · {{ detail.status }}</p>
              </div>
              <div class="actions">
                <button
                  type="button"
                  class="primary"
                  :disabled="!canLive"
                  @click="onLive"
                >直播</button>
                <button
                  type="button"
                  class="ghost"
                  :disabled="!canPlayback"
                  @click="showPlayback = true"
                >录像回放</button>
              </div>
            </header>
            <p v-if="!detail.playable" class="tip warn">设备已停用：可见但不可播放。</p>
            <p v-else-if="detail.status === '不可用'" class="tip warn">设备当前不可用（未推流），直播可能失败。</p>
            <p v-if="detail.liveStream" class="tip">
              直播码流：{{ detail.liveStream.streamType }} · {{ detail.liveStream.streamName || detail.liveStream.streamUrl }}
            </p>
            <p v-else class="tip">未配置业务直播码流（默认优先 sub）。</p>

            <div v-if="liveInfo" class="live-box">
              <h3>直播 · {{ liveInfo.streamType }}</h3>
              <StreamPlayer :url="liveInfo.streamUrl" />
              <button type="button" class="ghost sm" @click="liveInfo = null">关闭直播</button>
            </div>
          </div>
          <div v-else class="detail-panel empty-panel">请选择左侧设备</div>
        </div>
      </div>
    </div>

    <div v-if="showPlayback && detail" class="mask" @click.self="showPlayback = false">
      <div class="modal playback-modal" @click.stop>
        <RecordingPlaybackPanel
          :device-id="detail.deviceId"
          :device-name="detail.name || ''"
          :fetch-recordings="bizFetchRecordings"
          :get-video-url="bizGetVideoUrl"
          @close="showPlayback = false"
        />
      </div>
    </div>
  </AppShell>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import AppShell from '../components/AppShell.vue'
import DeviceFolderNode from '../components/DeviceFolderNode.vue'
import StreamPlayer from '../components/StreamPlayer.vue'
import RecordingPlaybackPanel from '../components/RecordingPlaybackPanel.vue'
import {
  fetchBizDevice,
  fetchBizDevices,
  fetchBizFolderTree,
  fetchBizRecordings,
  startBizLive
} from '../api/device'

const folderTree = ref([])
const devices = ref([])
const loading = ref(false)
const error = ref('')
const keyword = ref('')
const selectedFolderId = ref(null)
const includeChildren = ref(true)
const selectedDeviceId = ref(null)
const detail = ref(null)
const liveInfo = ref(null)
const showPlayback = ref(false)

const flatFolderOptions = computed(() => {
  const out = []
  const walk = (nodes, prefix) => {
    for (const n of nodes || []) {
      out.push({ id: n.id, label: `${prefix}${n.name}` })
      walk(n.children || [], `${prefix}${n.name} / `)
    }
  }
  walk(folderTree.value, '')
  return out
})

const currentFolderLabel = computed(() => {
  if (selectedFolderId.value == null) return '全部设备'
  const hit = flatFolderOptions.value.find((o) => o.id === selectedFolderId.value)
  return hit ? hit.label : '目录'
})

const filtered = computed(() => {
  const q = keyword.value.toLowerCase()
  if (!q) return devices.value
  return devices.value.filter((d) =>
    [d.deviceId, d.name, d.address].filter(Boolean).join(' ').toLowerCase().includes(q)
  )
})

const canLive = computed(
  () => detail.value?.livePlayable && detail.value?.liveStream?.streamUrl
)
const canPlayback = computed(() => detail.value?.playable)

async function loadFolders() {
  folderTree.value = (await fetchBizFolderTree()) || []
}

async function loadDevices() {
  loading.value = true
  error.value = ''
  try {
    const params = {}
    if (selectedFolderId.value != null) {
      params.folderId = selectedFolderId.value
      params.includeChildren = includeChildren.value
    }
    devices.value = (await fetchBizDevices(params)) || []
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function reloadAll() {
  error.value = ''
  try {
    await loadFolders()
    await loadDevices()
  } catch (e) {
    error.value = e.message || '加载失败'
  }
}

function selectFolder(id) {
  selectedFolderId.value = id
  loadDevices()
}

async function selectDevice(d) {
  selectedDeviceId.value = d.deviceId
  liveInfo.value = null
  showPlayback.value = false
  try {
    detail.value = await fetchBizDevice(d.deviceId)
  } catch (e) {
    error.value = e.message || '加载设备失败'
    detail.value = null
  }
}

async function onLive() {
  if (!detail.value?.deviceId) return
  try {
    liveInfo.value = await startBizLive(detail.value.deviceId)
  } catch (e) {
    error.value = e.message || '开启直播失败'
  }
}

function bizFetchRecordings(id, params) {
  return fetchBizRecordings(id, params)
}

function bizGetVideoUrl(id, fileName, record) {
  return record?.videoUrl || ''
}

onMounted(reloadAll)
</script>

<style scoped>
.head { margin-bottom: 18px; }
h1 { margin: 0; font-family: Syne, sans-serif; font-size: 32px; }
.head p { margin: 8px 0 0; color: var(--muted); }
.layout { display: grid; grid-template-columns: 240px minmax(0, 1fr); gap: 14px; }
@media (max-width: 960px) { .layout { grid-template-columns: 1fr; } }
.tree-panel, .table-wrap, .detail-panel {
  border: 1px solid var(--line); border-radius: 16px; background: var(--panel);
}
.tree-panel { padding: 12px; min-height: 420px; }
.tree-head { margin-bottom: 10px; font-size: 14px; }
.tree-root {
  width: 100%; text-align: left; border: 0; border-radius: 8px;
  background: transparent; color: var(--text); padding: 8px 10px; cursor: pointer;
}
.tree-root.active { background: rgba(61, 186, 122, 0.14); }
.tree { list-style: none; margin: 0; padding: 0; }
.toolbar { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 12px; align-items: center; }
.crumb { font-size: 13px; color: var(--muted); }
.toolbar input {
  flex: 1; min-width: 140px; border: 1px solid var(--line); background: rgba(8,16,13,.65);
  color: var(--text); border-radius: 12px; padding: 10px 12px;
}
.check { display: flex; gap: 6px; align-items: center; color: var(--muted); font-size: 13px; }
.split { display: grid; grid-template-columns: minmax(240px, 340px) minmax(0, 1fr); gap: 12px; }
@media (max-width: 1100px) { .split { grid-template-columns: 1fr; } }
.table-wrap { overflow: auto; max-height: 70vh; }
table { width: 100%; border-collapse: collapse; }
th, td { text-align: left; padding: 12px; border-bottom: 1px solid var(--line); }
th { color: var(--muted); font-size: 13px; font-weight: 500; }
tbody tr { cursor: pointer; }
tbody tr:hover { background: rgba(61,186,122,.06); }
tbody tr.active { background: rgba(61,186,122,.12); }
tbody tr.disabled { opacity: 0.55; }
.name { font-weight: 600; }
.sub { font-size: 12px; color: var(--muted); margin-top: 2px; }
.mono { font-family: ui-monospace, Menlo, Consolas, monospace; }
.badge { display: inline-block; padding: 2px 8px; border-radius: 999px; font-size: 12px; border: 1px solid var(--line); }
.badge.on { color: var(--accent-2); }
.badge.off { color: var(--muted); }
.badge.unavailable { color: var(--danger); }
.detail-panel { padding: 16px; min-height: 320px; }
.empty-panel { display: grid; place-items: center; color: var(--muted); }
.detail-head { display: flex; justify-content: space-between; gap: 12px; align-items: flex-start; }
.detail-head h2 { margin: 0; font-family: Syne, sans-serif; }
.muted { color: var(--muted); }
.tip { font-size: 13px; color: var(--muted); }
.tip.warn { color: #e0b06a; }
.live-box { margin-top: 14px; display: grid; gap: 10px; }
.actions { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }
.primary, .ghost, .link { cursor: pointer; }
.primary { border: 0; border-radius: 12px; padding: 10px 14px; background: linear-gradient(135deg, var(--accent), #2f9a65); color: #04140c; font-weight: 600; }
.primary:disabled { opacity: 0.45; cursor: not-allowed; }
.ghost { border: 1px solid var(--line); background: transparent; color: var(--text); border-radius: 12px; padding: 10px 14px; }
.ghost:disabled { opacity: 0.45; cursor: not-allowed; }
.ghost.sm { padding: 6px 10px; font-size: 12px; }
.link { border: 0; background: transparent; color: var(--accent-2); padding: 0; }
.error { color: var(--danger); }
.empty { color: var(--muted); text-align: center; }
.mask { position: fixed; inset: 0; background: rgba(0,0,0,.55); display: grid; place-items: center; padding: 20px; z-index: 40; }
.playback-modal {
  width: min(960px, 100%); max-height: min(92vh, 960px); overflow: auto;
  background: #102019; border: 1px solid var(--line); border-radius: 18px; padding: 16px 18px;
}
</style>
