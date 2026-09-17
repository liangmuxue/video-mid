<template>
  <AppShell>
    <section class="head">
      <div>
        <h1>业务端</h1>
        <p>目录下勾选设备，右侧可同时观看多路直播；已停用设备灰色不可播。</p>
      </div>
    </section>

    <p v-if="error" class="error">{{ error }}</p>

    <div class="layout">
      <aside class="tree-panel">
        <div class="tree-head">
          <strong>设备目录</strong>
          <button type="button" class="ghost sm" :disabled="loading" @click="reloadAll">刷新</button>
        </div>
        <input v-model.trim="keyword" class="search" placeholder="搜索设备" />

        <div v-if="loading" class="tree-empty">加载中…</div>
        <template v-else>
          <BizFolderDeviceTree
            v-if="deviceTree.length"
            :nodes="deviceTree"
            :keyword="keyword"
            :checked-ids="checkedIds"
            :loading-ids="loadingLiveIds"
            :open-folder-ids="openFolderIds"
            @toggle-folder="toggleFolder"
            @toggle-check="onToggleCheck"
            @playback="openPlayback"
          />
          <section v-if="unassignedDevices.length" class="unassigned">
            <div class="folder-row unassigned-head">
              <span class="folder-name">未分类</span>
              <span class="cnt">({{ unassignedDevices.length }})</span>
            </div>
            <div
              v-for="d in filterDevices(unassignedDevices)"
              :key="d.deviceId"
              class="device-row"
              :class="{ disabled: !d.livePlayable }"
              style="padding-left: 30px"
            >
              <label class="dev-check" :title="d.livePlayable ? '勾选后在右侧显示直播' : '不可直播'">
                <input
                  type="checkbox"
                  :checked="checkedIds.includes(d.deviceId)"
                  :disabled="!d.livePlayable || loadingLiveIds.includes(d.deviceId)"
                  @change="onToggleCheck({ device: d, checked: $event.target.checked })"
                />
              </label>
              <div class="dev-info">
                <span class="dev-name">{{ d.name || d.deviceId }}</span>
                <span class="mono sub">{{ d.deviceId }}</span>
                <span class="badge" :class="deviceStatusClass(d.status)">{{ deviceStatusLabel(d.status) }}</span>
              </div>
              <button v-if="d.playable" type="button" class="link" @click="openPlayback(d)">回放</button>
            </div>
          </section>
          <p v-if="!deviceTree.length && !unassignedDevices.length" class="tree-empty">暂无设备</p>
        </template>
      </aside>

      <div class="live-panel">
        <header class="live-head">
          <h2>直播</h2>
          <span class="live-count">{{ liveSessions.length }} 路</span>
          <button
            v-if="checkedIds.length"
            type="button"
            class="ghost sm"
            @click="clearAllLive"
          >全部关闭</button>
        </header>

        <div v-if="!liveSessions.length" class="live-empty">
          在左侧目录勾选设备，此处显示直播画面（可多选）
        </div>

        <div v-else class="live-grid">
          <article v-for="s in liveSessions" :key="s.deviceId" class="live-card">
            <header class="live-card-head">
              <div>
                <strong>{{ s.name || s.deviceId }}</strong>
                <p class="mono muted">{{ s.deviceId }} · {{ s.streamType }}</p>
              </div>
              <button type="button" class="ghost sm" @click="uncheckDevice(s.deviceId)">关闭</button>
            </header>
            <StreamPlayer :url="s.streamUrl" />
          </article>
        </div>
      </div>
    </div>

    <div v-if="playbackDevice" class="mask" @click.self="playbackDevice = null">
      <div class="modal playback-modal" @click.stop>
        <RecordingPlaybackPanel
          :device-id="playbackDevice.deviceId"
          :device-name="playbackDevice.name || ''"
          :fetch-recordings="bizFetchRecordings"
          :fetch-recording-days="bizFetchRecordingDays"
          :get-video-url="bizGetVideoUrl"
          @close="playbackDevice = null"
        />
      </div>
    </div>
  </AppShell>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import AppShell from '../components/AppShell.vue'
