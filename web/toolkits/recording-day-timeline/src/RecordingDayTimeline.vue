/**
 * RecordingDayTimeline — 全天 24h 历史录像进度条（独立工具包）
 *
 * 样式全部挂在 .rdt-root 下，不污染外部。
 * 不包含起止时间选择框（仅日期）；不写录像增删改。
 */
<template>
  <div class="rdt-root" data-rdt="recording-day-timeline">
    <div class="rdt-toolbar">
      <label class="rdt-date">
        <span class="rdt-label">录像日期</span>
        <input
          class="rdt-date-input"
          type="date"
          :value="date"
          :disabled="loading"
          @change="onDateChange"
        />
      </label>
      <div class="rdt-range-hint">{{ date }} 00:00:00 ~ 23:59:59</div>
      <button
        type="button"
        class="rdt-play-btn"
        :disabled="!canPlay || loading"
        @click="onPlayClick"
      >
        播放
      </button>
      <button
        type="button"
        class="rdt-refresh-btn"
        :disabled="loading || !deviceId"
        @click="reload"
      >
        刷新
      </button>
    </div>

    <p v-if="error" class="rdt-error">{{ error }}</p>
    <p v-else-if="loading" class="rdt-muted">加载录像中…</p>
    <p v-else-if="!segments.length" class="rdt-muted">当天无录像，进度条不可操作</p>

    <!-- 播放器壳：画面在上，全天 0-24h 进度条在最底部（替换原生分片进度） -->
    <div class="rdt-player-shell" :class="{ 'rdt-player-shell--empty': !showPlayer }">
      <div v-if="showPlayer" class="rdt-player-stage">
        <OnDemandVideoPlayer
          ref="onDemandRef"
          @timeupdate="onTimeUpdate"
          @ended="onEnded"
        />
        <div class="rdt-player-overlay">
          <button
            type="button"
            class="rdt-overlay-play"
            :disabled="!canPlay || loading"
            @click="onOverlayPlayToggle"
          >
            {{ isPlaying ? '暂停' : '播放' }}
          </button>
        </div>
      </div>

      <div class="rdt-player-footer">
        <DayTimelineBar
          :segments="segments"
          :playhead-sec="playheadSec"
          :disabled="!canPlay || loading"
          @seek="onTimelineSeek"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import OnDemandVideoPlayer from './OnDemandVideoPlayer.vue'
import DayTimelineBar from './DayTimelineBar.vue'
import { shouldPrefetchNext } from './onDemandPlay.js'
import {
  DAY_SECONDS,
  buildDaySegments,
  dayBounds,
  earliestSegment,
  formatClock,
  hitSegment,
  parseRecordTimestamp,
  pickRecordAt,
  secondsOfDay
} from './timeUtils.js'

const props = defineProps({
  /** 设备编码 */
  deviceId: { type: String, required: true },
  /** 初始日期 yyyy-MM-dd */
  initialDate: { type: String, default: '' },
  /** 单段默认时长（秒），接口无 endTime 时使用 */
  clipSeconds: { type: Number, default: 300 },
  /** (deviceId, { from, to }) => Promise<record[]> */
  fetchRecordings: { type: Function, required: true },
  /** (deviceId, fileName, record) => string 可播放 URL */
  getVideoUrl: { type: Function, required: true },
  /** 是否内置 video 播放器 */
  showPlayer: { type: Boolean, default: true }
})

const emit = defineEmits(['play', 'seek', 'loaded', 'error', 'date-change'])

const date = ref(props.initialDate || todayStr())
const loading = ref(false)
const error = ref('')
const records = ref([])
const playheadSec = ref(null)
const currentUrl = ref('')
const currentRecord = ref(null)
const onDemandRef = ref(null)
const isPlaying = ref(false)

const segments = computed(() =>
  buildDaySegments(records.value, date.value, props.clipSeconds)
)

const canPlay = computed(() => segments.value.length > 0)

