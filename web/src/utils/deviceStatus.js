/** 设备状态：0=不可用 1=已启用 2=已停用 */
export const STATUS_UNAVAILABLE = 0
export const STATUS_ENABLED = 1
export const STATUS_DISABLED = 2

export function normalizeStatus(raw) {
  if (raw == null || raw === '') return STATUS_DISABLED
  if (typeof raw === 'number') {
    if (raw === STATUS_ENABLED || raw === STATUS_DISABLED || raw === STATUS_UNAVAILABLE) return raw
    return STATUS_DISABLED
  }
  const s = String(raw).trim()
  if (/^-?\d+$/.test(s)) {
    const n = Number(s)
    if (n === STATUS_ENABLED || n === STATUS_DISABLED || n === STATUS_UNAVAILABLE) return n
    return STATUS_DISABLED
  }
  if (s === '已启用' || s.toUpperCase() === 'ON' || s.toUpperCase() === 'ENABLED') return STATUS_ENABLED
  if (s === '已停用' || s.toUpperCase() === 'OFF' || s.toUpperCase() === 'DISABLED') return STATUS_DISABLED
  if (s === '不可用' || s.toUpperCase() === 'UNAVAILABLE') return STATUS_UNAVAILABLE
  return STATUS_DISABLED
}

export function statusLabel(status) {
  const n = normalizeStatus(status)
  if (n === STATUS_ENABLED) return '已启用'
  if (n === STATUS_DISABLED) return '已停用'
  if (n === STATUS_UNAVAILABLE) return '不可用'
  return '-'
}

export function statusClass(status) {
  const n = normalizeStatus(status)
  return {
    on: n === STATUS_ENABLED,
    off: n === STATUS_DISABLED,
    unavailable: n === STATUS_UNAVAILABLE
  }
}

export function isEnabled(status) {
  return normalizeStatus(status) === STATUS_ENABLED
}

export function isDisabled(status) {
  return normalizeStatus(status) === STATUS_DISABLED
}

export function isUnavailable(status) {
  return normalizeStatus(status) === STATUS_UNAVAILABLE
}
