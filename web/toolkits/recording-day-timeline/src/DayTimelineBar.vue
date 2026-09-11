<!--
  影视播放器风格全天时间轴：
  - 滚轮缩放、拖拽框选放大、空白处拖拽平移、双击还原全天
  - 放大后显示分/秒刻度，可点选到分秒
  - 灰区不可点；仅有录像绿段可点 / 可按住拖拽滑块
-->
<template>
  <div class="rdt-tl" data-rdt-timeline="zoomable">
    <div class="rdt-tl-meta">
      <span class="rdt-tl-clock">{{ playheadLabel }}</span>
      <span class="rdt-tl-window">{{ windowLabel }}</span>
      <button type="button" class="rdt-tl-reset" title="双击时间轴也可还原" @click="resetView">
        全天
      </button>
    </div>

    <div
      ref="railRef"
      class="rdt-tl-rail"
      :class="{ 'rdt-tl-rail--scrubbing': dragMode === 'scrub' }"
      @wheel.prevent="onWheel"
      @mousedown="onRailDown"
      @dblclick.prevent="resetView"
    >
      <!-- 灰底：全视窗，不接收点击（由上层框选/平移处理） -->
      <div class="rdt-tl-empty" aria-hidden="true" />

      <!-- 有录像段：按住拖拽滑块选时 -->
      <button
        v-for="(seg, idx) in visibleSegments"
        :key="`${seg.startSec}-${idx}`"
        type="button"
        class="rdt-tl-seg"
        :style="{ left: seg.leftPct + '%', width: Math.max(seg.widthPct, 0.12) + '%' }"
        :title="`${formatClock(seg.startSec)} ~ ${formatClock(seg.endSec)}`"
        @mousedown.stop.prevent="onSegDown($event, seg)"
      />

      <!-- 刻度 -->
      <div class="rdt-tl-ticks" aria-hidden="true">
        <span
          v-for="t in ticks"
          :key="t.sec + '-' + t.label"
          class="rdt-tl-tick"
          :class="{ 'rdt-tl-tick--major': t.major }"
          :style="{ left: secToLeftPct(t.sec, viewStart, viewEnd) + '%' }"
        >
          <i class="rdt-tl-tick-line" />
          <em v-if="t.label" class="rdt-tl-tick-label">{{ t.label }}</em>
        </span>
      </div>

      <!-- 播放头滑块：仅落在绿段时可拖 -->
      <div
        v-if="displayPlayhead != null && displayPlayhead >= viewStart && displayPlayhead <= viewEnd"
        class="rdt-tl-playhead"
        :class="{ 'rdt-tl-playhead--grab': playheadOnGreen }"
        :style="{ left: secToLeftPct(displayPlayhead, viewStart, viewEnd) + '%' }"
        @mousedown.stop.prevent="onPlayheadDown"
      />

      <!-- 框选层 -->
      <div
        v-if="box"
        class="rdt-tl-box"
        :style="{ left: box.leftPct + '%', width: box.widthPct + '%' }"
      />
    </div>

    <div class="rdt-tl-hint">
      滚轮缩放 · 绿段拖拽滑块选时 · 拖拽框选放大 · Alt/Shift+拖拽平移 · 双击还原全天
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { formatClock, hitSegment } from './timeUtils.js'
import {
  DAY_SECONDS,
  buildTicks,
  clientXToSec,
  panView,
  secToLeftPct,
  widthPct,
  zoomAt,
  zoomToRange
} from './timelineZoom.js'

