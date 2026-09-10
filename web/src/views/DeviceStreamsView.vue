<template>
  <AppShell>
    <section class="head">
      <div>
        <router-link class="back" to="/devices">← 返回设备列表</router-link>
        <h1>码流管理</h1>
        <p v-if="device">
          <span class="mono">{{ device.deviceId }}</span>
          · {{ device.name || '未命名设备' }}
          · 状态 {{ device.status }}
        </p>
      </div>
      <button type="button" class="primary" @click="openRegister()">注册码流</button>
    </section>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="loading" class="muted">加载中…</p>

    <div v-else class="cards">
      <article v-for="s in streams" :key="s.id" class="card">
        <header>
          <strong>{{ s.streamType === 'main' ? '主码流' : '子码流' }}</strong>
          <span class="badge" :class="s.status === 'ON' ? 'on' : 'off'">{{ s.status }}</span>
        </header>
        <p class="name">{{ s.streamName || '-' }}</p>
        <p class="url">{{ s.streamUrl || '（未注册 RTMP 地址）' }}</p>
        <p class="muted">播放人数（Redis）：{{ s.playCount ?? 0 }}</p>
        <div class="actions">
          <button type="button" class="primary sm" :disabled="!s.streamUrl" @click="onPreview(s)">预览</button>
          <button type="button" class="ghost sm" @click="onStop(s)">停止</button>
          <button type="button" class="ghost sm" @click="openRegister(s)">编辑</button>
          <button type="button" class="link danger" @click="onDelete(s)">删除</button>
        </div>
      </article>
      <p v-if="!streams.length" class="empty">该设备暂无码流，请先注册 main/sub</p>
    </div>

    <section class="records">
      <div class="records-head">
        <h2>历史录像</h2>
        <button type="button" class="ghost sm" :disabled="recLoading" @click="loadRecordings">查询</button>
      </div>
      <div class="rec-filters">
        <label>
          <span>开始时间</span>
          <input v-model="recFrom" type="datetime-local" />
        </label>
        <label>
          <span>结束时间</span>
          <input v-model="recTo" type="datetime-local" />
        </label>
      </div>
      <p v-if="recError" class="error">{{ recError }}</p>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>时间戳</th>
              <th>文件名</th>
              <th>大小</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in recordings" :key="r.fileName">
              <td class="mono">{{ r.timestamp }}</td>
              <td>{{ r.fileName }}</td>
              <td>{{ formatSize(r.size) }}</td>
              <td class="actions">
                <button type="button" class="link" @click="playRecording(r)">播放</button>
                <a class="link" :href="fileUrl(r)" target="_blank" rel="noopener">下载</a>
              </td>
            </tr>
            <tr v-if="!recordings.length">
              <td colspan="4" class="empty">暂无录像（可按时间戳筛选后查询）</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <div v-if="recPlay" class="mask" @click.self="recPlay = null">
      <div class="modal player-modal">
        <h2>录像 · {{ recPlay.fileName }}</h2>
        <video class="rec-video" :src="fileUrl(recPlay)" controls autoplay />
        <div class="form-actions">
          <button type="button" class="ghost" @click="recPlay = null">关闭</button>
        </div>
      </div>
    </div>

    <div v-if="regOpen" class="mask" @click.self="regOpen = false">
      <form class="modal" @submit.prevent="saveRegister">
        <h2>{{ editingStream ? '编辑码流' : '注册码流' }}</h2>
        <label><span>设备ID</span><input v-model="regForm.deviceId" disabled /></label>
        <label><span>码流类型</span>
          <select v-model="regForm.streamType" :disabled="!!editingStream">
            <option value="sub">子码流 sub</option>
            <option value="main">主码流 main</option>
          </select>
        </label>
        <label>
          <span>RTMP 地址</span>
          <input v-model.trim="regForm.streamUrl" required placeholder="rtmp://8.130.74.232/live/cam01_sub" />
        </label>
        <label><span>码流名称</span><input v-model.trim="regForm.streamName" /></label>
        <label><span>状态</span>
          <select v-model="regForm.status"><option value="ON">ON</option><option value="OFF">OFF</option></select>
        </label>
        <p v-if="formError" class="error">{{ formError }}</p>
        <div class="form-actions">
          <button type="button" class="ghost" @click="regOpen = false">取消</button>
          <button type="submit" class="primary">保存</button>
        </div>
      </form>
    </div>

    <div v-if="playInfo" class="mask" @click.self="playInfo = null">
      <div class="modal player-modal">
        <h2>预览 · {{ playInfo.streamType === 'main' ? '主码流' : '子码流' }}</h2>
        <p class="muted">播放人数：{{ playInfo.ref }}</p>
        <StreamPlayer :url="playInfo.streamUrl" />
        <div class="form-actions">
          <button type="button" class="ghost" @click="playInfo = null">关闭</button>
        </div>
      </div>
    </div>
  </AppShell>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppShell from '../components/AppShell.vue'