function todayStr() {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

async function reload() {
  if (!props.deviceId) {
    error.value = '缺少 deviceId'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const { from, to } = dayBounds(date.value)
    const list = await props.fetchRecordings(props.deviceId, { from, to })
    records.value = Array.isArray(list) ? list : []
    if (!records.value.length) {
      playheadSec.value = null
      currentUrl.value = ''
      currentRecord.value = null
      onDemandRef.value?.stopAndUnload?.()
    }
    emit('loaded', { date: date.value, records: records.value, segments: segments.value })
  } catch (e) {
    error.value = e?.message || '加载录像失败'
    records.value = []
    emit('error', e)
  } finally {
    loading.value = false
  }
}

function onDateChange(e) {
  date.value = e.target.value
  emit('date-change', date.value)
  reload()
}

function resolveUrl(record) {
  if (!record) return ''
  if (record.videoUrl) return record.videoUrl
  return props.getVideoUrl(props.deviceId, record.fileName, record) || ''
}

function seekInFile(record, daySec) {
  const start = parseRecordTimestamp(record.recordTime || record.timestamp)
  if (!start) return 0
  const startSec = secondsOfDay(start)
  return Math.max(0, daySec - startSec)
}

function playAt(daySec, segment) {
  const seg = segment || hitSegment(segments.value, daySec)
  if (!seg) return
  const record = pickRecordAt(seg, daySec)
  if (!record) return
  // 按需：只解析「当前时段」这一条 URL，不预取其它片段
  const url = resolveUrl(record)
  const fileSeek = seekInFile(record, daySec)
  playheadSec.value = daySec
  currentRecord.value = record
  currentUrl.value = url

  const payload = {
    deviceId: props.deviceId,
    date: date.value,
    daySec,
    clock: formatClock(daySec),
    record,
    videoUrl: url,
    seekSeconds: fileSeek,
    onDemand: true
  }
  emit('play', payload)
  emit('seek', payload)

  if (props.showPlayer) {
    nextTick(() => {
      onDemandRef.value?.playOne?.(url, fileSeek)?.then?.(() => {
        isPlaying.value = true
      })
    })
  }
}

function onPlayClick() {
  const first = earliestSegment(segments.value)
  if (!first) return
  playAt(first.startSec, first)
}

function onOverlayPlayToggle() {
  const el = onDemandRef.value?.getElement?.()
  if (el && currentUrl.value && !el.paused) {
    el.pause()
    isPlaying.value = false
    return
  }
  if (el && currentUrl.value && el.paused && el.src) {
    el.play().then(() => {
      isPlaying.value = true
    }).catch(() => {})
    return
  }
  onPlayClick()
}

function onTimelineSeek(sec, seg) {
  const hit = seg || hitSegment(segments.value, sec)
  if (!hit) return
  playAt(sec, hit)
}

function onTimeUpdate() {
  const el = onDemandRef.value?.getElement?.()
  const record = currentRecord.value
  if (!el || !record) return
  isPlaying.value = !el.paused
  const start = parseRecordTimestamp(record.recordTime || record.timestamp)
  if (!start) return
  playheadSec.value = Math.min(DAY_SECONDS - 1, secondsOfDay(start) + (el.currentTime || 0))
}

function onEnded() {
  isPlaying.value = false
  if (shouldPrefetchNext()) return
  const cur = playheadSec.value
  if (cur == null) return
  const next = segments.value.find((s) => s.startSec > cur + 0.5)
  if (next) playAt(next.startSec, next)
}

watch(
  () => props.deviceId,
  () => {
    reload()
  }
)

watch(
  () => props.initialDate,
  (v) => {
    if (v && v !== date.value) {
      date.value = v
      reload()
    }
  }
)

onMounted(reload)

defineExpose({ reload, playAt, segments, date })
</script>

<style scoped>
/* 全部样式限制在工具包根节点，类名统一 rdt- 前缀 */
.rdt-root {
  --rdt-bg: #0f1a15;
  --rdt-panel: #14241c;
  --rdt-line: rgba(140, 180, 150, 0.28);
  --rdt-muted: #8aa396;
  --rdt-text: #e8f2ec;
  --rdt-accent: #3dba7a;
  --rdt-empty: #2a3530;
  --rdt-danger: #e07070;
  box-sizing: border-box;
  color: var(--rdt-text);
  background: var(--rdt-panel);
  border: 1px solid var(--rdt-line);
  border-radius: 16px;
  padding: 16px;
  display: grid;
  gap: 12px;
  font-family: ui-sans-serif, system-ui, -apple-system, 'Segoe UI', sans-serif;
}

.rdt-root *,
.rdt-root *::before,
.rdt-root *::after {
  box-sizing: border-box;
}

.rdt-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: flex-end;
}

.rdt-date {
  display: grid;
  gap: 6px;
}

.rdt-label {
  font-size: 12px;
  color: var(--rdt-muted);
}

.rdt-date-input {
  border: 1px solid var(--rdt-line);
  background: rgba(8, 16, 13, 0.65);
  color: var(--rdt-text);
  border-radius: 10px;
  padding: 8px 10px;
  min-width: 160px;
}

.rdt-range-hint {
  font-size: 12px;
  color: var(--rdt-muted);
  padding-bottom: 8px;
}

.rdt-play-btn,
.rdt-refresh-btn {
  border-radius: 10px;
  padding: 8px 14px;
  cursor: pointer;
  font-size: 13px;
  border: 1px solid var(--rdt-line);
}

.rdt-play-btn {
  border: 0;
  background: linear-gradient(135deg, var(--rdt-accent), #2f9a65);
  color: #04140c;
  font-weight: 600;
}

.rdt-play-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.rdt-refresh-btn {
  background: transparent;
  color: var(--rdt-text);
}

.rdt-refresh-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.rdt-error {
  margin: 0;
  color: var(--rdt-danger);
  font-size: 13px;
}

.rdt-muted {
  margin: 0;
  color: var(--rdt-muted);
  font-size: 13px;
}

/* 播放器壳：画面 + 底部影视风格时间轴 */
.rdt-player-shell {
  border: 1px solid var(--rdt-line);
  border-radius: 14px;
  overflow: hidden;
  background: #000;
}

.rdt-player-shell--empty {
  background: var(--rdt-panel);
}

.rdt-player-stage {
  position: relative;
  background: #000;
}

.rdt-player-overlay {
  position: absolute;
  right: 12px;
  bottom: 12px;
  z-index: 2;
  pointer-events: none;
}

.rdt-overlay-play {
  pointer-events: auto;
  border: 0;
  border-radius: 999px;
  padding: 8px 14px;
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
  background: rgba(61, 186, 122, 0.92);
  color: #04140c;
}

.rdt-overlay-play:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.rdt-player-footer {
  background: #0c1210;
  border-top: 1px solid var(--rdt-line);
  padding: 8px 10px 10px;
}
</style>
