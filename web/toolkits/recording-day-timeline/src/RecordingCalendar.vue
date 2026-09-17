<template>
  <div ref="rootRef" class="rdt-cal" :class="{ 'rdt-cal--open': open }">
    <button
      type="button"
      class="rdt-cal-trigger"
      :disabled="loading"
      @click="toggleOpen"
    >
      <span class="rdt-cal-trigger-label">录像日期</span>
      <span class="rdt-cal-trigger-value">{{ modelValue || '选择日期' }}</span>
      <span class="rdt-cal-trigger-icon" aria-hidden="true">{{ open ? '▴' : '▾' }}</span>
    </button>

    <div v-show="open" class="rdt-cal-popover">
      <div class="rdt-cal-head">
        <button type="button" class="rdt-cal-nav" :disabled="loading" @click="prevMonth">‹</button>
        <span class="rdt-cal-title">{{ viewYear }}年{{ viewMonth }}月</span>
        <button type="button" class="rdt-cal-nav" :disabled="loading || !canNextMonth" @click="nextMonth">›</button>
      </div>

      <div class="rdt-cal-week">
        <span v-for="w in weekLabels" :key="w">{{ w }}</span>
      </div>

      <div class="rdt-cal-grid">
        <span v-for="blank in leadingBlanks" :key="'b-' + blank" class="rdt-cal-cell rdt-cal-cell--blank" />
        <button
          v-for="cell in monthCells"
          :key="cell.dateStr"
          type="button"
          class="rdt-cal-cell"
          :class="cellClass(cell)"
          :disabled="cell.disabled || loading"
          :title="cellTitle(cell)"
          @click="pick(cell)"
        >
          <span class="rdt-cal-day">{{ cell.day }}</span>
          <span v-if="cell.hasRecording" class="rdt-cal-dot" aria-hidden="true" />
        </button>
      </div>

      <div class="rdt-cal-legend">
        <span class="rdt-cal-legend-item"><i class="rdt-cal-dot rdt-cal-dot--legend" />有录像</span>
        <span class="rdt-cal-legend-item rdt-cal-legend-muted">灰色不可选</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  viewYear: { type: Number, required: true },
  viewMonth: { type: Number, required: true },
  recordingDays: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  maxDate: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue', 'navigate'])

const open = ref(false)
const rootRef = ref(null)

const weekLabels = ['日', '一', '二', '三', '四', '五', '六']

const recordingSet = computed(() => new Set(props.recordingDays || []))

const maxDateStr = computed(() => {
  if (props.maxDate) return props.maxDate
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
})

const canNextMonth = computed(() => {
  const maxY = Number(maxDateStr.value.slice(0, 4))
  const maxM = Number(maxDateStr.value.slice(5, 7))
  return props.viewYear < maxY || (props.viewYear === maxY && props.viewMonth < maxM)
})

const leadingBlanks = computed(() => {
  const first = new Date(props.viewYear, props.viewMonth - 1, 1)
  return first.getDay()
})

const monthCells = computed(() => {
  const y = props.viewYear
  const m = props.viewMonth
  const daysInMonth = new Date(y, m, 0).getDate()
  const cells = []
  for (let day = 1; day <= daysInMonth; day++) {
    const dateStr = `${y}-${String(m).padStart(2, '0')}-${String(day).padStart(2, '0')}`
    const hasRecording = recordingSet.value.has(dateStr)
    const isFuture = dateStr > maxDateStr.value
    cells.push({
      day,
      dateStr,
      hasRecording,
      isFuture,
      disabled: !hasRecording || isFuture
    })
  }
  return cells
})

function cellClass(cell) {
  return {
    'rdt-cal-cell--has': cell.hasRecording && !cell.isFuture,
    'rdt-cal-cell--none': !cell.hasRecording && !cell.isFuture,
    'rdt-cal-cell--future': cell.isFuture,
    'rdt-cal-cell--selected': cell.dateStr === props.modelValue,
    'rdt-cal-cell--today': cell.dateStr === maxDateStr.value
  }
}

