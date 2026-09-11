import { DAY_SECONDS, formatClock } from './timeUtils.js'

export const MIN_VIEW_SPAN = 10 // 最小可视窗口 10 秒，可点到分秒
export const MAX_VIEW_SPAN = DAY_SECONDS

/**
 * 根据可视跨度生成刻度
 * @returns {{ sec: number, label: string, major: boolean }[]}
 */
export function buildTicks(viewStart, viewEnd) {
  const start = Math.max(0, viewStart)
  const end = Math.min(DAY_SECONDS, viewEnd)
  const span = Math.max(1, end - start)

  let step
  let majorEvery
  if (span > 8 * 3600) {
    step = 3600
    majorEvery = 3
  } else if (span > 3 * 3600) {
    step = 1800
    majorEvery = 2
  } else if (span > 3600) {
    step = 600
    majorEvery = 3
  } else if (span > 20 * 60) {
    step = 60
    majorEvery = 5
  } else if (span > 5 * 60) {
    step = 30
    majorEvery = 2
  } else if (span > 60) {
    step = 10
    majorEvery = 6
  } else if (span > 20) {
    step = 5
    majorEvery = 2
  } else {
    step = 1
    majorEvery = 5
  }

  const first = Math.ceil(start / step) * step
  const ticks = []
  for (let s = first; s <= end + 0.0001; s += step) {
    const sec = Math.round(s)
    if (sec < 0 || sec > DAY_SECONDS) continue
    const idx = Math.round(sec / step)
    const major = idx % majorEvery === 0
    ticks.push({
      sec,
      major,
      label: major ? formatTickLabel(sec, span) : ''
    })
  }
  return ticks
}

export function formatTickLabel(sec, span) {
  const s = Math.max(0, Math.min(DAY_SECONDS, Math.floor(sec)))
  const hh = String(Math.floor(s / 3600)).padStart(2, '0')
  const mm = String(Math.floor((s % 3600) / 60)).padStart(2, '0')
  const ss = String(s % 60).padStart(2, '0')
  if (span > 3600) return `${hh}:${mm}`
  if (span > 60) return `${hh}:${mm}:${ss}`
  return `${mm}:${ss}`
}

export function clampView(start, end) {
  let a = start
  let b = end
  if (b - a < MIN_VIEW_SPAN) {
    const mid = (a + b) / 2
    a = mid - MIN_VIEW_SPAN / 2
    b = mid + MIN_VIEW_SPAN / 2
  }
  if (b - a > MAX_VIEW_SPAN) {
    a = 0
    b = DAY_SECONDS
  }
  if (a < 0) {
    b -= a
    a = 0
  }
  if (b > DAY_SECONDS) {
    a -= b - DAY_SECONDS
    b = DAY_SECONDS
  }
  a = Math.max(0, a)
  b = Math.min(DAY_SECONDS, b)
  if (b - a < MIN_VIEW_SPAN) {
    b = Math.min(DAY_SECONDS, a + MIN_VIEW_SPAN)
    a = Math.max(0, b - MIN_VIEW_SPAN)
  }
  return { viewStart: a, viewEnd: b }
}

/** 滚轮缩放：以锚点秒数为中心 */
export function zoomAt(viewStart, viewEnd, anchorSec, factor) {
  const span = viewEnd - viewStart
  const next = span * factor
  const ratio = span <= 0 ? 0.5 : (anchorSec - viewStart) / span
  const a = anchorSec - next * ratio
  const b = anchorSec + next * (1 - ratio)
  return clampView(a, b)
}

/** 框选范围 → 新视窗 */
export function zoomToRange(fromSec, toSec) {
  const a = Math.min(fromSec, toSec)
  const b = Math.max(fromSec, toSec)
  return clampView(a, b)
}

export function panView(viewStart, viewEnd, deltaSec) {
  return clampView(viewStart + deltaSec, viewEnd + deltaSec)
}

export function secToLeftPct(sec, viewStart, viewEnd) {
  const span = Math.max(1e-6, viewEnd - viewStart)
  return ((sec - viewStart) / span) * 100
}

export function widthPct(startSec, endSec, viewStart, viewEnd) {
  const span = Math.max(1e-6, viewEnd - viewStart)
  return (Math.max(0, endSec - startSec) / span) * 100
}

export function clientXToSec(clientX, rect, viewStart, viewEnd) {
  if (!rect || rect.width <= 0) return viewStart
  const ratio = Math.min(1, Math.max(0, (clientX - rect.left) / rect.width))
  return viewStart + ratio * (viewEnd - viewStart)
}

export { formatClock, DAY_SECONDS }