const props = defineProps({
  segments: { type: Array, default: () => [] },
  playheadSec: { type: Number, default: null },
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['seek'])

const railRef = ref(null)
const viewStart = ref(0)
const viewEnd = ref(DAY_SECONDS)

const box = ref(null)
/** 拖拽滑块时的本地预览秒，优先于 props.playheadSec */
const scrubSec = ref(null)

const dragMode = ref(null) // 'box' | 'pan' | 'scrub'
let dragOriginX = 0
let dragOriginSec = 0
let dragStartView = null
let moved = false
let scrubRaf = 0
let pendingScrub = null

const ticks = computed(() => buildTicks(viewStart.value, viewEnd.value))

const visibleSegments = computed(() => {
  const a = viewStart.value
  const b = viewEnd.value
  return (props.segments || [])
    .filter((s) => s.endSec > a && s.startSec < b)
    .map((s) => {
      const start = Math.max(s.startSec, a)
      const end = Math.min(s.endSec, b)
      return {
        ...s,
        leftPct: secToLeftPct(start, a, b),
        widthPct: widthPct(start, end, a, b)
      }
    })
})

const displayPlayhead = computed(() =>
  scrubSec.value != null ? scrubSec.value : props.playheadSec
)

const playheadOnGreen = computed(() => {
  if (displayPlayhead.value == null) return false
  return !!hitSegment(props.segments, displayPlayhead.value)
})

const playheadLabel = computed(() =>
  displayPlayhead.value == null ? '--:--:--' : formatClock(displayPlayhead.value)
)

const windowLabel = computed(
  () => `${formatClock(viewStart.value)} — ${formatClock(Math.min(DAY_SECONDS - 1, viewEnd.value))}`
)

function resetView() {
  viewStart.value = 0
  viewEnd.value = DAY_SECONDS
  box.value = null
}

function onWheel(e) {
  if (props.disabled) return
  if (dragMode.value === 'scrub') return
  const rect = railRef.value?.getBoundingClientRect()
  const anchor = clientXToSec(e.clientX, rect, viewStart.value, viewEnd.value)
  const factor = e.deltaY > 0 ? 1.18 : 1 / 1.18
  const next = zoomAt(viewStart.value, viewEnd.value, anchor, factor)
  viewStart.value = next.viewStart
  viewEnd.value = next.viewEnd
}

function bindDrag() {
  window.addEventListener('mousemove', onDragMove)
  window.addEventListener('mouseup', onDragUp)
}

function unbindDrag() {
  window.removeEventListener('mousemove', onDragMove)
  window.removeEventListener('mouseup', onDragUp)
}

/** 仅绿段生效：灰区直接忽略 */
function scrubAtClientX(clientX, { immediate = false } = {}) {
  const rect = railRef.value?.getBoundingClientRect()
  const sec = clientXToSec(clientX, rect, viewStart.value, viewEnd.value)
  const hit = hitSegment(props.segments, sec)
  if (!hit) return false

  const clamped = Math.min(Math.max(sec, hit.startSec), Math.max(hit.startSec, hit.endSec - 0.001))
  scrubSec.value = clamped

  if (immediate) {
    if (scrubRaf) {
      cancelAnimationFrame(scrubRaf)
      scrubRaf = 0
    }
    pendingScrub = null
    emit('seek', clamped, hit)
    return true
  }

  pendingScrub = { sec: clamped, hit }
  if (!scrubRaf) {
    scrubRaf = requestAnimationFrame(() => {
      scrubRaf = 0
      if (!pendingScrub) return
      const p = pendingScrub
      pendingScrub = null
      emit('seek', p.sec, p.hit)
    })
  }
  return true
}

function startScrub(clientX) {
  dragMode.value = 'scrub'
  moved = false
  dragOriginX = clientX
  box.value = null
  scrubAtClientX(clientX, { immediate: true })
  bindDrag()
}

function onSegDown(e) {
  if (props.disabled) return
  if (e.button !== 0) return
  // 绿段：按住拖拽滑块；灰区不会走到这里
  startScrub(e.clientX)
}

function onPlayheadDown(e) {
  if (props.disabled) return
  if (e.button !== 0) return
  // 滑块落在灰区时禁止拖拽
  if (!playheadOnGreen.value) return
  startScrub(e.clientX)
}

function onRailDown(e) {
  if (props.disabled) return
  if (e.button !== 0) return
  // 灰区 / 空白：仅框选或平移，绝不 scrub
  const rect = railRef.value?.getBoundingClientRect()
  dragOriginX = e.clientX
  dragOriginSec = clientXToSec(e.clientX, rect, viewStart.value, viewEnd.value)
  dragStartView = { a: viewStart.value, b: viewEnd.value }
  moved = false
  dragMode.value = e.altKey || e.shiftKey ? 'pan' : 'box'
  box.value = null
  bindDrag()
}

function onDragMove(e) {
  const rect = railRef.value?.getBoundingClientRect()
  if (!rect) return
  const dx = e.clientX - dragOriginX
  if (Math.abs(dx) > 3) moved = true

  if (dragMode.value === 'scrub') {
    // 拖到灰区：不生效（保持上次绿段选中）
    scrubAtClientX(e.clientX)
    return
  }

  if (dragMode.value === 'pan' && dragStartView) {
    const span = dragStartView.b - dragStartView.a
    const deltaSec = -(dx / rect.width) * span
    const next = panView(dragStartView.a, dragStartView.b, deltaSec)
    viewStart.value = next.viewStart
    viewEnd.value = next.viewEnd
    return
  }

  if (dragMode.value === 'box') {
    const cur = clientXToSec(e.clientX, rect, viewStart.value, viewEnd.value)
    const a = Math.min(dragOriginSec, cur)
    const b = Math.max(dragOriginSec, cur)
    box.value = {
      leftPct: secToLeftPct(a, viewStart.value, viewEnd.value),
      widthPct: widthPct(a, b, viewStart.value, viewEnd.value)
    }
  }
}

function onDragUp(e) {
  unbindDrag()
  const rect = railRef.value?.getBoundingClientRect()
  const mode = dragMode.value

  if (mode === 'scrub') {
    scrubAtClientX(e.clientX, { immediate: true })
    scrubSec.value = null
  } else if (mode === 'box' && moved && box.value) {
    const cur = clientXToSec(e.clientX, rect, viewStart.value, viewEnd.value)
    const next = zoomToRange(dragOriginSec, cur)
    if (Math.abs(cur - dragOriginSec) > (viewEnd.value - viewStart.value) * 0.02) {
      viewStart.value = next.viewStart
      viewEnd.value = next.viewEnd
    }
  } else if (mode === 'box' && !moved) {
    // 单击灰区：不 seek；绿段由 seg mousedown 处理，不会走到这里
    const sec = clientXToSec(e.clientX, rect, viewStart.value, viewEnd.value)
    const hit = hitSegment(props.segments, sec)
    if (hit) emit('seek', sec, hit)
  }

  box.value = null
  dragMode.value = null
  dragStartView = null
}

function onKeyReset(e) {
  if (e.key === 'Escape') resetView()
}

if (typeof window !== 'undefined') {
  window.addEventListener('keydown', onKeyReset)
}

onBeforeUnmount(() => {
  unbindDrag()
  window.removeEventListener('keydown', onKeyReset)
  if (scrubRaf) cancelAnimationFrame(scrubRaf)
})

defineExpose({ resetView, viewStart, viewEnd })
</script>

<style scoped>
.rdt-tl {
  --rdt-tl-bg: #0c1210;
  --rdt-tl-empty: #2a3330;
  --rdt-tl-seg: #3dba7a;
  --rdt-tl-line: rgba(160, 190, 170, 0.28);
  --rdt-tl-muted: #8aa396;
  --rdt-tl-text: #e8f2ec;
  display: grid;
  gap: 6px;
  user-select: none;
}

.rdt-tl-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: var(--rdt-tl-muted);
}

