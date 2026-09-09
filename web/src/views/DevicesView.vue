<template>
  <AppShell>
    <section class="head">
      <div>
        <h1>设备管理</h1>
        <p>设备与码流分表；播放地址由注册写入 MySQL，Redis 仅统计播放人数。</p>
      </div>
      <div class="head-actions">
        <button type="button" class="ghost" @click="openRegister()">注册码流</button>
        <button type="button" class="primary" @click="openDevice()">新增设备</button>
      </div>
    </section>

    <section class="toolbar">
      <input v-model.trim="keyword" placeholder="搜索设备ID / 名称 / 播放地址" />
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
            <th>子码流地址</th>
            <th>主码流地址</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="d in filtered" :key="d.id">
            <td class="mono">{{ d.deviceId }}</td>
            <td>{{ d.name || '-' }}</td>
            <td><span class="badge" :class="d.status === 'ON' ? 'on' : 'off'">{{ d.status }}</span></td>
            <td class="url">{{ d.subStreamUrl || '-' }}</td>
            <td class="url">{{ d.mainStreamUrl || '-' }}</td>
            <td class="actions">
              <button type="button" class="link" @click="openStreams(d)">码流</button>
              <button type="button" class="link" @click="play(d, 'sub')">预览子码流</button>
              <button type="button" class="link" @click="openDevice(d)">编辑</button>
              <button type="button" class="link danger" @click="onDeleteDevice(d)">删除</button>
            </td>
          </tr>
          <tr v-if="!filtered.length">
            <td colspan="6" class="empty">暂无设备，可「新增设备」或「注册码流」</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 码流明细 -->
    <div v-if="detail" class="mask" @click.self="detail = null">
      <aside class="drawer">
        <header>
          <div>
            <h2>{{ detail.name || detail.deviceId }}</h2>
            <p class="mono">{{ detail.deviceId }}</p>
          </div>
          <button type="button" class="ghost" @click="detail = null">关闭</button>
        </header>
        <button type="button" class="primary block" @click="openRegister(detail.deviceId)">为此设备注册码流</button>
        <ul class="list">
          <li v-for="s in detail.streams || []" :key="s.id">
            <div>
              <strong>{{ s.streamType === 'main' ? '主码流' : '子码流' }}</strong>
              <span class="muted">播放数 {{ s.playCount ?? 0 }}</span>
              <p class="url">{{ s.streamUrl || '（未注册地址）' }}</p>
            </div>
            <div class="actions">
              <button type="button" class="link" @click="playDevice(detail.deviceId, s.streamType)">预览</button>
              <button type="button" class="link" @click="stopDevice(detail.deviceId, s.streamType)">停止</button>
              <button type="button" class="link danger" @click="onDeleteStream(s)">删除</button>
            </div>
          </li>
          <li v-if="!(detail.streams && detail.streams.length)" class="empty">暂无码流</li>
        </ul>
      </aside>
    </div>

    <!-- 设备表单 -->
    <div v-if="deviceFormOpen" class="mask" @click.self="deviceFormOpen = false">
      <form class="modal" @submit.prevent="saveDevice">
        <h2>{{ editingDeviceId ? '编辑设备' : '新增设备' }}</h2>
        <label><span>设备ID</span><input v-model.trim="deviceForm.deviceId" required :disabled="!!editingDeviceId" /></label>
        <label><span>名称</span><input v-model.trim="deviceForm.name" /></label>
        <label><span>状态</span>
          <select v-model="deviceForm.status"><option value="ON">ON</option><option value="OFF">OFF</option></select>
        </label>
        <label><span>厂家</span><input v-model.trim="deviceForm.manufacturer" /></label>
        <label><span>型号</span><input v-model.trim="deviceForm.model" /></label>
        <label><span>地址</span><input v-model.trim="deviceForm.address" /></label>
        <label><span>网关ID</span><input v-model.trim="deviceForm.gatewayId" /></label>
        <p v-if="formError" class="error">{{ formError }}</p>
        <div class="form-actions">
          <button type="button" class="ghost" @click="deviceFormOpen = false">取消</button>
          <button type="submit" class="primary">保存</button>
        </div>
      </form>
    </div>

    <!-- 注册码流 -->
    <div v-if="regOpen" class="mask" @click.self="regOpen = false">
      <form class="modal" @submit.prevent="saveRegister">
        <h2>注册码流地址</h2>
        <label><span>设备ID</span><input v-model.trim="regForm.deviceId" required /></label>
        <label><span>设备名称（可选，新建设备时用）</span><input v-model.trim="regForm.deviceName" /></label>
        <label><span>码流类型</span>
          <select v-model="regForm.streamType">
            <option value="sub">子码流 sub</option>
            <option value="main">主码流 main</option>
          </select>
        </label>
        <label><span>播放地址 streamUrl</span><input v-model.trim="regForm.streamUrl" required placeholder="http://..." /></label>
        <label><span>码流名称</span><input v-model.trim="regForm.streamName" /></label>
        <p v-if="formError" class="error">{{ formError }}</p>
        <div class="form-actions">
          <button type="button" class="ghost" @click="regOpen = false">取消</button>
          <button type="submit" class="primary">注册</button>
        </div>
      </form>
    </div>

    <!-- 预览结果 -->
    <div v-if="playInfo" class="mask" @click.self="playInfo = null">
      <div class="modal">
        <h2>预览</h2>
        <p>播放人数（Redis）：{{ playInfo.ref }}</p>
        <p class="url break">{{ playInfo.streamUrl }}</p>
        <div class="form-actions">
          <a class="primary" :href="playInfo.streamUrl" target="_blank" rel="noopener">打开地址</a>
          <button type="button" class="ghost" @click="playInfo = null">关闭</button>
        </div>
      </div>
    </div>
  </AppShell>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import AppShell from '../components/AppShell.vue'