import BizFolderDeviceTree from '../components/BizFolderDeviceTree.vue'
import StreamPlayer from '../components/StreamPlayer.vue'
import RecordingPlaybackPanel from '../components/RecordingPlaybackPanel.vue'
import {
  fetchBizDevices,
  fetchBizFolderTree,
  fetchBizRecordingDays,
  fetchBizRecordings,
  startBizLive
} from '../api/device'
import { statusClass as deviceStatusClass, statusLabel as deviceStatusLabel } from '../utils/deviceStatus'

const folderTree = ref([])
const devices = ref([])
const deviceTree = ref([])
const unassignedDevices = ref([])
const loading = ref(false)
const error = ref('')
const keyword = ref('')
const checkedIds = ref([])
const loadingLiveIds = ref([])
const liveSessions = ref([])
const openFolderIds = reactive({})
const playbackDevice = ref(null)

function filterDevices(list) {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return list || []
  return (list || []).filter((d) =>
    [d.deviceId, d.name, d.address].filter(Boolean).join(' ').toLowerCase().includes(q)
  )
}

function buildDeviceTree(folders, allDevices) {
  const byFolder = new Map()
  const unassigned = []
  for (const d of allDevices) {
    if (d.folderId != null && d.folderId !== '') {
      const fid = Number(d.folderId)
      if (!byFolder.has(fid)) byFolder.set(fid, [])
      byFolder.get(fid).push(d)
    } else {
      unassigned.push(d)
    }
  }
  const sortDev = (a, b) => String(a.name || a.deviceId).localeCompare(String(b.name || b.deviceId))
  for (const arr of byFolder.values()) arr.sort(sortDev)
  unassigned.sort(sortDev)

  function enrich(nodes) {
    return (nodes || []).map((n) => ({
      ...n,
      devices: byFolder.get(n.id) || [],
      children: enrich(n.children)
    }))
  }

  return { tree: enrich(folders), unassigned }
}

function initOpenFolders(nodes) {
  for (const n of nodes || []) {
    if (openFolderIds[n.id] === undefined) openFolderIds[n.id] = true
    initOpenFolders(n.children)
  }
}

function toggleFolder(id) {
  openFolderIds[id] = openFolderIds[id] === false
}

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    const [folders, devs] = await Promise.all([
      fetchBizFolderTree(),
      fetchBizDevices({})
    ])
    folderTree.value = folders || []
    devices.value = devs || []
    const built = buildDeviceTree(folderTree.value, devices.value)
    deviceTree.value = built.tree
    unassignedDevices.value = built.unassigned
    initOpenFolders(deviceTree.value)
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function reloadAll() {
  await loadAll()
}

async function onToggleCheck({ device, checked }) {
  if (!device?.deviceId) return
  if (checked) {
    if (!device.livePlayable || checkedIds.value.includes(device.deviceId)) return
    loadingLiveIds.value = [...loadingLiveIds.value, device.deviceId]
    error.value = ''
    try {
      const live = await startBizLive(device.deviceId)
      checkedIds.value = [...checkedIds.value, device.deviceId]
      liveSessions.value = [
        ...liveSessions.value.filter((s) => s.deviceId !== device.deviceId),
        {
          deviceId: device.deviceId,
          name: device.name,
          streamType: live.streamType,
          streamUrl: live.playUrl || live.streamUrl
        }
      ]
    } catch (e) {
      error.value = e.message || `开启直播失败：${device.deviceId}`
    } finally {
      loadingLiveIds.value = loadingLiveIds.value.filter((id) => id !== device.deviceId)
    }
  } else {
    uncheckDevice(device.deviceId)
  }
}

