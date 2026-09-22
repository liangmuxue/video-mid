<template>
  <AppShell>
    <section class="clip-page">
      <header class="clip-head">
        <div>
          <h1>录像片段截取</h1>
          <p class="sub">按设备编号 + 时间戳 + 秒数，截取前后各 N 秒并播放</p>
        </div>
      </header>

      <div class="clip-layout">
        <div class="panel form-panel">
          <div class="panel-title">
            <h2>截取参数</h2>
            <button type="button" class="ghost" @click="addRow">+ 添加一行</button>
          </div>

          <div v-for="(row, index) in rows" :key="row.key" class="form-row">
            <label class="field grow">
              <span>设备编号</span>
              <input
                v-model.trim="row.deviceId"
                list="device-id-list"
                placeholder="如 TIC7632_01"
                required
              />
            </label>
            <label class="field grow">
              <span>时间点</span>
              <input v-model="row.datetimeLocal" type="datetime-local" step="1" required />
            </label>
            <label class="field narrow">
              <span>秒数</span>
              <input v-model.number="row.seconds" type="number" min="1" max="150" />
            </label>
            <div class="row-actions">
              <button type="button" class="ghost" title="设为当前时间" @click="setNow(row)">现在</button>
              <button
                type="button"
                class="ghost danger"
                :disabled="rows.length <= 1"
                @click="removeRow(index)"
              >删</button>
            </div>
          </div>

          <datalist id="device-id-list">
            <option v-for="d in devices" :key="d.deviceId" :value="d.deviceId">
              {{ d.name || d.deviceId }}
            </option>
          </datalist>

          <p v-if="formError" class="error">{{ formError }}</p>

          <div class="form-actions">
            <button type="button" class="primary" :disabled="loading" @click="submit">
              {{ loading ? '截取中…' : '开始截取' }}
            </button>
            <button type="button" class="ghost" :disabled="loading" @click="resetForm">清空</button>
          </div>
        </div>

        <div class="panel result-panel">
          <h2>截取结果</h2>
          <p v-if="!results.length && !loading" class="muted">提交参数后在此显示 videoUrl、起止时间</p>

          <ul v-if="results.length" class="result-list">
            <li
              v-for="(item, idx) in results"
              :key="idx"
              class="result-item"
              :class="{ active: activeIndex === idx, fail: !item.ok }"
              @click="selectResult(idx)"
            >
              <div class="result-top">
                <span class="mono">{{ item.deviceId || '-' }}</span>
                <span class="badge" :class="item.ok ? 'ok' : 'err'">{{ item.ok ? '成功' : '失败' }}</span>
              </div>
              <template v-if="item.ok">
                <p class="time-line">
                  {{ fmtMs(item.startTime) }}
                  <span class="arrow">→</span>
                  {{ fmtMs(item.endTime) }}
                </p>
                <p class="meta">时长 {{ item.durationSeconds ?? '-' }}s · 前后各 {{ item.seconds }}s</p>
              </template>
              <p v-else class="error inline">{{ item.error }}</p>
            </li>
          </ul>

          <div v-if="activeUrl" class="player-wrap">
            <div class="player-head">
              <span>正在播放</span>
              <a :href="activeUrl" target="_blank" rel="noopener" class="link">新窗口打开</a>
            </div>
            <video
              ref="playerRef"
              class="clip-video"
              controls
              playsinline
              preload="metadata"
              :src="activeUrl"
            />
          </div>
        </div>
      </div>
    </section>
  </AppShell>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import AppShell from '../components/AppShell.vue'
import { fetchDevices, fetchRecordingClipsBatch } from '../api/device'

let rowSeq = 0

const devices = ref([])
const rows = ref([createRow()])
const results = ref([])
const loading = ref(false)
const formError = ref('')
const activeIndex = ref(-1)
const activeUrl = ref('')
const playerRef = ref(null)

function createRow() {
  return {
    key: ++rowSeq,
    deviceId: '',
    datetimeLocal: toDatetimeLocal(Date.now()),
    seconds: 30
  }
}

