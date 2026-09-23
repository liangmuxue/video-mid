<template>
  <AppShell>
    <section class="head">
      <div>
        <h1>设备管理</h1>
        <p>左侧目录树组织设备；码流在设备详情中管理。</p>
      </div>
      <button type="button" class="primary" @click="openDevice()">新增设备</button>
    </section>

    <p v-if="error" class="error">{{ error }}</p>

    <div class="layout">
      <aside class="tree-panel">
        <div class="tree-head">
          <strong>设备目录</strong>
          <button type="button" class="ghost sm" @click="beginCreateFolder(null)">新建</button>
        </div>
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
            @select="selectFolder"
            @add="beginCreateFolder"
            @edit="beginEditFolder"
            @remove="onDeleteFolder"
          />
        </ul>
        <p v-if="!folderTree.length" class="tree-empty">暂无目录，可点「新建」</p>
      </aside>

      <div class="main-panel">
        <section class="toolbar">
          <div class="crumb">{{ currentFolderLabel }}</div>
          <input v-model.trim="keyword" placeholder="搜索设备ID / 名称 / 厂家 / 地址" />
          <label class="check">
            <input v-model="includeChildren" type="checkbox" @change="loadDevices" />
            含下级目录
          </label>
          <button type="button" class="ghost" :disabled="loading" @click="reloadAll">刷新</button>
        </section>

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
                <td>
                  <span class="badge" :class="statusClass(d.status)">{{ statusLabel(d.status) }}</span>
                </td>
                <td>{{ d.manufacturer || '-' }}</td>
                <td>{{ d.address || '-' }}</td>
                <td>{{ d.streamCount ?? 0 }}</td>
                <td class="actions">
                  <button type="button" class="link" @click="openPlayback(d)">录像回放</button>
                  <router-link
                    class="link"
                    :to="`/devices/${encodeURIComponent(d.deviceId)}/streams`"
                  >管理码流</router-link>
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
      </div>
    </div>

    <div v-if="formOpen" class="mask" @click.self="formOpen = false">
      <form class="modal" @submit.prevent="save">
        <h2>{{ editingId ? '编辑设备' : '新增设备' }}</h2>
        <label><span>设备ID</span><input v-model.trim="form.deviceId" required :disabled="!!editingId" /></label>
        <label><span>名称</span><input v-model.trim="form.name" /></label>
        <label><span>所属目录</span>
          <select v-model="form.folderId">
            <option :value="null">未分组</option>
            <option v-for="opt in flatFolderOptions" :key="opt.id" :value="opt.id">{{ opt.label }}</option>
          </select>
        </label>
        <label><span>状态</span>
          <select v-model.number="form.status">
            <option :value="1">已启用</option>
            <option :value="2">已停用</option>
          </select>
        </label>
        <p class="tip">「不可用」由系统按推流自动判定；停用后不会被巡检改写。</p>
        <label><span>厂家</span><input v-model.trim="form.manufacturer" /></label>
        <label><span>型号</span><input v-model.trim="form.model" /></label>
        <label><span>安装地址</span><input v-model.trim="form.address" /></label>
        <label><span>网关ID</span><input v-model.trim="form.gatewayId" /></label>
        <label><span>平台ID</span><input v-model.trim="form.platformId" /></label>
        <label><span>宇视 IP</span><input v-model.trim="form.host" placeholder="留空表示非宇视设备" /></label>
        <label><span>端口</span><input v-model.number="form.port" type="number" min="1" max="65535" /></label>
        <label><span>用户名</span><input v-model.trim="form.username" autocomplete="off" /></label>
        <label><span>密码</span><input v-model="form.password" type="password" autocomplete="new-password" placeholder="编辑时留空则不修改" /></label>
        <label><span>通道号</span><input v-model.trim="form.accessChannel" placeholder="IPC 一般为 0" /></label>
        <p class="tip">保存时向摄像机查询实际启用的码流，再写入码流表。有人播放才拉流，没人看就停。</p>
        <p v-if="formError" class="error">{{ formError }}</p>
        <div class="form-actions">
          <button type="button" class="ghost" @click="formOpen = false">取消</button>
          <button type="submit" class="primary">保存</button>
        </div>
      </form>
    </div>

    <div v-if="folderFormOpen" class="mask" @click.self="folderFormOpen = false">
      <form class="modal" @submit.prevent="saveFolder">
        <h2>{{ folderEditingId ? '编辑目录' : '新建目录' }}</h2>
        <label><span>名称</span><input v-model.trim="folderForm.name" required /></label>
        <label><span>上级目录</span>
          <select v-model="folderForm.parentId">
            <option :value="null">（根目录）</option>
            <option
              v-for="opt in flatFolderOptions.filter((o) => o.id !== folderEditingId)"
              :key="opt.id"
              :value="opt.id"
            >{{ opt.label }}</option>
          </select>
        </label>
        <label><span>排序</span><input v-model.number="folderForm.sortNo" type="number" /></label>
        <p v-if="folderFormError" class="error">{{ folderFormError }}</p>
        <div class="form-actions">
          <button type="button" class="ghost" @click="folderFormOpen = false">取消</button>
          <button type="submit" class="primary">保存</button>
        </div>
      </form>
    </div>

    <div
      v-if="playback"
      class="mask"
      @mousedown.self="onPlaybackBackdropDown"
      @mouseup.self="onPlaybackBackdropUp"
      @click.self.prevent
    >
      <div
        class="modal playback-modal"
        @mousedown.stop
        @mouseup="cancelPlaybackBackdrop"
      >
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
import DeviceFolderNode from '../components/DeviceFolderNode.vue'
import RecordingPlaybackPanel from '../components/RecordingPlaybackPanel.vue'
import {
  createDevice,
  createDeviceFolder,
  deleteDevice,
  deleteDeviceFolder,
  fetchDeviceFolderTree,
  fetchDevices,
  updateDevice,
  updateDeviceFolder
} from '../api/device'
import {
  STATUS_DISABLED,
  STATUS_ENABLED,
  normalizeStatus,
  statusClass,
  statusLabel
} from '../utils/deviceStatus'
import { useBackdropClose } from '../composables/useBackdropClose'

