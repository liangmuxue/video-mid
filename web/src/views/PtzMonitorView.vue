<template>
  <AppShell>
    <section class="ptz-page">
      <header class="ptz-head">
        <div>
          <h1>云台监控</h1>
          <p class="sub">
            TIC7632 双光谱云台 ·
            <span v-if="config?.mock" class="badge mock">模拟数据</span>
            <span v-else class="badge live">真实宇视</span>
          </p>
        </div>
        <p v-if="error" class="error">{{ error }}</p>
      </header>

      <div v-if="loading" class="muted">加载设备…</div>

      <template v-else>
        <div class="device-bar">
          <button
            v-for="d in devices"
            :key="d.deviceId"
            type="button"
            class="device-tab"
            :class="{ active: d.deviceId === activeId }"
            @click="selectDevice(d.deviceId)"
          >
            {{ d.name || d.deviceId }}
          </button>
        </div>

        <div v-if="activeDevice" class="ptz-grid">
          <div class="main-col">
            <div class="panel">
              <div class="panel-title">
                <span>可见光 · 主画面</span>
                <button type="button" class="ghost" @click="captureMain">抓拍</button>
              </div>
              <MockPtzPlayer v-if="simEnabled" ref="mainPlayerRef" :url="visibleUrl" :pose="pose" />
              <StreamPlayer v-else ref="mainPlayerRef" :url="visibleUrl" />
            </div>

            <div v-if="thermalUrl" class="panel sub-panel">
              <div class="panel-title"><span>热成像{{ simEnabled ? '（随云台·模拟）' : '' }}</span></div>
              <MockPtzPlayer v-if="simEnabled" :url="thermalUrl" :pose="pose" />
              <StreamPlayer v-else :url="thermalUrl" />
            </div>
          </div>

          <aside class="side-col">
            <div class="panel">
              <h3>云台控制</h3>
              <div class="pad">
                <button type="button" @mousedown="holdMove('up')" @mouseup="stopHold" @mouseleave="stopHold">上</button>
                <div class="pad-mid">
                  <button type="button" @mousedown="holdMove('left')" @mouseup="stopHold" @mouseleave="stopHold">左</button>
                  <span class="pad-center">●</span>
                  <button type="button" @mousedown="holdMove('right')" @mouseup="stopHold" @mouseleave="stopHold">右</button>
                </div>
                <button type="button" @mousedown="holdMove('down')" @mouseup="stopHold" @mouseleave="stopHold">下</button>
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
              <p v-if="simEnabled" class="pose-readout">{{ poseText }}</p>
              <p v-if="actionMsg" class="action-msg">{{ actionMsg }}</p>
            </div>

            <div class="panel">
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

            <div v-if="presets.length" class="panel">
              <h3>预置位分屏</h3>
              <div class="preset-grid">
                <div v-for="p in presets.slice(0, 4)" :key="'pv-' + p.index" class="preset-thumb">
                  <span>{{ p.name }}</span>
                  <button type="button" class="link" @click="gotoPreset(p.index)">查看</button>
                </div>
              </div>
            </div>
          </aside>
        </div>
      </template>
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
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import AppShell from '../components/AppShell.vue'
import StreamPlayer from '../components/StreamPlayer.vue'
import MockPtzPlayer from '../mock/MockPtzPlayer.vue'
import { useBackdropClose } from '../composables/useBackdropClose'
import {
  fetchUniviewConfig,
  fetchUniviewDevice,
  fetchPtzDevices,
  ptzFocus,
  ptzGotoPreset,
  ptzMove,
  ptzSetPreset,
  ptzWideAngle,
  ptzZoom
} from '../api/uniview'
import {
  applyFocus,
  applyMove,
  applyWideAngle,
  applyZoom,
  captureSimFrame,
  captureSyntheticFrame,
  defaultPose,
  describePose,
  loadStoredPose,
  poseFromPreset,
  saveStoredPose,
  saveStoredPreset,
  storedPresetPose
} from '../mock/ptzSim'

const loading = ref(true)
const error = ref('')
const config = ref(null)
const devices = ref([])
const activeId = ref('')
const detail = ref(null)
const actionMsg = ref('')
const mainPlayerRef = ref(null)

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