function toDatetimeLocal(ms) {
  const d = new Date(ms)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function localToMs(local) {
  if (!local) return null
  const ms = new Date(local).getTime()
  return Number.isFinite(ms) ? ms : null
}

function fmtMs(ms) {
  if (ms == null) return '-'
  return new Date(ms).toLocaleString('zh-CN', { hour12: false })
}

function authVideoUrl(url) {
  if (!url) return ''
  const token = localStorage.getItem('video_mid_token')
  if (!token) return url
  return `${url}${url.includes('?') ? '&' : '?'}token=${encodeURIComponent(token)}`
}

function addRow() {
  rows.value.push(createRow())
}

function removeRow(index) {
  if (rows.value.length <= 1) return
  rows.value.splice(index, 1)
}

function setNow(row) {
  row.datetimeLocal = toDatetimeLocal(Date.now())
}

function resetForm() {
  rows.value = [createRow()]
  results.value = []
  activeIndex.value = -1
  activeUrl.value = ''
  formError.value = ''
}

function buildPayload() {
  const items = []
  for (const row of rows.value) {
    const deviceId = row.deviceId?.trim()
    const at = localToMs(row.datetimeLocal)
    const seconds = row.seconds ?? 30
    if (!deviceId) {
      throw new Error('请填写设备编号')
    }
    if (at == null) {
      throw new Error(`设备 ${deviceId} 的时间点无效`)
    }
    if (!Number.isFinite(seconds) || seconds <= 0) {
      throw new Error(`设备 ${deviceId} 的秒数必须大于 0`)
    }
    items.push({ deviceId, at, seconds })
  }
  if (!items.length) {
    throw new Error('请至少添加一条截取参数')
  }
  return items
}

async function submit() {
  formError.value = ''
  let items
  try {
    items = buildPayload()
  } catch (e) {
    formError.value = e.message
    return
  }

  loading.value = true
  results.value = []
  activeIndex.value = -1
  activeUrl.value = ''

  try {
    const list = await fetchRecordingClipsBatch(items)
    results.value = Array.isArray(list) ? list : []
    const firstOk = results.value.findIndex((r) => r.ok && r.videoUrl)
    if (firstOk >= 0) {
      selectResult(firstOk)
    }
  } catch (e) {
    formError.value = e.message || '截取失败'
  } finally {
    loading.value = false
  }
}

function selectResult(index) {
  const item = results.value[index]
  if (!item?.ok || !item.videoUrl) return
  activeIndex.value = index
  activeUrl.value = authVideoUrl(item.videoUrl)
  playerRef.value?.load()
}

onMounted(async () => {
  try {
    devices.value = (await fetchDevices()) || []
  } catch {
    devices.value = []
  }
})
</script>

<style scoped>
.clip-page { display: grid; gap: 20px; }
.clip-head h1 {
  margin: 0;
  font-family: Syne, sans-serif;
  font-size: 28px;
}
.sub { margin: 6px 0 0; color: var(--muted); }

.clip-layout {
  display: grid;
  grid-template-columns: minmax(320px, 1fr) minmax(360px, 1.2fr);
  gap: 16px;
  align-items: start;
}
@media (max-width: 960px) {
  .clip-layout { grid-template-columns: 1fr; }
}

.panel {
  border: 1px solid var(--line);
  border-radius: 16px;
  background: var(--panel);
  padding: 18px;
}
.panel h2 {
  margin: 0;
  font-size: 18px;
  font-family: Syne, sans-serif;
}
.panel-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.form-row {
  display: grid;
  grid-template-columns: 1.2fr 1.4fr 88px auto;
  gap: 10px;
  align-items: end;
  margin-bottom: 10px;
}
@media (max-width: 720px) {
  .form-row { grid-template-columns: 1fr; }
}

.field { display: grid; gap: 6px; }
.field span { font-size: 12px; color: var(--muted); }
.field input, .field select {
  width: 100%;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.2);
  color: var(--text);
  padding: 10px 12px;
}
.row-actions { display: flex; gap: 6px; padding-bottom: 2px; }

.form-actions { display: flex; gap: 10px; margin-top: 14px; }
.primary, .ghost {
  border-radius: 10px;
  padding: 10px 16px;
  cursor: pointer;
  border: 1px solid var(--line);
}
.primary {
  background: linear-gradient(135deg, var(--accent), #2f9a65);
  color: #04140c;
  border: none;
  font-weight: 600;
}
.ghost { background: transparent; color: var(--text); }
.ghost.danger { color: #f07178; }
.primary:disabled, .ghost:disabled { opacity: 0.55; cursor: not-allowed; }

.result-list {
  list-style: none;
  margin: 0 0 16px;
  padding: 0;
  display: grid;
  gap: 8px;
  max-height: 280px;
  overflow: auto;
}
.result-item {
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 10px 12px;
  cursor: pointer;
  background: rgba(0, 0, 0, 0.15);
}
.result-item.active { border-color: var(--accent); background: rgba(61, 186, 122, 0.12); }
.result-item.fail { cursor: default; }
.result-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.mono { font-family: ui-monospace, monospace; font-size: 13px; }
.badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid var(--line);
}
.badge.ok { color: var(--accent); border-color: rgba(61, 186, 122, 0.5); }
.badge.err { color: #f07178; border-color: rgba(240, 113, 120, 0.5); }
.time-line { margin: 6px 0 0; font-size: 13px; color: var(--text); }
.arrow { color: var(--muted); margin: 0 4px; }
.meta { margin: 4px 0 0; font-size: 12px; color: var(--muted); }

.player-wrap { border-top: 1px solid var(--line); padding-top: 14px; }
.player-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  color: var(--muted);
  font-size: 13px;
}
.clip-video {
  width: 100%;
  max-height: 360px;
  background: #000;
  border-radius: 12px;
}
.link {
  color: var(--accent-2);
  background: none;
  border: none;
  cursor: pointer;
  text-decoration: none;
}
.error { color: #f07178; margin: 8px 0 0; }
.error.inline { margin: 6px 0 0; font-size: 13px; }
.muted { color: var(--muted); margin: 0; }
</style>