import {
  createDevice, deleteDevice, deleteStream, fetchDevices,
  previewStart, previewStop, registerStream, updateDevice
} from '../api/device'

const devices = ref([])
const loading = ref(false)
const error = ref('')
const keyword = ref('')
const detail = ref(null)
const formError = ref('')
const playInfo = ref(null)

const deviceFormOpen = ref(false)
const editingDeviceId = ref(null)
const deviceForm = reactive({
  deviceId: '', name: '', status: 'ON', manufacturer: '', model: '', address: '', gatewayId: ''
})

const regOpen = ref(false)
const regForm = reactive({
  deviceId: '', deviceName: '', streamType: 'sub', streamUrl: '', streamName: ''
})

const filtered = computed(() => {
  const q = keyword.value.toLowerCase()
  if (!q) return devices.value
  return devices.value.filter((d) =>
    [d.deviceId, d.name, d.mainStreamUrl, d.subStreamUrl].filter(Boolean).join(' ').toLowerCase().includes(q)
  )
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    devices.value = (await fetchDevices()) || []
    if (detail.value) {
      detail.value = devices.value.find((x) => x.deviceId === detail.value.deviceId) || null
    }
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function openStreams(d) { detail.value = d }

function openDevice(d = null) {
  formError.value = ''
  editingDeviceId.value = d?.id || null
  Object.assign(deviceForm, {
    deviceId: d?.deviceId || '',
    name: d?.name || '',
    status: d?.status || 'ON',
    manufacturer: d?.manufacturer || '',
    model: d?.model || '',
    address: d?.address || '',
    gatewayId: d?.gatewayId || ''
  })
  deviceFormOpen.value = true
}

function openRegister(deviceId = '') {
  formError.value = ''
  Object.assign(regForm, {
    deviceId: deviceId || '',
    deviceName: '',
    streamType: 'sub',
    streamUrl: '',
    streamName: ''
  })
  regOpen.value = true
}

async function saveDevice() {
  formError.value = ''
  try {
    const payload = { ...deviceForm }
    if (editingDeviceId.value) await updateDevice(editingDeviceId.value, payload)
    else await createDevice(payload)
    deviceFormOpen.value = false
    await load()
  } catch (e) {
    formError.value = e.message || '保存失败'
  }
}

async function saveRegister() {
  formError.value = ''
  try {
    await registerStream({ ...regForm })
    regOpen.value = false
    await load()
  } catch (e) {
    formError.value = e.message || '注册失败'
  }
}

async function onDeleteDevice(d) {
  if (!confirm(`删除设备 ${d.deviceId} 及其码流？`)) return
  await deleteDevice(d.id)
  await load()
}

async function onDeleteStream(s) {
  if (!confirm(`删除码流 ${s.streamType}？`)) return
  await deleteStream(s.id)
  await load()
}

async function play(d, type) {
  await playDevice(d.deviceId, type)
}

async function playDevice(deviceId, streamType) {
  try {
    playInfo.value = await previewStart({ deviceId, streamType })
    await load()
  } catch (e) {
    alert(e.message || '预览失败')
  }
}

async function stopDevice(deviceId, streamType) {
  try {
    await previewStop({ deviceId, streamType })
    await load()
  } catch (e) {
    alert(e.message || '停止失败')
  }
}

onMounted(load)
</script>

<style scoped>
.head { display: flex; justify-content: space-between; gap: 16px; align-items: flex-end; margin-bottom: 18px; }
.head-actions { display: flex; gap: 10px; }
h1 { margin: 0; font-family: Syne, sans-serif; font-size: 32px; }
.head p { margin: 8px 0 0; color: var(--muted); max-width: 52ch; }
.toolbar { display: flex; gap: 10px; margin-bottom: 14px; }
.toolbar input, .modal input, .modal select {
  border: 1px solid var(--line); background: rgba(8,16,13,.65); color: var(--text);
  border-radius: 12px; padding: 11px 14px; outline: none; width: 100%;
}
.toolbar input { flex: 1; }
.table-wrap { border: 1px solid var(--line); border-radius: 16px; overflow: auto; background: var(--panel); }
table { width: 100%; border-collapse: collapse; min-width: 960px; }
th, td { text-align: left; padding: 13px 14px; border-bottom: 1px solid var(--line); vertical-align: top; }
th { color: var(--muted); font-size: 13px; font-weight: 500; }
.mono { font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 13px; }
.url { font-size: 12px; color: var(--muted); max-width: 220px; word-break: break-all; }
.url.break { color: var(--text); max-width: none; }
.badge { display: inline-block; padding: 2px 8px; border-radius: 999px; font-size: 12px; border: 1px solid var(--line); }
.badge.on { color: var(--accent-2); }
.actions { display: flex; flex-wrap: wrap; gap: 8px; }
.primary, .ghost, .link { cursor: pointer; }
.primary { border: 0; border-radius: 12px; padding: 10px 14px; background: linear-gradient(135deg, var(--accent), #2f9a65); color: #04140c; font-weight: 600; text-decoration: none; }
.primary.block { width: 100%; margin-bottom: 14px; }
.ghost { border: 1px solid var(--line); background: transparent; color: var(--text); border-radius: 12px; padding: 10px 14px; }
.link { border: 0; background: transparent; color: var(--accent-2); padding: 0; }
.link.danger { color: var(--danger); }
.error { color: var(--danger); }
.empty, .muted { color: var(--muted); }
.mask { position: fixed; inset: 0; background: rgba(0,0,0,.55); display: grid; place-items: center; padding: 20px; z-index: 40; }
.drawer { position: absolute; right: 0; top: 0; bottom: 0; width: min(440px, 100%); background: #102019; border-left: 1px solid var(--line); padding: 22px; overflow: auto; }
.drawer header { display: flex; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.drawer h2 { margin: 0; font-family: Syne, sans-serif; }
.list { list-style: none; margin: 0; padding: 0; display: grid; gap: 10px; }
.list li { border: 1px solid var(--line); border-radius: 12px; padding: 12px; display: flex; justify-content: space-between; gap: 10px; }
.modal { width: min(460px, 100%); background: #102019; border: 1px solid var(--line); border-radius: 18px; padding: 22px; display: grid; gap: 12px; box-shadow: var(--shadow); }
.modal h2 { margin: 0; font-family: Syne, sans-serif; }
.modal label { display: grid; gap: 6px; }
.modal label span { font-size: 13px; color: var(--muted); }
.form-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 6px; }
</style>