const activeDevice = computed(() =>
  devices.value.find((d) => d.deviceId === activeId.value) || null
)

const presets = computed(() => activeDevice.value?.presets || [])

function streamUrl(channelType, streamType = 'sub') {
  const streams = detail.value?.streams || []
  const hit =
    streams.find((s) => s.channelType === channelType && s.streamType === streamType) ||
    streams.find((s) => s.channelType === channelType)
  return hit?.streamUrl || ''
}

const visibleUrl = computed(() => streamUrl('visible', 'sub') || streamUrl('visible', 'main'))
const thermalUrl = computed(() => streamUrl('thermal', 'main') || streamUrl('thermal', 'sub'))
const simEnabled = computed(() => config.value?.mock === true)
const pose = reactive(defaultPose())
const poseText = computed(() => describePose(pose))

function commitPose(next) {
  Object.assign(pose, next)
  if (activeId.value) saveStoredPose(activeId.value, pose)
}

function restorePose(deviceId, devicePresets) {
  const stored = loadStoredPose(deviceId)
  const first = devicePresets?.[0]
  Object.assign(pose, stored || poseFromPreset(first) || defaultPose())
}

let holdTimer = null

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    config.value = await fetchUniviewConfig()
    devices.value = await fetchPtzDevices()
    if (devices.value.length && !activeId.value) {
      await selectDevice(devices.value[0].deviceId)
    }
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function selectDevice(deviceId) {
  activeId.value = deviceId
  actionMsg.value = ''
  detail.value = await fetchUniviewDevice(deviceId)
  const d = devices.value.find((x) => x.deviceId === deviceId)
  if (d) {
    const fresh = await fetchPtzDevices()
    const found = fresh.find((x) => x.deviceId === deviceId)
    if (found) Object.assign(d, found)
  }
  restorePose(deviceId, devices.value.find((x) => x.deviceId === deviceId)?.presets)
}

async function runAction(label, fn, after) {
  if (!activeId.value) return
  try {
    const data = await fn()
    if (after) after(data)
    actionMsg.value = label + ' 已发送'
  } catch (e) {
    actionMsg.value = e.message || '操作失败'
  }
}

function holdMove(direction) {
  stopHold()
  runAction('云台 ' + direction, () => ptzMove(activeId.value, direction), () => {
    commitPose(applyMove(pose, direction))
  })
  holdTimer = setInterval(() => {
    ptzMove(activeId.value, direction)
      .then(() => commitPose(applyMove(pose, direction)))
      .catch(() => {})
  }, 400)
}

function stopHold() {
  if (holdTimer) {
    clearInterval(holdTimer)
    holdTimer = null
  }
}

function doZoom(action) {
  runAction('变倍', () => ptzZoom(activeId.value, action), () => {
    commitPose(applyZoom(pose, action))
  })
}

function doFocus(kind) {
  const action = kind === 'near' ? 'near' : 'far'
  runAction('对焦', () => ptzFocus(activeId.value, action), () => {
    commitPose(applyFocus(pose, action))
  })
}

function doWideAngle() {
  runAction('一键广角', () => ptzWideAngle(activeId.value), () => {
    commitPose(applyWideAngle())
  })
}

function gotoPreset(index) {
  runAction('预置位 ' + index, () => ptzGotoPreset(activeId.value, index), (data) => {
    const listed = presets.value.find((p) => Number(p.index) === Number(index))
    commitPose(storedPresetPose(activeId.value, index, data || listed))
  })
}

async function savePreset() {
  if (!presetForm.name.trim()) {
    actionMsg.value = '请输入预置位名称'
    return
  }
  await runAction(
    '保存预置位',
    () =>
      ptzSetPreset(
        activeId.value,
        presetForm.index,
        presetForm.name.trim(),
        presetForm.overwrite,
        pose.zoom
      ),
    () => {
      saveStoredPreset(activeId.value, presetForm.index, presetForm.name.trim(), pose)
    }
  )
  const fresh = await fetchPtzDevices()
  devices.value = fresh
}

