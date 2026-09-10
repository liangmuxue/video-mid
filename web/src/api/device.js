import http from './http'

export const fetchDevices = () => http.get('/api/devices')
export const fetchDevice = (id) => http.get(`/api/devices/${id}`)
export const fetchDeviceByDeviceId = (deviceId) =>
  http.get(`/api/devices/by-device-id/${encodeURIComponent(deviceId)}`)
export const createDevice = (data) => http.post('/api/devices', data)
export const updateDevice = (id, data) => http.put(`/api/devices/${id}`, data)
export const deleteDevice = (id) => http.delete(`/api/devices/${id}`)
export const registerStream = (data) => http.post('/api/streams/register', data)
export const deleteStream = (id) => http.delete(`/api/streams/${id}`)
export const previewStart = (data) => http.post('/api/preview/start', data)
export const previewStop = (data) => http.post('/api/preview/stop', data)
export const fetchRecordings = (deviceId, params = {}) =>
  http.get('/api/recordings', { params: { deviceId, ...params } })
export const recordingFileUrl = (deviceId, fileName) => {
  const base = (http.defaults.baseURL || '').replace(/\/$/, '')
  const token = localStorage.getItem('video_mid_token')
  const q = token ? `?token=${encodeURIComponent(token)}` : ''
  // video 标签无法带 Authorization，若后端仅 Bearer 则需另开鉴权；先拼直链路径
  return `${base}/api/recordings/${encodeURIComponent(deviceId)}/${encodeURIComponent(fileName)}${q}`
}

