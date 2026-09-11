<template>
  <AppShell>
    <section class="head">
      <div>
        <h1>设备管理</h1>
        <p>设备基础信息。码流（RTMP）在设备详情中单独管理。</p>
      </div>
      <button type="button" class="primary" @click="openDevice()">新增设备</button>
    </section>

    <section class="toolbar">
      <input v-model.trim="keyword" placeholder="搜索设备ID / 名称 / 厂家 / 地址" />
      <button type="button" class="ghost" :disabled="loading" @click="load">刷新</button>
    </section>
    <p v-if="error" class="error">{{ error }}</p>

    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>设备ID</th>
            <th>名称</th>
            <th>状态</th>
            <th>厂家</th>
            <th>安装地址</th>
            <th>码流数</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="d in filtered" :key="d.id">
            <td class="mono">{{ d.deviceId }}</td>
            <td>{{ d.name || '-' }}</td>
            <td><span class="badge" :class="d.status === 'ON' ? 'on' : 'off'">{{ d.status }}</span></td>
            <td>{{ d.manufacturer || '-' }}</td>
            <td>{{ d.address || '-' }}</td>
            <td>{{ d.streamCount ?? d.streams?.length ?? 0 }}</td>
            <td class="actions">
              <button type="button" class="link" @click="openPlayback(d)">录像回放</button>
              <router-link class="link" :to="`/devices/${encodeURIComponent(d.deviceId)}/streams`">管理码流</router-link>
              <button type="button" class="link" @click="openDevice(d)">编辑</button>
              <button type="button" class="link danger" @click="onDelete(d)">删除</button>
            </td>
          </tr>
          <tr v-if="!filtered.length">
            <td colspan="7" class="empty">暂无设备</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="formOpen" class="mask" @click.self="formOpen = false">
      <form class="modal" @submit.prevent="save">
        <h2>{{ editingId ? '编辑设备' : '新增设备' }}</h2>
        <label><span>设备ID</span><input v-model.trim="form.deviceId" required :disabled="!!editingId" /></label>
        <label><span>名称</span><input v-model.trim="form.name" /></label>
        <label><span>状态</span>
          <select v-model="form.status"><option value="ON">ON</option><option value="OFF">OFF</option></select>
        </label>
        <label><span>厂家</span><input v-model.trim="form.manufacturer" /></label>
        <label><span>型号</span><input v-model.trim="form.model" /></label>
        <label><span>安装地址</span><input v-model.trim="form.address" /></label>
        <label><span>网关ID</span><input v-model.trim="form.gatewayId" /></label>
        <label><span>平台ID</span><input v-model.trim="form.platformId" /></label>
        <p v-if="formError" class="error">{{ formError }}</p>
        <div class="form-actions">
          <button type="button" class="ghost" @click="formOpen = false">取消</button>
          <button type="submit" class="primary">保存</button>
        </div>
      </form>
    </div>

    <!-- 当前页弹层嵌入回放组件，不新开路由；遮罩需「按下+抬起」都在遮罩上才关闭，避免时间轴拖拽松手误关 -->
    <div
      v-if="playback"
      class="mask"
      @pointerdown.self="onPlaybackMaskDown"
      @pointerup.self="onPlaybackMaskUp"
      @click.self.prevent
    >
      <div class="modal playback-modal" @pointerdown.stop>
        <RecordingPlaybackPanel
          :device-id="playback.deviceId"
          :device-name="playback.name || ''"
          @close="closePlayback"
        />
      </div>
    </div>
  </AppShell>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import AppShell from '../components/AppShell.vue'
import RecordingPlaybackPanel from '../components/RecordingPlaybackPanel.vue'
import { createDevice, deleteDevice, fetchDevices, updateDevice } from '../api/device'

const devices = ref([])
const loading = ref(false)
const error = ref('')
const keyword = ref('')
const formOpen = ref(false)
const editingId = ref(null)
const formError = ref('')
const form = reactive({
  deviceId: '', name: '', status: 'ON', manufacturer: '', model: '', address: '', gatewayId: '', platformId: ''
})
const playback = ref(null)
/** 仅当在遮罩空白处按下时允许抬起关闭，防止时间轴拖出弹窗外松手误关 */
let playbackMaskArmed = false