import StreamPlayer from '../components/StreamPlayer.vue'
import {
  deleteStream, fetchDevice, fetchDeviceByDeviceId, fetchDevices,
  fetchRecordings, previewStart, previewStop, recordingFileUrl, registerStream
} from '../api/device'

const route = useRoute()
const deviceId = computed(() => decodeURIComponent(route.params.deviceId || ''))

const device = ref(null)
const loading = ref(false)
const error = ref('')
const formError = ref('')
const regOpen = ref(false)
const editingStream = ref(null)
const playInfo = ref(null)

const recordings = ref([])
const recLoading = ref(false)
const recError = ref('')
const recFrom = ref('')
const recTo = ref('')
const recPlay = ref(null)

const regForm = reactive({
  deviceId: '',
  streamType: 'sub',
  streamUrl: '',
  streamName: '',
  status: 'ON'
})

const streams = computed(() => device.value?.streams || [])

function toApiTime(localValue) {
  if (!localValue) return undefined
  // datetime-local: 2026-09-10T14:30 -> 2026-09-10 14:30:00
  const s = localValue.length === 16 ? `${localValue}:00` : localValue
  return s.replace('T', ' ')
}

function formatSize(n) {
  const v = Number(n) || 0
  if (v < 1024) return `${v} B`
  if (v < 1024 * 1024) return `${(v / 1024).toFixed(1)} KB`
  return `${(v / 1024 / 1024).toFixed(1)} MB`
}

function fileUrl(r) {
  return recordingFileUrl(deviceId.value, r.fileName)
}

async function loadRecordings() {
  if (!deviceId.value) return
  recLoading.value = true
  recError.value = ''
  try {
    recordings.value = (await fetchRecordings(deviceId.value, {
      from: toApiTime(recFrom.value),
      to: toApiTime(recTo.value)
    })) || []
  } catch (e) {
    recError.value = e.message || '查询录像失败'
    recordings.value = []
  } finally {
    recLoading.value = false
  }
}

function playRecording(r) {
  recPlay.value = r
}

async function load() {
  if (!deviceId.value) return
  loading.value = true
  error.value = ''
  try {
    try {
      device.value = await fetchDeviceByDeviceId(deviceId.value)
    } catch {
      const list = (await fetchDevices()) || []
      const row = list.find((d) => d.deviceId === deviceId.value)
      if (!row?.id) throw new Error('设备不存在')
      device.value = await fetchDevice(row.id)
    }
    await loadRecordings()
  } catch (e) {
    error.value = e.message || '加载失败'
    device.value = null
  } finally {
    loading.value = false
  }
}

function openRegister(stream = null) {
  formError.value = ''
  editingStream.value = stream
  Object.assign(regForm, {
    deviceId: deviceId.value,
    streamType: stream?.streamType || 'sub',
    streamUrl: stream?.streamUrl || '',
    streamName: stream?.streamName || '',
    status: stream?.status || 'ON'
  })
  regOpen.value = true
}

async function saveRegister() {
  formError.value = ''
  try {
    await registerStream({
      deviceId: regForm.deviceId,
      streamType: regForm.streamType,
      streamUrl: regForm.streamUrl,
      streamName: regForm.streamName || null,
      status: regForm.status,
      deviceName: device.value?.name || null
    })
    regOpen.value = false
    await load()
  } catch (e) {
    formError.value = e.message || '保存失败'
  }
}

