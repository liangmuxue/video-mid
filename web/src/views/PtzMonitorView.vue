<template>
  <AppShell>
    <section class="ptz-page">
      <header class="ptz-head">
        <div>
          <h1>云台监控</h1>
          <p class="sub">点击左侧一台设备，右侧只打开这一路。云台仍走同一组接口，按设备来源分别控制。</p>
        </div>
      </header>

      <p v-if="error" class="error">{{ error }}</p>

      <div class="layout">
        <aside class="tree-panel">
          <div class="tree-head">
            <strong>设备目录</strong>
            <button type="button" class="ghost sm" :disabled="loading" @click="loadAll">刷新</button>
          </div>
          <input v-model.trim="keyword" class="search" placeholder="搜索设备" />
          <div v-if="loading" class="tree-empty">加载设备…</div>
          <template v-else>
            <PtzFolderTree
              v-if="deviceTree.length"
              :nodes="deviceTree"
              :keyword="keyword"
              :selected-id="activeId"
              :open-folder-ids="openFolderIds"
              @toggle-folder="toggleFolder"
              @select="selectDevice"
            />
            <section v-if="filteredUnassigned.length" class="unassigned">
              <div class="folder-row">
                <span class="folder-name">未分类</span>
                <span class="cnt">({{ unassignedDevices.length }})</span>
              </div>
              <button
                v-for="d in filteredUnassigned"
                :key="d.deviceId"
                type="button"
                class="device-row"
                :class="{ active: activeId === d.deviceId, disabled: !d.livePlayable }"
                :disabled="!d.livePlayable"
                @click="selectDevice(d)"
              >
                <span class="dev-name">{{ d.name || d.deviceId }}</span>
                <span class="mono sub">{{ d.deviceId }}</span>
                <span v-if="d.vendorLabel" class="vendor">{{ d.vendorLabel }}</span>
                <span class="badge" :class="statusClass(d.status)">{{ statusLabel(d.status) }}</span>
              </button>
            </section>
            <p v-if="!deviceTree.length && !unassignedDevices.length" class="tree-empty">暂无设备</p>
          </template>
        </aside>

        <div class="stage">
          <div v-if="!activeDevice" class="stage-empty">点击左侧设备，此处显示这一台的画面和云台</div>
          <div v-else class="ptz-grid">
            <div class="main-col">
              <div class="panel">
                <div class="panel-title">
                  <span>
                    {{ activeDevice.name || activeDevice.deviceId }}
                    <em v-if="activeDevice.vendorLabel" class="vendor">{{ activeDevice.vendorLabel }}</em>
                  </span>
                  <button v-if="playedUrl" type="button" class="ghost" @click="captureMain">抓拍</button>
                </div>
                <p v-if="opening" class="muted">正在打开…</p>
                <StreamPlayer v-else-if="playedUrl" ref="mainPlayerRef" :url="playedUrl" />
                <p v-else class="muted">没有可播放的画面</p>
              </div>
              <div v-if="thermalUrl" class="panel sub-panel">
                <div class="panel-title"><span>热成像</span></div>
                <StreamPlayer :url="thermalUrl" />
              </div>
            </div>

            <aside class="side-col">
              <div v-if="ptzCapable" class="panel">
                <h3>云台控制</h3>
                <div class="pad">
                  <button type="button" @pointerdown="(e) => holdMove('up', e)" @pointerup="stopHold" @pointercancel="stopHold">上</button>
                  <div class="pad-mid">
                    <button type="button" @pointerdown="(e) => holdMove('left', e)" @pointerup="stopHold" @pointercancel="stopHold">左</button>
                    <span class="pad-center">●</span>
                    <button type="button" @pointerdown="(e) => holdMove('right', e)" @pointerup="stopHold" @pointercancel="stopHold">右</button>
                  </div>
                  <button type="button" @pointerdown="(e) => holdMove('down', e)" @pointerup="stopHold" @pointercancel="stopHold">下</button>
                </div>
                <div class="btn-row">
                  <button type="button" @click="doZoom('in')">变倍+</button>
                  <button type="button" @click="doZoom('out')">变倍−</button>
                  <button type="button" @click="doWideAngle">一键广角</button>
                </div>
                <div class="btn-row">
                  <button type="button" @click="doFocus('near')">近焦</button>
                  <button type="button" @click="doFocus('far')">远焦</button>
                </div>
                <p v-if="actionMsg" class="action-msg">{{ actionMsg }}</p>
              </div>
              <div v-else class="panel">
                <h3>云台控制</h3>
                <p class="muted">该设备来源不支持云台。</p>
              </div>

              <div v-if="ptzCapable" class="panel">
                <h3>预置位</h3>
                <div class="preset-form">
                  <input v-model.number="presetForm.index" type="number" min="1" max="1024" placeholder="编号" />
                  <input v-model.trim="presetForm.name" type="text" placeholder="名称" />
                  <label class="chk"><input v-model="presetForm.overwrite" type="checkbox" />覆盖</label>
                  <button type="button" class="primary" @click="savePreset">保存预置位</button>
                </div>
                <ul class="preset-list">
                  <li v-for="p in presets" :key="p.index">
                    <span class="preset-name">{{ p.index }} · {{ p.name }}</span>
                    <button type="button" class="link" @click="gotoPreset(p.index)">调用</button>
                  </li>
                  <li v-if="!presets.length" class="muted">暂无预置位</li>
                </ul>
              </div>
            </aside>
          </div>
        </div>
      </div>
    </section>

    <div v-if="snapshotOpen" class="mask" @mousedown.self="onSnapBackdropDown" @mouseup.self="onSnapBackdropUp">
      <div class="snap-modal" @mousedown.stop @mouseup="cancelSnapBackdrop">
        <h2>抓拍预览</h2>
        <img v-if="snapshotDataUrl" :src="snapshotDataUrl" alt="snapshot" class="snap-img" />
        <div class="snap-actions">
          <button type="button" class="primary" @click="downloadSnapshot(false)">保存</button>
          <button type="button" @click="downloadSnapshot(true)">另存为</button>
          <button type="button" class="ghost" @click="snapshotOpen = false">关闭</button>
        </div>
      </div>
    </div>
  </AppShell>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import AppShell from '../components/AppShell.vue'