.rdt-tl-clock {
  color: var(--rdt-tl-text);
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  min-width: 68px;
}

.rdt-tl-window {
  flex: 1;
  font-variant-numeric: tabular-nums;
}

.rdt-tl-reset {
  border: 1px solid var(--rdt-tl-line);
  background: transparent;
  color: var(--rdt-tl-text);
  border-radius: 8px;
  padding: 2px 8px;
  cursor: pointer;
  font-size: 12px;
}

.rdt-tl-rail {
  position: relative;
  height: 54px;
  border-radius: 8px;
  background: linear-gradient(180deg, #141c18, #0e1512);
  border: 1px solid var(--rdt-tl-line);
  overflow: hidden;
  cursor: crosshair;
}

.rdt-tl-rail--scrubbing {
  cursor: ew-resize;
}

.rdt-tl-empty {
  position: absolute;
  inset: 14px 0 auto 0;
  height: 16px;
  background: repeating-linear-gradient(
    -45deg,
    var(--rdt-tl-empty),
    var(--rdt-tl-empty) 4px,
    #24302c 4px,
    #24302c 8px
  );
  border-radius: 4px;
  margin: 0 2px;
  pointer-events: none;
  opacity: 0.95;
}

.rdt-tl-seg {
  position: absolute;
  top: 14px;
  height: 16px;
  padding: 0;
  margin: 0;
  border: 0;
  border-radius: 3px;
  background: linear-gradient(180deg, #55d492, #2f9a65);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.12);
  cursor: ew-resize;
  z-index: 2;
}

.rdt-tl-seg:hover {
  filter: brightness(1.1);
}

.rdt-tl-ticks {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 1;
}

.rdt-tl-tick {
  position: absolute;
  top: 0;
  bottom: 0;
  transform: translateX(-50%);
}

.rdt-tl-tick-line {
  position: absolute;
  left: 50%;
  top: 8px;
  width: 1px;
  height: 6px;
  background: rgba(180, 210, 190, 0.35);
  display: block;
}

.rdt-tl-tick--major .rdt-tl-tick-line {
  height: 10px;
  background: rgba(220, 240, 230, 0.55);
}

.rdt-tl-tick-label {
  position: absolute;
  top: 34px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 10px;
  font-style: normal;
  color: var(--rdt-tl-muted);
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.rdt-tl-playhead {
  position: absolute;
  top: 6px;
  bottom: 18px;
  width: 2px;
  background: #fff;
  box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.4);
  transform: translateX(-1px);
  z-index: 4;
  pointer-events: none;
}

.rdt-tl-playhead--grab {
  pointer-events: auto;
  cursor: ew-resize;
  width: 10px;
  margin-left: -4px;
  background: transparent;
  box-shadow: none;
}

.rdt-tl-playhead--grab::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 0;
  bottom: 0;
  width: 2px;
  transform: translateX(-50%);
  background: #fff;
  box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.4);
}

.rdt-tl-playhead::before {
  content: '';
  position: absolute;
  top: -3px;
  left: 50%;
  transform: translateX(-50%);
  border-left: 5px solid transparent;
  border-right: 5px solid transparent;
  border-top: 6px solid #fff;
  z-index: 1;
}

.rdt-tl-box {
  position: absolute;
  top: 8px;
  bottom: 16px;
  background: rgba(61, 186, 122, 0.18);
  border: 1px solid rgba(61, 186, 122, 0.7);
  z-index: 3;
  pointer-events: none;
}

.rdt-tl-hint {
  font-size: 11px;
  color: var(--rdt-tl-muted);
}
</style>