const devices = ref([])
const folderTree = ref([])
const loading = ref(false)
const error = ref('')
const keyword = ref('')
const selectedFolderId = ref(null)
const includeChildren = ref(true)

const formOpen = ref(false)
const editingId = ref(null)
const formError = ref('')
const form = reactive({
  deviceId: '',
  name: '',
  folderId: null,
  status: STATUS_ENABLED,
  manufacturer: '',
  model: '',
  address: '',
  gatewayId: '',
  platformId: '',
  host: '',
  port: null,
  username: '',
  password: '',
  accessChannel: '0'
})

const folderFormOpen = ref(false)
const folderEditingId = ref(null)
const folderFormError = ref('')
const folderForm = reactive({ name: '', parentId: null, sortNo: 0 })

const playback = ref(null)
const {
  onBackdropDown: onPlaybackBackdropDown,
  onBackdropUp: onPlaybackBackdropUp,
  cancelBackdrop: cancelPlaybackBackdrop
} = useBackdropClose(() => {
  playback.value = null
})

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
    [d.deviceId, d.name, d.manufacturer, d.address, d.model]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
      .includes(q)
  )
})

async function loadFolders() {
  folderTree.value = (await fetchDeviceFolderTree()) || []
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
    devices.value = (await fetchDevices(params)) || []
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

function openDevice(d = null) {
  formError.value = ''
  editingId.value = d?.id || null
  const raw = normalizeStatus(d?.status ?? STATUS_ENABLED)
  const manual = raw === STATUS_DISABLED ? STATUS_DISABLED : STATUS_ENABLED
  Object.assign(form, {
    deviceId: d?.deviceId || '',
    name: d?.name || '',
    folderId: d?.folderId ?? selectedFolderId.value ?? null,
    status: manual,
    manufacturer: d?.manufacturer || '',
    model: d?.model || '',
    address: d?.address || '',
    gatewayId: d?.gatewayId || '',
    platformId: d?.platformId || '',
    host: d?.host || '',
    port: d?.port ?? null,
    username: d?.username || '',
    password: '',
    accessChannel: d?.accessChannel || '0'
  })
  formOpen.value = true
}

function beginCreateFolder(parent) {
  folderFormError.value = ''
  folderEditingId.value = null
  Object.assign(folderForm, {
    name: '',
    parentId: parent?.id ?? null,
    sortNo: 0
  })
  folderFormOpen.value = true
}

function beginEditFolder(node) {
  folderFormError.value = ''
  folderEditingId.value = node.id
  Object.assign(folderForm, {
    name: node.name || '',
    parentId: node.parentId ?? null,
    sortNo: node.sortNo || 0
  })
  folderFormOpen.value = true
}

async function save() {
  formError.value = ''
  try {
    const payload = { ...form, folderId: form.folderId ?? null }
    if (payload.port === '' || Number.isNaN(payload.port)) payload.port = null
    if (!payload.host) {
      payload.port = null
      payload.username = null
      payload.password = null
      payload.accessChannel = null
    }
    if (editingId.value) await updateDevice(editingId.value, payload)
    else await createDevice(payload)
    formOpen.value = false
    await reloadAll()
  } catch (e) {
    formError.value = e.message || '保存失败'
  }
}

async function saveFolder() {
  folderFormError.value = ''
  try {
    const payload = {
      name: folderForm.name,
      parentId: folderForm.parentId,
      sortNo: folderForm.sortNo || 0
    }
    if (folderEditingId.value) await updateDeviceFolder(folderEditingId.value, payload)
    else await createDeviceFolder(payload)
    folderFormOpen.value = false
    await loadFolders()
  } catch (e) {
    folderFormError.value = e.message || '保存失败'
  }
}

async function onDeleteFolder(node) {
  if (!confirm(`删除目录「${node.name}」？`)) return
  try {
    await deleteDeviceFolder(node.id)
    if (selectedFolderId.value === node.id) selectedFolderId.value = null
    await reloadAll()
  } catch (e) {
    error.value = e.message || '删除目录失败'
  }
}

function openPlayback(d) {
  if (!d?.deviceId) return
  cancelPlaybackBackdrop()
  playback.value = { deviceId: d.deviceId, name: d.name || '' }
}

function closePlayback() {
  cancelPlaybackBackdrop()
  playback.value = null
}

async function onDelete(d) {
  if (!confirm(`删除设备 ${d.deviceId} 及其全部码流？`)) return
  await deleteDevice(d.id)
  await reloadAll()
}

onMounted(reloadAll)
</script>

<style scoped>
.head { display: flex; justify-content: space-between; gap: 16px; align-items: flex-end; margin-bottom: 18px; }
h1 { margin: 0; font-family: Syne, sans-serif; font-size: 32px; }
.head p { margin: 8px 0 0; color: var(--muted); }

.layout {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}
@media (max-width: 900px) {
  .layout { grid-template-columns: 1fr; }
}

.tree-panel {
  border: 1px solid var(--line);
  border-radius: 16px;
  background: var(--panel);
  padding: 12px;
  min-height: 420px;
}
.tree-head {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 10px; font-size: 14px;
}
.tree-root {
  width: 100%; text-align: left; border: 0; border-radius: 8px;
  background: transparent; color: var(--text); padding: 8px 10px; cursor: pointer; margin-bottom: 4px;
}
.tree-root.active { background: rgba(61, 186, 122, 0.14); }
.tree { list-style: none; margin: 0; padding: 0; }
.tree-empty { color: var(--muted); font-size: 12px; margin: 12px 4px; }

.main-panel { min-width: 0; }
.toolbar { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 14px; align-items: center; }
.crumb { font-size: 13px; color: var(--muted); min-width: 80px; }
.toolbar input, .modal input, .modal select {
  border: 1px solid var(--line); background: rgba(8,16,13,.65); color: var(--text);
  border-radius: 12px; padding: 11px 14px; outline: none;
}
.toolbar input { flex: 1; min-width: 160px; }
.check { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--muted); white-space: nowrap; }