import PtzFolderTree from '../components/PtzFolderTree.vue'
import StreamPlayer from '../components/StreamPlayer.vue'
import { useBackdropClose } from '../composables/useBackdropClose'
import { fetchBizDevices, fetchBizFolderTree, fetchDeviceByDeviceId, previewStart } from '../api/device'
import {
  fetchUniviewDevice,
  ptzFocus,
  ptzGotoPreset,
  ptzMove,
  ptzSetPreset,
  ptzWideAngle,
  ptzZoom
} from '../api/uniview'
import { stopUniviewCloudLive } from '../api/univiewCloud'
import { isEnabled, statusClass, statusLabel } from '../utils/deviceStatus'

const PTZ_VENDORS = new Set(['MOCK', 'UNIVIEW', 'UNIVIEW_CLOUD', 'EAGLE_EYE'])

const loading = ref(true)
const opening = ref(false)
const error = ref('')
const keyword = ref('')
const deviceTree = ref([])
const unassignedDevices = ref([])
const activeId = ref('')
const activeDevice = ref(null)
const detail = ref(null)
const presets = ref([])
const actionMsg = ref('')
const playedUrl = ref('')
const mainPlayerRef = ref(null)
const openFolderIds = reactive({})
let openTicket = 0

const snapshotOpen = ref(false)
const snapshotDataUrl = ref('')
const snapshotFileName = ref('ptz-snapshot.jpg')
const presetForm = reactive({ index: 1, name: '', overwrite: false })

const {
  onBackdropDown: onSnapBackdropDown,
  onBackdropUp: onSnapBackdropUp,
  cancelBackdrop: cancelSnapBackdrop
} = useBackdropClose(() => {
  snapshotOpen.value = false
})

const ptzCapable = computed(() => PTZ_VENDORS.has(activeDevice.value?.vendor))
const filteredUnassigned = computed(() => filterDevices(unassignedDevices.value))

function streamUrl(channelType, streamType = 'sub') {
  const streams = detail.value?.streams || []
  const hit =
    streams.find((s) => s.channelType === channelType && s.streamType === streamType) ||
    streams.find((s) => s.channelType === channelType)
  return hit?.streamUrl || ''
}

const thermalUrl = computed(() => streamUrl('thermal', 'main') || streamUrl('thermal', 'sub'))