function captureMain() {
  const video = mainPlayerRef.value?.getVideoElement?.()
  let canvas = null
  if (video?.videoWidth && simEnabled.value) {
    canvas = captureSimFrame(video, pose)
  } else if (video?.videoWidth) {
    canvas = document.createElement('canvas')
    canvas.width = video.videoWidth
    canvas.height = video.videoHeight
    canvas.getContext('2d').drawImage(video, 0, 0)
  } else if (simEnabled.value) {
    canvas = captureSyntheticFrame(pose)
  }
  if (!canvas) {
    actionMsg.value = '当前无画面，无法抓拍'
    return
  }
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

onMounted(loadAll)
onBeforeUnmount(stopHold)
</script>

<style scoped>
.ptz-page { display: grid; gap: 16px; }
.ptz-head h1 { margin: 0; font-family: Syne, sans-serif; font-size: 28px; }
.sub { margin: 6px 0 0; color: var(--muted); font-size: 13px; }
.badge { font-size: 11px; padding: 2px 8px; border-radius: 999px; margin-left: 6px; }
.badge.mock { background: rgba(200, 240, 106, 0.2); color: var(--accent-2); }
.badge.live { background: rgba(61, 186, 122, 0.2); color: var(--accent); }
.error { color: var(--danger); margin: 8px 0 0; }
.muted { color: var(--muted); }

.device-bar { display: flex; gap: 8px; flex-wrap: wrap; }
.device-tab {
  border: 1px solid var(--line); background: transparent; color: var(--text);
  border-radius: 999px; padding: 8px 16px; cursor: pointer;
}
.device-tab.active {
  border-color: var(--accent); background: rgba(61, 186, 122, 0.15);
}

.ptz-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 16px;
  align-items: start;
}
@media (max-width: 960px) {
  .ptz-grid { grid-template-columns: 1fr; }
}

.panel {
  border: 1px solid var(--line); border-radius: 16px; padding: 14px;
  background: var(--panel); margin-bottom: 12px;
}
.panel-title {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 10px; font-weight: 600;
}
.panel h3 { margin: 0 0 12px; font-size: 15px; }
.sub-panel { margin-top: 12px; }

.pad { display: grid; gap: 6px; justify-items: center; margin-bottom: 12px; }
.pad-mid { display: flex; gap: 6px; align-items: center; }
.pad-center { width: 36px; text-align: center; color: var(--muted); }
.pad button, .btn-row button {
  min-width: 52px; border: 1px solid var(--line); background: rgba(0,0,0,.2);
  color: var(--text); border-radius: 10px; padding: 8px 10px; cursor: pointer;
}
.btn-row { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 8px; }
.action-msg { font-size: 12px; color: var(--muted); margin: 8px 0 0; }
.pose-readout { font-size: 12px; color: var(--accent-2); margin: 8px 0 0; }

.preset-form { display: grid; gap: 8px; margin-bottom: 12px; }
.preset-form input[type="text"],
.preset-form input[type="number"] {
  border: 1px solid var(--line); border-radius: 10px; padding: 8px 10px;
  background: rgba(0,0,0,.2); color: var(--text);
}
.chk { font-size: 13px; color: var(--muted); display: flex; align-items: center; gap: 6px; }
.preset-list { list-style: none; margin: 0; padding: 0; display: grid; gap: 6px; }
.preset-list li {
  display: flex; justify-content: space-between; align-items: center;
  padding: 8px 0; border-top: 1px solid var(--line);
}
.preset-name { font-size: 13px; }
.link { border: 0; background: none; color: var(--accent); cursor: pointer; }
.ghost { border: 1px solid var(--line); background: transparent; color: var(--text); border-radius: 10px; padding: 6px 12px; cursor: pointer; }
.primary {
  border: 0; border-radius: 10px; padding: 8px 14px; cursor: pointer;
  background: linear-gradient(135deg, var(--accent), #2f9a65); color: #04140c; font-weight: 600;
}

.preset-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.preset-thumb {
  border: 1px dashed var(--line); border-radius: 10px; padding: 12px;
  min-height: 72px; display: flex; flex-direction: column; justify-content: space-between;
  font-size: 12px; color: var(--muted);
}

.mask {
  position: fixed; inset: 0; background: rgba(0,0,0,.55);
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
