import http from './http'

export const fetchDevices = (params = {}) => http.get('/api/devices', { params })
export const fetchDevice = (id) => http.get(`/api/devices/${id}`)
export const fetchDeviceByDeviceId = (deviceId) =>
  http.get(`/api/devices/by-device-id/${encodeURIComponent(deviceId)}`)
export const createDevice = (data) => http.post('/api/devices', data)
export const updateDevice = (id, data) => http.put(`/api/devices/${id}`, data)
export const deleteDevice = (id) => http.delete(`/api/devices/${id}`)
export const fetchDeviceFolderTree = () => http.get('/api/device-folders/tree')
export const createDeviceFolder = (data) => http.post('/api/device-folders', data)
export const updateDeviceFolder = (id, data) => http.put(`/api/device-folders/${id}`, data)
export const deleteDeviceFolder = (id) => http.delete(`/api/device-folders/${id}`)
export const registerStream = (data) => http.post('/api/streams/register', data)
export const deleteStream = (id) => http.delete(`/api/streams/${id}`)
export const setStreamLive = (id) => http.put(`/api/streams/${id}/live`)
export const previewStart = (data) => http.post('/api/preview/start', data)

/** 业务端 */
export const fetchBizFolderTree = () => http.get('/api/biz/folders/tree')
export const fetchBizDevices = (params = {}) => http.post('/api/biz/devices/list', params)
export const fetchBizDevice = (deviceId) =>
  http.post('/api/biz/devices/detail', { deviceId })
export const startBizLive = (deviceId) =>
  http.post('/api/biz/devices/live', { deviceId })
/** 录像列表/日历数据量大，单独放宽超时 */
const recordingHttpOpts = { timeout: 60000 }

export const fetchBizRecordings = (deviceId, params = {}) =>
  http.post('/api/biz/devices/recordings', { deviceId, ...params }, recordingHttpOpts)
export const fetchRecordings = (deviceId, params = {}) =>
  http.get('/api/recordings', { params: { deviceId, ...params }, ...recordingHttpOpts })
export const fetchRecordingDays = (deviceId, params = {}) =>
  http.get('/api/recordings/days', { params: { deviceId, ...params }, ...recordingHttpOpts })
export const fetchBizRecordingDays = (deviceId, params = {}) =>
  http.post('/api/biz/devices/recording-days', { deviceId, ...params }, recordingHttpOpts)
export const recordingFileUrl = (deviceId, fileName) => {
  const base = (http.defaults.baseURL || '').replace(/\/$/, '')
  const token = localStorage.getItem('video_mid_token')
  const q = token ? `?token=${encodeURIComponent(token)}` : ''
  // video 标签无法带 Authorization，若后端仅 Bearer 则需另开鉴权；先拼直链路径
  return `${base}/api/recordings/${encodeURIComponent(deviceId)}/${encodeURIComponent(fileName)}${q}`
}

/** 按时间点截取录像：时间戳前后各 seconds 秒，返回 videoUrl / startTime / endTime */
export const fetchRecordingClip = (deviceId, at, seconds = 30) =>
  http.get('/api/recordings/clip', {
    params: { deviceId, at, seconds },
    ...recordingHttpOpts
  })
export const fetchOpenRecordingClip = (deviceId, at, seconds = 30) =>
  http.get(`/api/open/devices/${encodeURIComponent(deviceId)}/clip`, {
    params: { at, seconds },
    ...recordingHttpOpts
  })
/** 批量截取：items = [{ deviceId, at, seconds }, ...] */
export const fetchRecordingClipsBatch = (items) =>
  http.post('/api/recordings/clips', items, recordingHttpOpts)
export const fetchOpenRecordingClipsBatch = (items) =>
  http.post('/api/open/clips', items, recordingHttpOpts)
/** 片段 MP4 直链（供 video 标签播放） */
export const recordingClipFileUrl = (deviceId, at, seconds = 30) => {
  const base = (http.defaults.baseURL || '').replace(/\/$/, '')
  const params = new URLSearchParams({
    deviceId,
    at: String(at),
    seconds: String(seconds)
  })
  const token = localStorage.getItem('video_mid_token')
  if (token) params.set('token', token)
  return `${base}/api/recordings/clip/file?${params}`
}

/** 为 video 标签播放追加 token（管理端 clip/file 需鉴权） */
export const authRecordingVideoUrl = (url) => {
  if (!url) return ''
  const token = localStorage.getItem('video_mid_token')
  if (!token) return url
  return `${url}${url.includes('?') ? '&' : '?'}token=${encodeURIComponent(token)}`
}