const filtered = computed(() => {
  const q = keyword.value.toLowerCase()
  if (!q) return devices.value
  return devices.value.filter((d) =>
    [d.deviceId, d.name, d.manufacturer, d.address, d.model]
      .filter(Boolean).join(' ').toLowerCase().includes(q)
  )
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    devices.value = (await fetchDevices()) || []
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function openDevice(d = null) {
  formError.value = ''
  editingId.value = d?.id || null
  Object.assign(form, {
    deviceId: d?.deviceId || '',
    name: d?.name || '',
    status: d?.status || 'ON',
    manufacturer: d?.manufacturer || '',
    model: d?.model || '',
    address: d?.address || '',
    gatewayId: d?.gatewayId || '',
    platformId: d?.platformId || ''
  })
  formOpen.value = true
}

function openPlayback(d) {
  if (!d?.deviceId) return
  playbackMaskArmed = false
  playback.value = { deviceId: d.deviceId, name: d.name || '' }
}

function onPlaybackMaskDown() {
  playbackMaskArmed = true
}

function onPlaybackMaskUp() {
  if (playbackMaskArmed) closePlayback()
  playbackMaskArmed = false
}

function closePlayback() {
  playbackMaskArmed = false
  playback.value = null
}

async function save() {
  formError.value = ''
  try {
    const payload = { ...form }
    if (editingId.value) await updateDevice(editingId.value, payload)
    else await createDevice(payload)
    formOpen.value = false
    await load()
  } catch (e) {
    formError.value = e.message || '保存失败'
  }
}

async function onDelete(d) {
  if (!confirm(`删除设备 ${d.deviceId} 及其全部码流？`)) return
  await deleteDevice(d.id)
  await load()
}

onMounted(load)
</script>

<style scoped>
.head { display: flex; justify-content: space-between; gap: 16px; align-items: flex-end; margin-bottom: 18px; }
h1 { margin: 0; font-family: Syne, sans-serif; font-size: 32px; }
.head p { margin: 8px 0 0; color: var(--muted); }
.toolbar { display: flex; gap: 10px; margin-bottom: 14px; }
.toolbar input, .modal input, .modal select {
  border: 1px solid var(--line); background: rgba(8,16,13,.65); color: var(--text);
  border-radius: 12px; padding: 11px 14px; outline: none; width: 100%;
}
.toolbar input { flex: 1; }
.table-wrap { border: 1px solid var(--line); border-radius: 16px; overflow: auto; background: var(--panel); }
table { width: 100%; border-collapse: collapse; min-width: 900px; }
th, td { text-align: left; padding: 13px 14px; border-bottom: 1px solid var(--line); }
th { color: var(--muted); font-size: 13px; font-weight: 500; }
.mono { font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 13px; }
.badge { display: inline-block; padding: 2px 8px; border-radius: 999px; font-size: 12px; border: 1px solid var(--line); }
.badge.on { color: var(--accent-2); }
.actions { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; }
.primary, .ghost, .link { cursor: pointer; }
.primary { border: 0; border-radius: 12px; padding: 10px 14px; background: linear-gradient(135deg, var(--accent), #2f9a65); color: #04140c; font-weight: 600; }
.ghost { border: 1px solid var(--line); background: transparent; color: var(--text); border-radius: 12px; padding: 10px 14px; }
.link { border: 0; background: transparent; color: var(--accent-2); padding: 0; text-decoration: none; }
.link.danger { color: var(--danger); }
.error { color: var(--danger); }
.empty { color: var(--muted); text-align: center; }
.mask { position: fixed; inset: 0; background: rgba(0,0,0,.55); display: grid; place-items: center; padding: 20px; z-index: 40; }
.modal { width: min(460px, 100%); background: #102019; border: 1px solid var(--line); border-radius: 18px; padding: 22px; display: grid; gap: 12px; box-shadow: var(--shadow); }
.playback-modal {
  width: min(960px, 100%);
  max-height: min(92vh, 960px);
  overflow: auto;
  padding: 18px 20px 20px;
}
.modal h2 { margin: 0; font-family: Syne, sans-serif; }
.modal label { display: grid; gap: 6px; }
.modal label span { font-size: 13px; color: var(--muted); }
.form-actions { display: flex; justify-content: flex-end; gap: 10px; }
</style>
