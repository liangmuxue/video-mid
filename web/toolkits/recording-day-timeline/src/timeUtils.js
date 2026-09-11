/** 一天秒数 */
export const DAY_SECONDS = 24 * 60 * 60

/**
 * 选中日期的起止（固定 00:00:00 ~ 23:59:59）
 * @param {string} dateStr yyyy-MM-dd
 */
export function dayBounds(dateStr) {
  const day = String(dateStr || '').slice(0, 10)
  if (!/^\d{4}-\d{2}-\d{2}$/.test(day)) {
    throw new Error('日期格式应为 yyyy-MM-dd')
  }
  return {
    day,
    from: `${day} 00:00:00`,
    to: `${day} 23:59:59`
  }
}

/**
 * 解析录像时间戳 → Date
 * 支持 yyyyMMdd_HHmmss / ISO / yyyy-MM-dd HH:mm:ss
 */
export function parseRecordTimestamp(raw) {
  if (!raw) return null
  if (raw instanceof Date) return Number.isNaN(raw.getTime()) ? null : raw
  const s = String(raw).trim()
  if (/^\d{8}_\d{6}$/.test(s)) {
    const y = s.slice(0, 4)
    const m = s.slice(4, 6)
    const d = s.slice(6, 8)
    const hh = s.slice(9, 11)
    const mm = s.slice(11, 13)
    const ss = s.slice(13, 15)
    const dt = new Date(`${y}-${m}-${d}T${hh}:${mm}:${ss}`)
    return Number.isNaN(dt.getTime()) ? null : dt
  }
  const normalized = s.includes('T') ? s : s.replace(' ', 'T')
  const dt = new Date(normalized)
  return Number.isNaN(dt.getTime()) ? null : dt
}

/** 当天 00:00:00 起的秒数 0~86399 */
export function secondsOfDay(date) {
  if (!(date instanceof Date) || Number.isNaN(date.getTime())) return 0
  return date.getHours() * 3600 + date.getMinutes() * 60 + date.getSeconds()
}

export function formatClock(totalSeconds) {
  const s = Math.max(0, Math.min(DAY_SECONDS - 1, Math.floor(totalSeconds)))
  const hh = String(Math.floor(s / 3600)).padStart(2, '0')
  const mm = String(Math.floor((s % 3600) / 60)).padStart(2, '0')
  const ss = String(s % 60).padStart(2, '0')
  return `${hh}:${mm}:${ss}`
}

/**
 * 将接口录像列表映射为当天可点击时间段
 * @param {Array} records API 返回列表
 * @param {string} dateStr yyyy-MM-dd
 * @param {number} clipSeconds 单段默认时长（接口无结束时间时使用）
 */
export function buildDaySegments(records, dateStr, clipSeconds = 300) {
  const day = String(dateStr).slice(0, 10)
  const dayStart = new Date(`${day}T00:00:00`)
  const dayEnd = new Date(`${day}T23:59:59`)
  const list = Array.isArray(records) ? records : []
  const raw = []

  for (const item of list) {
    const start =
      parseRecordTimestamp(item.recordTime) ||
      parseRecordTimestamp(item.timestamp) ||
      parseRecordTimestamp(item.startTime)
    if (!start) continue
    if (start < dayStart || start > dayEnd) continue

    let end = parseRecordTimestamp(item.endTime)
    if (!end) {
      end = new Date(start.getTime() + Math.max(1, clipSeconds) * 1000)
    }
    if (end > dayEnd) end = dayEnd
    if (end <= start) {
      end = new Date(start.getTime() + 1000)
    }

    raw.push({
      startSec: secondsOfDay(start),
      endSec: Math.min(DAY_SECONDS, secondsOfDay(end) || DAY_SECONDS),
      start,
      end,
      record: item
    })
  }

  raw.sort((a, b) => a.startSec - b.startSec)

  // 合并重叠区间，保留代表录像（取最早那段的 record）
  const merged = []
  for (const seg of raw) {
    const last = merged[merged.length - 1]
    if (!last || seg.startSec > last.endSec) {
      merged.push({ ...seg, records: [seg.record] })
      continue
    }
    last.endSec = Math.max(last.endSec, seg.endSec)
    last.end = seg.end > last.end ? seg.end : last.end
    last.records.push(seg.record)
  }

  return merged.map((seg) => ({
    startSec: seg.startSec,
    endSec: seg.endSec,
    leftPct: (seg.startSec / DAY_SECONDS) * 100,
    widthPct: (Math.max(1, seg.endSec - seg.startSec) / DAY_SECONDS) * 100,
    record: seg.records[0],
    records: seg.records
  }))
}

export function earliestSegment(segments) {
  if (!segments?.length) return null
  return segments.reduce((a, b) => (a.startSec <= b.startSec ? a : b))
}

/** 点击秒数落在哪个有录像段内（灰色不可点） */
export function hitSegment(segments, sec) {
  return (segments || []).find((s) => sec >= s.startSec && sec < s.endSec) || null
}

/** 在段内定位具体录像文件（按开始时间） */
export function pickRecordAt(segment, sec) {
  if (!segment) return null
  const records = [...(segment.records || [segment.record])].filter(Boolean)
  records.sort((a, b) => {
    const ta = parseRecordTimestamp(a.recordTime || a.timestamp)?.getTime() || 0
    const tb = parseRecordTimestamp(b.recordTime || b.timestamp)?.getTime() || 0
    return ta - tb
  })
  let chosen = records[0]
  for (const r of records) {
    const t = parseRecordTimestamp(r.recordTime || r.timestamp)
    if (t && secondsOfDay(t) <= sec) chosen = r
  }
  return chosen
}