let holdTimer = null
let holding = false

function vendorLabel(vendor) {
  switch (vendor) {
    case 'UNIVIEW_CLOUD': return '宇视云'
    case 'UNIVIEW': return '宇视'
    case 'MOCK': return '模拟'
    case 'GB28181': return '国标'
    case 'RTSP': return 'RTSP'
    case 'HIKVISION': return '海康'
    case 'EAGLE_EYE': return '鹰眼'
    default: return ''
  }
}

function decorate(device) {
  return {
    ...device,
    vendorLabel: vendorLabel(device.vendor),
    livePlayable: isEnabled(device.status)
  }
}

function filterDevices(list) {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return list || []
  return (list || []).filter((d) =>
    [d.deviceId, d.name, d.address, d.vendorLabel].filter(Boolean).join(' ').toLowerCase().includes(q)
  )
}

function buildDeviceTree(folders, allDevices) {
  const byFolder = new Map()
  const unassigned = []
  for (const raw of allDevices) {
    const d = decorate(raw)
    if (d.folderId != null && d.folderId !== '') {
      const fid = Number(d.folderId)
      if (!byFolder.has(fid)) byFolder.set(fid, [])
      byFolder.get(fid).push(d)
    } else {
      unassigned.push(d)
    }
  }
  const sortDev = (a, b) => String(a.name || a.deviceId).localeCompare(String(b.name || b.deviceId), 'zh')
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

function releaseCloud(device) {
  if (device?.vendor === 'UNIVIEW_CLOUD' && device.deviceId) {
    stopUniviewCloudLive(device.deviceId).catch(() => {})
  }
}

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    const [folders, devices] = await Promise.all([
      fetchBizFolderTree(),
      fetchBizDevices({})
    ])
    const built = buildDeviceTree(folders || [], devices || [])
    deviceTree.value = built.tree
    unassignedDevices.value = built.unassigned
    initOpenFolders(deviceTree.value)
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function preferredStreamType(device, info) {
  if (device.vendor === 'UNIVIEW_CLOUD') return 'main'
  const streams = info?.streams || []
  const prefer = streams.find((s) => s.streamType === 'sub')
    || streams.find((s) => s.streamType === 'main')
    || streams[0]
  return prefer?.streamType || 'sub'
}

async function selectDevice(device) {
  if (!device?.livePlayable || !device.deviceId) return
  if (activeId.value === device.deviceId && playedUrl.value) return
  const previous = activeDevice.value
  if (previous && previous.deviceId !== device.deviceId) releaseCloud(previous)
  const mine = ++openTicket
  activeId.value = device.deviceId
  activeDevice.value = device
  detail.value = null
  presets.value = []
  playedUrl.value = ''
  actionMsg.value = ''
  error.value = ''
  opening.value = true
  let chosen = device
  try {
    const full = await fetchDeviceByDeviceId(device.deviceId)
    if (mine !== openTicket) return
    chosen = {
      ...device,
      vendor: full.vendor || '',
      vendorLabel: vendorLabel(full.vendor)
    }
    activeDevice.value = chosen
  } catch (e) {
    if (mine !== openTicket) return
    error.value = e.message || '读取设备失败'
    opening.value = false
    return
  }
  try {
    let info = null
    if (PTZ_VENDORS.has(chosen.vendor)) {
      try {
        info = await fetchUniviewDevice(chosen.deviceId, { presets: true })
        if (mine !== openTicket) return
        detail.value = info
        presets.value = info?.presets || []
      } catch (e) {
        if (mine !== openTicket) return
        actionMsg.value = e.message || '预置位加载失败'
      }
    }
    if (mine !== openTicket) return
    const play = await previewStart(
      { deviceId: chosen.deviceId, streamType: preferredStreamType(chosen, info) },
      { timeout: 30000 }
    )
    if (mine !== openTicket) {
      releaseCloud(chosen)
      return
    }
    playedUrl.value = play.playUrl || play.streamUrl || ''
  } catch (e) {
    if (mine !== openTicket) return
    error.value = e.message || '打开设备失败'
  } finally {
    if (mine === openTicket) opening.value = false
  }
}

async function reloadPresets() {
  if (!activeId.value || !ptzCapable.value) return
  const info = await fetchUniviewDevice(activeId.value, { presets: true })
  presets.value = info?.presets || []
}

async function runAction(label, fn) {
  if (!activeId.value) return
  try {
    await fn()
    actionMsg.value = label + ' 已发送'
  } catch (e) {
    actionMsg.value = e.message || '操作失败'
  }
}

function holdMove(direction, event) {
  const target = event?.currentTarget
  if (target?.setPointerCapture && event.pointerId != null) {
    try {
      target.setPointerCapture(event.pointerId)
    } catch (_) {
      /* ignore */
    }
  }
  if (holdTimer) {
    clearInterval(holdTimer)
    holdTimer = null
  }
  holding = true
  const deviceId = activeId.value
  runAction('云台 ' + direction, () => ptzMove(deviceId, direction))
  holdTimer = setInterval(() => {
    if (activeId.value) ptzMove(activeId.value, direction).catch(() => {})
  }, 400)
}

function stopHold() {
  const wasHolding = holding
  holding = false
  if (holdTimer) {
    clearInterval(holdTimer)
    holdTimer = null
  }
  if (wasHolding && activeId.value) {
    ptzMove(activeId.value, 'stop').catch(() => {})
  }
}

function doZoom(action) {
  runAction('变倍', () => ptzZoom(activeId.value, action))
}

function doFocus(kind) {
  runAction('对焦', () => ptzFocus(activeId.value, kind === 'near' ? 'near' : 'far'))
}

function doWideAngle() {
  runAction('一键广角', () => ptzWideAngle(activeId.value))
}

function gotoPreset(index) {
  runAction('预置位 ' + index, () => ptzGotoPreset(activeId.value, index))
}

async function savePreset() {
  if (!presetForm.name.trim()) {
    actionMsg.value = '请输入预置位名称'
    return
  }
  await runAction('保存预置位', () =>
    ptzSetPreset(activeId.value, presetForm.index, presetForm.name.trim(), presetForm.overwrite)
  )
  try {
    await reloadPresets()
  } catch (e) {
    actionMsg.value = e.message || '预置位已保存，列表刷新失败'
  }
}

function captureMain() {
  const video = mainPlayerRef.value?.getVideoElement?.()
  if (!video || !video.videoWidth) {
    actionMsg.value = '当前无画面，无法抓拍'
    return
  }
  const canvas = document.createElement('canvas')
  canvas.width = video.videoWidth
  canvas.height = video.videoHeight
  canvas.getContext('2d').drawImage(video, 0, 0)
  snapshotDataUrl.value = canvas.toDataURL('image/jpeg', 0.92)
  snapshotFileName.value = `${activeId.value}_${Date.now()}.jpg`
  cancelSnapBackdrop()
  snapshotOpen.value = true
}

function downloadSnapshot(saveAs) {
  const a = document.createElement('a')
  a.href = snapshotDataUrl.value
  a.download = snapshotFileName.value
  if (saveAs) {
    a.download = prompt('文件名', snapshotFileName.value) || snapshotFileName.value
  }
  a.click()
  if (!saveAs) snapshotOpen.value = false
}

onMounted(() => {
  window.addEventListener('pointerup', stopHold)
  loadAll()
})

onUnmounted(() => {
  window.removeEventListener('pointerup', stopHold)
  stopHold()
  openTicket += 1
  releaseCloud(activeDevice.value)
})
</script>

<style scoped>
.ptz-page { display: grid; gap: 16px; }
.ptz-head h1 { margin: 0; font-family: Syne, sans-serif; font-size: 28px; }
.sub { margin: 6px 0 0; color: var(--muted); font-size: 13px; }
.error { color: var(--danger); margin: 0; }
.muted { color: var(--muted); font-size: 13px; }

.layout {
  display: grid;
  grid-template-columns: minmax(280px, 340px) minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}
@media (max-width: 960px) {
  .layout { grid-template-columns: 1fr; }
}

.tree-panel, .stage {
  border: 1px solid var(--line);
  border-radius: 16px;
  background: var(--panel);
}
.tree-panel { padding: 12px; max-height: calc(100vh - 160px); overflow: auto; }
.tree-head {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 10px; font-size: 14px;
}
.search {
  width: 100%; box-sizing: border-box; margin-bottom: 10px;
  border: 1px solid var(--line); background: rgba(8, 16, 13, 0.65);
  color: var(--text); border-radius: 10px; padding: 8px 10px; font-size: 13px;
}
.tree-empty { color: var(--muted); font-size: 13px; padding: 12px 4px; }
.unassigned { margin-top: 12px; border-top: 1px solid var(--line); padding-top: 8px; }
.folder-row { display: flex; align-items: center; gap: 6px; padding: 6px 8px; font-size: 13px; }
.folder-name { font-weight: 600; }
.cnt { color: var(--muted); font-size: 12px; }
.device-row {
  width: 100%; box-sizing: border-box; text-align: left;
  display: flex; flex-wrap: wrap; align-items: center; gap: 6px;
  padding: 8px; border: 0; border-radius: 8px;
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
  font-style: normal; font-size: 11px; color: var(--accent-2);
  border: 1px solid var(--line); border-radius: 999px; padding: 0 6px;
}
.badge {
  display: inline-block; padding: 1px 6px; border-radius: 999px;
  font-size: 11px; border: 1px solid var(--line);
}
.badge.on { color: var(--accent-2); }
.badge.off { color: var(--muted); }
.badge.unavailable { color: var(--danger); }

.stage { padding: 14px; min-height: 420px; }
.stage-empty {
  display: grid; place-items: center; min-height: 360px;
  color: var(--muted); font-size: 14px; text-align: center; padding: 24px;
}
.ptz-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 16px;
  align-items: start;
}
@media (max-width: 960px) {
  .ptz-grid { grid-template-columns: 1fr; }
}
.panel {
  border: 1px solid var(--line); border-radius: 16px; padding: 14px;
  background: rgba(8, 16, 13, 0.35); margin-bottom: 12px;
}
.panel-title {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 10px; font-weight: 600; gap: 8px;
}
.panel h3 { margin: 0 0 12px; font-size: 15px; }
.sub-panel { margin-top: 12px; }
.pad { display: grid; gap: 6px; justify-items: center; margin-bottom: 12px; }
.pad-mid { display: flex; gap: 6px; align-items: center; }
.pad-center { width: 36px; text-align: center; color: var(--muted); }
.pad button, .btn-row button {
  min-width: 52px; border: 1px solid var(--line); background: rgba(0, 0, 0, 0.2);
  color: var(--text); border-radius: 10px; padding: 8px 10px; cursor: pointer;
}
.btn-row { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 8px; }
.action-msg { font-size: 12px; color: var(--muted); margin: 8px 0 0; }
.preset-form { display: grid; gap: 8px; margin-bottom: 12px; }
.preset-form input[type="text"],
.preset-form input[type="number"] {
  border: 1px solid var(--line); border-radius: 10px; padding: 8px 10px;
  background: rgba(0, 0, 0, 0.2); color: var(--text);
}
.chk { font-size: 13px; color: var(--muted); display: flex; align-items: center; gap: 6px; }
.preset-list { list-style: none; margin: 0; padding: 0; display: grid; gap: 6px; }
.preset-list li {
  display: flex; justify-content: space-between; align-items: center;
  padding: 8px 0; border-top: 1px solid var(--line);
}
.preset-name { font-size: 13px; }
.link { border: 0; background: none; color: var(--accent); cursor: pointer; }
.ghost {
  border: 1px solid var(--line); background: transparent; color: var(--text);
  border-radius: 10px; padding: 6px 12px; cursor: pointer;
}
.ghost.sm { padding: 4px 10px; font-size: 12px; }
.ghost:disabled { opacity: 0.45; cursor: not-allowed; }
.primary {
  border: 0; border-radius: 10px; padding: 8px 14px; cursor: pointer;
  background: linear-gradient(135deg, var(--accent), #2f9a65); color: #04140c; font-weight: 600;
}
.mask {
  position: fixed; inset: 0; background: rgba(0, 0, 0, 0.55);
  display: grid; place-items: center; padding: 20px; z-index: 50;
}
.snap-modal {
  background: var(--panel); border: 1px solid var(--line); border-radius: 16px;
  padding: 20px; max-width: min(720px, 100%); width: 100%;
}
.snap-modal h2 { margin: 0 0 12px; font-size: 18px; }
.snap-img { width: 100%; border-radius: 10px; background: #000; }
.snap-actions { display: flex; gap: 8px; margin-top: 12px; flex-wrap: wrap; }
</style>
