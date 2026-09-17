/**
 * RecordingDayTimeline — 全天 24h 历史录像进度条（独立工具包）
 */
<template>
  <div class="rdt-root" data-rdt="recording-day-timeline">
    <div class="rdt-toolbar">
      <RecordingCalendar
        :model-value="date"
        :view-year="viewYear"
        :view-month="viewMonth"
        :recording-days="recordingDays"
        :loading="daysLoading || loading"
        @update:model-value="onDatePick"
        @navigate="onMonthNavigate"
      />
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

    <p v-if="daysLoading" class="rdt-muted">加载录像日历…</p>
    <p v-else-if="error" class="rdt-error">{{ error }}</p>
    <p v-else-if="loading" class="rdt-muted">加载录像中…</p>
    <p v-else-if="!recordingDays.length" class="rdt-muted">当前月份无录像，请切换月份</p>
    <p v-else-if="!segments.length" class="rdt-muted">所选日期无录像，请点选日历中有绿点的日期</p>

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
import RecordingCalendar from './RecordingCalendar.vue'
import { shouldPrefetchNext } from './onDemandPlay.js'
import {
  DAY_SECONDS,
  buildDaySegments,
  dayBounds,
  earliestSegment,
  formatClock,
  formatDateStr,
  hitSegment,
  monthBounds,
  parseRecordTimestamp,
  pickRecordAt,
  secondsOfDay,
  todayStr
} from './timeUtils.js'

const props = defineProps({
  deviceId: { type: String, required: true },
  initialDate: { type: String, default: '' },
  clipSeconds: { type: Number, default: 300 },
  fetchRecordings: { type: Function, required: true },
  /** (deviceId, { year, month }) => Promise<string[]> 有录像的 yyyy-MM-dd */
  fetchRecordingDays: { type: Function, default: null },
  getVideoUrl: { type: Function, required: true },
  showPlayer: { type: Boolean, default: true }
})

const emit = defineEmits(['play', 'seek', 'loaded', 'error', 'date-change'])

const date = ref(props.initialDate || todayStr())
const viewYear = ref(parseYear(date.value))
const viewMonth = ref(parseMonth(date.value))
const recordingDays = ref([])
const daysLoading = ref(false)
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

function parseYear(dateStr) {
  return Number(String(dateStr).slice(0, 4)) || new Date().getFullYear()
}

function parseMonth(dateStr) {
  return Number(String(dateStr).slice(5, 7)) || new Date().getMonth() + 1
}

async function queryRecordingDays(year, month) {
  if (props.fetchRecordingDays) {
    const list = await props.fetchRecordingDays(props.deviceId, { year, month })
    return Array.isArray(list) ? list : []
  }
  const { from, to } = monthBounds(year, month)
  const list = await props.fetchRecordings(props.deviceId, { from, to })
  const days = new Set()
  for (const item of list || []) {
    const dt = parseRecordTimestamp(item.recordTime || item.timestamp)
    if (dt) days.add(formatDateStr(dt))
  }
  return [...days].sort()
}

async function loadRecordingDays(year, month) {
  daysLoading.value = true
  try {
    recordingDays.value = await queryRecordingDays(year, month)
  } catch (e) {
    recordingDays.value = []
    error.value = e?.message || '加载录像日历失败'
  } finally {
    daysLoading.value = false
  }
}

function pickBestDate(days) {
  if (!days?.length) return null
  const today = todayStr()
  const eligible = days.filter((d) => d <= today).sort()
  return eligible.pop() || days.sort().pop()
}

async function initCalendar() {
  error.value = ''
  let y = parseYear(date.value)
  let m = parseMonth(date.value)
  viewYear.value = y
  viewMonth.value = m
  await loadRecordingDays(y, m)
  if (recordingDays.value.includes(date.value)) {
    await reload()
    return
  }
  const picked = pickBestDate(recordingDays.value)
  if (picked) {
    date.value = picked
    emit('date-change', date.value)
    await reload()
    return
  }
  for (let i = 0; i < 11; i++) {
    m -= 1
    if (m < 1) {
      m = 12
      y -= 1
    }
    await loadRecordingDays(y, m)
    const back = pickBestDate(recordingDays.value)
    if (back) {
      viewYear.value = y
      viewMonth.value = m
      date.value = back
      emit('date-change', date.value)
      await reload()
      return
    }
  }
  records.value = []
}

async function reload() {
  if (!props.deviceId) {
    error.value = '缺少 deviceId'
    return
  }
  if (!recordingDays.value.includes(date.value)) {
    records.value = []
    playheadSec.value = null
    currentUrl.value = ''
    currentRecord.value = null
    onDemandRef.value?.stopAndUnload?.()
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

function onDatePick(nextDate) {
  if (!nextDate || nextDate === date.value) return
  date.value = nextDate
  emit('date-change', date.value)
  reload()
}

async function onMonthNavigate({ year, month }) {
  viewYear.value = year
  viewMonth.value = month
  await loadRecordingDays(year, month)
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
    initCalendar()
  }
)

watch(
  () => props.initialDate,
  (v) => {
    if (v && v !== date.value) {
      date.value = v
      viewYear.value = parseYear(v)
      viewMonth.value = parseMonth(v)
      initCalendar()
    }
  }
)

onMounted(initCalendar)

defineExpose({ reload, playAt, segments, date })
</script>

<style scoped>
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

.rdt-range-hint {
  font-size: 12px;
  color: var(--rdt-muted);
  padding-bottom: 8px;
  flex: 1;
  min-width: 160px;
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
