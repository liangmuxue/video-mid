/** 仅 mock 云台画面姿态。正式预览不要引用本文件。 */

export const MIN_ZOOM = 1
export const MAX_ZOOM = 12
export const MIN_FOCUS = -5
export const MAX_FOCUS = 5
/** 广角仍略大于 1，转向才看得到裁切 */
export const BASE_SCALE = 1.28

export function defaultPose() {
  return { panX: 0, panY: 0, zoom: 1, focus: 0 }
}

export function clamp(n, min, max) {
  return Math.min(max, Math.max(min, n))
}

export function normalizePose(raw) {
  const p = raw && typeof raw === 'object' ? raw : {}
  return {
    panX: clamp(Number(p.panX) || 0, -1, 1),
    panY: clamp(Number(p.panY) || 0, -1, 1),
    zoom: clamp(Number(p.zoom) || 1, MIN_ZOOM, MAX_ZOOM),
    focus: clamp(Number(p.focus) || 0, MIN_FOCUS, MAX_FOCUS)
  }
}

export function displayScale(zoom) {
  return BASE_SCALE * clamp(Number(zoom) || 1, MIN_ZOOM, MAX_ZOOM)
}

/** 镜头右转 → 画面左移 */
export function applyMove(pose, direction, speed = 4) {
  const next = { ...normalizePose(pose) }
  const step = (0.14 * (speed / 4)) / Math.sqrt(next.zoom)
  const d = String(direction || '').toLowerCase()
  if (d === 'up' || d === 'left_up' || d === 'right_up') next.panY -= step
  if (d === 'down' || d === 'left_down' || d === 'right_down') next.panY += step
  if (d === 'left' || d === 'left_up' || d === 'left_down') next.panX -= step
  if (d === 'right' || d === 'right_up' || d === 'right_down') next.panX += step
  next.panX = clamp(next.panX, -1, 1)
  next.panY = clamp(next.panY, -1, 1)
  return next
}

export function applyZoom(pose, action, speed = 4) {
  const next = { ...normalizePose(pose) }
  const delta = 0.45 * (speed / 4)
  if (action === 'in' || action === 'zoom_in') next.zoom += delta
  else next.zoom -= delta
  next.zoom = clamp(next.zoom, MIN_ZOOM, MAX_ZOOM)
  if (next.zoom <= MIN_ZOOM + 0.01) {
    next.panX = 0
    next.panY = 0
  }
  return next
}

export function applyWideAngle() {
  return defaultPose()
}

export function applyFocus(pose, action) {
  const next = { ...normalizePose(pose) }
  if (action === 'near' || action === 'focus_near') next.focus += 1
  else next.focus -= 1
  next.focus = clamp(next.focus, MIN_FOCUS, MAX_FOCUS)
  return next
}

export function poseFromPreset(preset) {
  if (!preset || typeof preset !== 'object') return defaultPose()
  if (preset.panX != null || preset.pose) {
    return normalizePose(preset.pose || preset)
  }
  const zoom = Number(preset.zoom)
  return normalizePose({
    panX: 0,
    panY: 0,
    zoom: Number.isFinite(zoom) && zoom > 0 ? zoom : 1,
    focus: 0
  })
}

export function cssTransform(pose) {
  const p = normalizePose(pose)
  const scale = displayScale(p.zoom)
  const room = (1 - 1 / scale) * 50
  const x = (-p.panX * room).toFixed(2)
  const y = (-p.panY * room).toFixed(2)
  return {
    transform: `translate(${x}%, ${y}%) scale(${scale.toFixed(3)})`,
    filter: p.focus ? `blur(${Math.abs(p.focus) * 1.15}px)` : 'none'
  }
}

export function describePose(pose) {
  const p = normalizePose(pose)
  const hz = p.panX === 0 ? '中' : p.panX > 0 ? `右${Math.round(p.panX * 100)}` : `左${Math.round(-p.panX * 100)}`
  const vt = p.panY === 0 ? '中' : p.panY > 0 ? `下${Math.round(p.panY * 100)}` : `上${Math.round(-p.panY * 100)}`
  const focus =
    p.focus === 0 ? '合焦' : p.focus > 0 ? `近焦 ${p.focus}` : `远焦 ${-p.focus}`
  return `变倍 ${p.zoom.toFixed(1)}x · ${hz} / ${vt} · ${focus}`
}