.table-wrap { border: 1px solid var(--line); border-radius: 16px; overflow: auto; background: var(--panel); }
table { width: 100%; border-collapse: collapse; min-width: 900px; }
th, td { text-align: left; padding: 13px 14px; border-bottom: 1px solid var(--line); }
th { color: var(--muted); font-size: 13px; font-weight: 500; }
.mono { font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 13px; }
.badge { display: inline-block; padding: 2px 8px; border-radius: 999px; font-size: 12px; border: 1px solid var(--line); }
.badge.on { color: var(--accent-2); }
.badge.off { color: var(--muted); }
.badge.unavailable { color: var(--danger); }
.tip { margin: 0; font-size: 12px; color: var(--muted); line-height: 1.4; }
.actions { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; }
.primary, .ghost, .link { cursor: pointer; }
.primary { border: 0; border-radius: 12px; padding: 10px 14px; background: linear-gradient(135deg, var(--accent), #2f9a65); color: #04140c; font-weight: 600; }
.ghost { border: 1px solid var(--line); background: transparent; color: var(--text); border-radius: 12px; padding: 10px 14px; }
.ghost.sm { padding: 4px 10px; font-size: 12px; border-radius: 8px; }
.link { border: 0; background: transparent; color: var(--accent-2); padding: 0; text-decoration: none; }
.link.danger { color: var(--danger); }
.error { color: var(--danger); }
.empty { color: var(--muted); text-align: center; }
.mask { position: fixed; inset: 0; background: rgba(0,0,0,.55); display: grid; place-items: center; padding: 20px; z-index: 40; }
.modal { width: min(460px, 100%); max-height: min(86vh, 820px); overflow: auto; background: #102019; border: 1px solid var(--line); border-radius: 18px; padding: 22px; display: grid; gap: 12px; box-shadow: var(--shadow); }
.playback-modal { width: min(960px, 100%); max-height: min(92vh, 960px); overflow: auto; padding: 18px 20px 20px; }
.modal h2 { margin: 0; font-family: Syne, sans-serif; }
.modal label { display: grid; gap: 6px; }
.modal label span { font-size: 13px; color: var(--muted); }
.form-actions { display: flex; justify-content: flex-end; gap: 10px; }
</style>