async function onDelete(s) {
  if (!confirm(`删除 ${s.streamType} 码流？`)) return
  await deleteStream(s.id)
  await load()
}

async function onPreview(s) {
  try {
    playInfo.value = await previewStart({
      deviceId: deviceId.value,
      streamType: s.streamType
    })
    await load()
  } catch (e) {
    alert(e.message || '预览失败')
  }
}

async function onStop(s) {
  try {
    await previewStop({ deviceId: deviceId.value, streamType: s.streamType })
    await load()
  } catch (e) {
    alert(e.message || '停止失败')
  }
}

watch(deviceId, load, { immediate: false })
onMounted(load)
</script>

<style scoped>
.head { display: flex; justify-content: space-between; gap: 16px; align-items: flex-end; margin-bottom: 20px; }
.back { color: var(--muted); font-size: 13px; text-decoration: none; }
h1 { margin: 8px 0 0; font-family: Syne, sans-serif; font-size: 32px; }
.head p { margin: 8px 0 0; color: var(--muted); }
.mono { font-family: ui-monospace, Menlo, Consolas, monospace; }
.cards { display: grid; gap: 14px; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); }
.card {
  border: 1px solid var(--line); border-radius: 16px; padding: 16px;
  background: var(--panel); display: grid; gap: 8px;
}
.card header { display: flex; justify-content: space-between; align-items: center; }
.name { margin: 0; }
.url { margin: 0; font-size: 12px; color: var(--muted); word-break: break-all; }
.badge { display: inline-block; padding: 2px 8px; border-radius: 999px; font-size: 12px; border: 1px solid var(--line); }
.badge.on { color: var(--accent-2); }
.actions { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-top: 6px; }
.primary, .ghost, .link { cursor: pointer; }
.primary { border: 0; border-radius: 12px; padding: 10px 14px; background: linear-gradient(135deg, var(--accent), #2f9a65); color: #04140c; font-weight: 600; }
.primary.sm, .ghost.sm { padding: 8px 12px; font-size: 13px; }
.ghost { border: 1px solid var(--line); background: transparent; color: var(--text); border-radius: 12px; padding: 10px 14px; }
.link { border: 0; background: transparent; color: var(--accent-2); padding: 0; }
.link.danger { color: var(--danger); }
.error { color: var(--danger); }
.muted, .empty { color: var(--muted); }
.mask { position: fixed; inset: 0; background: rgba(0,0,0,.55); display: grid; place-items: center; padding: 20px; z-index: 40; }
.modal { width: min(460px, 100%); background: #102019; border: 1px solid var(--line); border-radius: 18px; padding: 22px; display: grid; gap: 12px; box-shadow: var(--shadow); }
.player-modal { width: min(720px, 100%); }
.modal h2 { margin: 0; font-family: Syne, sans-serif; }
.modal label { display: grid; gap: 6px; }
.modal label span { font-size: 13px; color: var(--muted); }
.modal input, .modal select {
  border: 1px solid var(--line); background: rgba(8,16,13,.65); color: var(--text);
  border-radius: 12px; padding: 11px 14px; outline: none; width: 100%;
}
.form-actions { display: flex; justify-content: flex-end; gap: 10px; }
.records { margin-top: 28px; }
.records-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.records h2 { margin: 0; font-family: Syne, sans-serif; font-size: 20px; }
.rec-filters { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 12px; }
.rec-filters label { display: grid; gap: 6px; min-width: 220px; }
.rec-filters label span { font-size: 13px; color: var(--muted); }
.rec-filters input {
  border: 1px solid var(--line); background: rgba(8,16,13,.65); color: var(--text);
  border-radius: 12px; padding: 10px 12px; outline: none;
}
.table-wrap { border: 1px solid var(--line); border-radius: 16px; overflow: auto; background: var(--panel); }
table { width: 100%; border-collapse: collapse; min-width: 640px; }
th, td { text-align: left; padding: 12px 14px; border-bottom: 1px solid var(--line); font-size: 13px; }
th { color: var(--muted); font-weight: 500; }
.rec-video { width: 100%; max-height: 420px; background: #000; border-radius: 12px; }
</style>