function uncheckDevice(deviceId) {
  checkedIds.value = checkedIds.value.filter((id) => id !== deviceId)
  liveSessions.value = liveSessions.value.filter((s) => s.deviceId !== deviceId)
}

function clearAllLive() {
  checkedIds.value = []
  liveSessions.value = []
}

function openPlayback(d) {
  playbackDevice.value = d
}

function bizFetchRecordings(id, params) {
  return fetchBizRecordings(id, params)
}

function bizFetchRecordingDays(id, params) {
  return fetchBizRecordingDays(id, params)
}

function bizGetVideoUrl(id, fileName, record) {
  return record?.videoUrl || ''
}

onMounted(loadAll)
</script>

<style scoped>
.head { margin-bottom: 18px; }
h1 { margin: 0; font-family: Syne, sans-serif; font-size: 32px; }
.head p { margin: 8px 0 0; color: var(--muted); }
.layout {
  display: grid;
  grid-template-columns: minmax(280px, 360px) minmax(0, 1fr);
  gap: 14px; align-items: start;
}
@media (max-width: 960px) { .layout { grid-template-columns: 1fr; } }

.tree-panel, .live-panel {
  border: 1px solid var(--line); border-radius: 16px; background: var(--panel);
}
.tree-panel { padding: 12px; max-height: calc(100vh - 160px); overflow: auto; }
.tree-head {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 10px; font-size: 14px;
}
.search {
  width: 100%; box-sizing: border-box; margin-bottom: 10px;
  border: 1px solid var(--line); background: rgba(8,16,13,.65);
  color: var(--text); border-radius: 10px; padding: 8px 10px; font-size: 13px;
}
.tree-empty { color: var(--muted); font-size: 13px; padding: 12px 4px; }
.unassigned { margin-top: 12px; border-top: 1px solid var(--line); padding-top: 8px; }
.unassigned-head { padding: 6px 8px; }
.folder-row { display: flex; align-items: center; gap: 6px; font-size: 13px; }
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
.dev-info { flex: 1; min-width: 0; display: flex; flex-wrap: wrap; align-items: center; gap: 6px; }
.dev-name { font-weight: 500; font-size: 13px; }
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
  cursor: pointer; font-size: 12px; flex-shrink: 0;
}

.live-panel { padding: 16px; min-height: 420px; }
.live-head {
  display: flex; align-items: center; gap: 12px; margin-bottom: 14px;
}
.live-head h2 { margin: 0; font-family: Syne, sans-serif; font-size: 20px; }
.live-count { color: var(--muted); font-size: 13px; }
.live-empty {
  display: grid; place-items: center; min-height: 280px;
  color: var(--muted); font-size: 14px; text-align: center; padding: 24px;
}
.live-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 14px;
}
.live-card {
  border: 1px solid var(--line); border-radius: 14px;
  padding: 12px; background: rgba(8, 16, 13, 0.35);
}
.live-card-head {
  display: flex; justify-content: space-between; align-items: flex-start;
  gap: 8px; margin-bottom: 10px;
}
.live-card-head strong { font-size: 14px; }
.muted { color: var(--muted); font-size: 11px; margin: 2px 0 0; }

.primary, .ghost, .link { cursor: pointer; }
.ghost {
  border: 1px solid var(--line); background: transparent; color: var(--text);
  border-radius: 10px; padding: 8px 12px;
}
.ghost.sm { padding: 4px 10px; font-size: 12px; }
.ghost:disabled { opacity: 0.45; cursor: not-allowed; }
.error { color: var(--danger); }
.mask {
  position: fixed; inset: 0; background: rgba(0,0,0,.55);
  display: grid; place-items: center; padding: 20px; z-index: 40;
}
.playback-modal {
  width: min(960px, 100%); max-height: min(92vh, 960px); overflow: auto;
  background: #102019; border: 1px solid var(--line); border-radius: 18px; padding: 16px 18px;
}
</style>