function cellTitle(cell) {
  if (cell.isFuture) return '未来日期不可选'
  if (!cell.hasRecording) return '当天无录像'
  return '有录像，点击回放'
}

function toggleOpen() {
  open.value = !open.value
}

function close() {
  open.value = false
}

function pick(cell) {
  if (cell.disabled) return
  emit('update:modelValue', cell.dateStr)
  close()
}

function prevMonth() {
  let y = props.viewYear
  let m = props.viewMonth - 1
  if (m < 1) {
    m = 12
    y -= 1
  }
  emit('navigate', { year: y, month: m })
}

function nextMonth() {
  if (!canNextMonth.value) return
  let y = props.viewYear
  let m = props.viewMonth + 1
  if (m > 12) {
    m = 1
    y += 1
  }
  emit('navigate', { year: y, month: m })
}

function onDocPointerDown(e) {
  if (!open.value) return
  const el = rootRef.value
  if (el && !el.contains(e.target)) close()
}

onMounted(() => {
  document.addEventListener('pointerdown', onDocPointerDown)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocPointerDown)
})
</script>

<style scoped>
.rdt-cal {
  position: relative;
  display: inline-block;
  min-width: 200px;
}

.rdt-cal-trigger {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--rdt-line);
  border-radius: 10px;
  background: rgba(8, 16, 13, 0.65);
  color: var(--rdt-text);
  padding: 8px 10px;
  cursor: pointer;
  font-size: 13px;
  text-align: left;
}

.rdt-cal-trigger:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.rdt-cal-trigger-label {
  color: var(--rdt-muted);
  font-size: 12px;
  flex-shrink: 0;
}

.rdt-cal-trigger-value {
  flex: 1;
  font-weight: 600;
}

.rdt-cal-trigger-icon {
  color: var(--rdt-muted);
  font-size: 11px;
  flex-shrink: 0;
}

.rdt-cal-popover {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  z-index: 20;
  width: min(300px, 92vw);
  padding: 10px;
  border: 1px solid var(--rdt-line);
  border-radius: 12px;
  background: var(--rdt-panel, #14241c);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.35);
  display: grid;
  gap: 8px;
}

.rdt-cal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.rdt-cal-title {
  font-size: 14px;
  font-weight: 600;
}

.rdt-cal-nav {
  width: 32px;
  height: 32px;
  border: 1px solid var(--rdt-line);
  border-radius: 8px;
  background: transparent;
  color: var(--rdt-text);
  cursor: pointer;
  font-size: 18px;
  line-height: 1;
}

.rdt-cal-nav:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.rdt-cal-week {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4px;
  font-size: 11px;
  color: var(--rdt-muted);
  text-align: center;
}

.rdt-cal-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4px;
}

.rdt-cal-cell {
  position: relative;
  aspect-ratio: 1;
  min-height: 34px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: var(--rdt-text);
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  padding: 2px;
  font-size: 13px;
}

.rdt-cal-cell--blank {
  pointer-events: none;
  visibility: hidden;
}

.rdt-cal-cell--has {
  background: rgba(61, 186, 122, 0.12);
  border-color: rgba(61, 186, 122, 0.35);
}

.rdt-cal-cell--has:hover:not(:disabled) {
  background: rgba(61, 186, 122, 0.22);
}

.rdt-cal-cell--none,
.rdt-cal-cell--future {
  color: var(--rdt-muted);
  opacity: 0.45;
  cursor: not-allowed;
}

.rdt-cal-cell--selected {
  background: rgba(61, 186, 122, 0.35);
  border-color: var(--rdt-accent);
  font-weight: 700;
}

.rdt-cal-cell--today:not(.rdt-cal-cell--selected) {
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.25);
}

.rdt-cal-day {
  line-height: 1;
}

.rdt-cal-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--rdt-accent);
}

.rdt-cal-dot--legend {
  display: inline-block;
  vertical-align: middle;
  margin-right: 4px;
}

.rdt-cal-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 11px;
  color: var(--rdt-muted);
}

.rdt-cal-legend-item {
  display: inline-flex;
  align-items: center;
}

.rdt-cal-legend-muted {
  opacity: 0.75;
}
</style>