export function captureSyntheticFrame(pose, w = 1280, h = 720) {
  const p = normalizePose(pose)
  const canvas = document.createElement('canvas')
  canvas.width = w
  canvas.height = h
  const ctx = canvas.getContext('2d')
  const scale = displayScale(p.zoom)
  ctx.fillStyle = '#07140c'
  ctx.fillRect(0, 0, w, h)
  ctx.save()
  ctx.translate(w / 2, h / 2)
  ctx.scale(scale, scale)
  ctx.translate(-p.panX * w * 0.28, -p.panY * h * 0.28)
  if (p.focus) ctx.filter = `blur(${Math.abs(p.focus) * 1.15}px)`
  ctx.fillStyle = '#1a3320'
  ctx.fillRect(-w, -h, w * 2, h * 2)
  ctx.strokeStyle = '#2d5a38'
  ctx.lineWidth = 2
  for (let x = -w; x <= w; x += 80) {
    ctx.beginPath()
    ctx.moveTo(x, -h)
    ctx.lineTo(x, h)
    ctx.stroke()
  }
  for (let y = -h; y <= h; y += 80) {
    ctx.beginPath()
    ctx.moveTo(-w, y)
    ctx.lineTo(w, y)
    ctx.stroke()
  }
  ctx.fillStyle = '#c8f06a'
  ctx.font = '22px sans-serif'
  ctx.textAlign = 'center'
  ctx.fillText('北 · 周界', 0, -h * 0.32)
  ctx.fillText('南 · 入口', 0, h * 0.32)
  ctx.fillText('西 · 停车场', -w * 0.32, 0)
  ctx.fillStyle = '#7ee0ff'
  ctx.fillText('东 · 岗卡', w * 0.32, 0)
  ctx.fillStyle = '#fff'
  ctx.font = '16px sans-serif'
  ctx.fillText('云台中心', 0, 28)
  ctx.restore()
  return canvas
}

export function captureSimFrame(video, pose) {
  const vw = video.videoWidth
  const vh = video.videoHeight
  if (!vw || !vh) return null
  const p = normalizePose(pose)
  const scale = displayScale(p.zoom)
  const sw = vw / scale
  const sh = vh / scale
  const maxX = (vw - sw) / 2
  const maxY = (vh - sh) / 2
  const sx = clamp(maxX + p.panX * maxX, 0, vw - sw)
  const sy = clamp(maxY + p.panY * maxY, 0, vh - sh)
  const canvas = document.createElement('canvas')
  canvas.width = vw
  canvas.height = vh
  const ctx = canvas.getContext('2d')
  if (p.focus) ctx.filter = `blur(${Math.abs(p.focus) * 1.15}px)`
  ctx.drawImage(video, sx, sy, sw, sh, 0, 0, vw, vh)
  return canvas
}

const poseKey = (deviceId) => `video-mid-ptz-pose:${deviceId}`
const presetKey = (deviceId) => `video-mid-ptz-presets:${deviceId}`

export function loadStoredPose(deviceId) {
  try {
    const raw = sessionStorage.getItem(poseKey(deviceId))
    return raw ? normalizePose(JSON.parse(raw)) : null
  } catch {
    return null
  }
}

export function saveStoredPose(deviceId, pose) {
  sessionStorage.setItem(poseKey(deviceId), JSON.stringify(normalizePose(pose)))
}

export function loadStoredPresets(deviceId) {
  try {
    const raw = sessionStorage.getItem(presetKey(deviceId))
    return raw ? JSON.parse(raw) : {}
  } catch {
    return {}
  }
}

export function saveStoredPreset(deviceId, index, name, pose) {
  const all = loadStoredPresets(deviceId)
  all[String(index)] = { index, name, ...normalizePose(pose) }
  sessionStorage.setItem(presetKey(deviceId), JSON.stringify(all))
}

export function storedPresetPose(deviceId, index, fallbackPreset) {
  const all = loadStoredPresets(deviceId)
  const hit = all[String(index)]
  if (hit) return normalizePose(hit)
  return poseFromPreset(fallbackPreset)
}
