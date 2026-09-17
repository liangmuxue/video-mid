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
export const fetchBizDevices = (params = {}) => http.get('/api/biz/devices', { params })
export const fetchBizDevice = (deviceId) =>
  http.get(`/api/biz/devices/${encodeURIComponent(deviceId)}`)
export const startBizLive = (deviceId) =>
  http.post(`/api/biz/devices/${encodeURIComponent(deviceId)}/live`)
export const fetchBizRecordings = (deviceId, params = {}) =>
  http.get(`/api/biz/devices/${encodeURIComponent(deviceId)}/recordings`, { params })
export const fetchRecordings = (deviceId, params = {}) =>
  http.get('/api/recordings', { params: { deviceId, ...params } })
export const fetchRecordingDays = (deviceId, params = {}) =>
  http.get('/api/recordings/days', { params: { deviceId, ...params } })
export const fetchBizRecordingDays = (deviceId, params = {}) =>
  http.get(`/api/biz/devices/${encodeURIComponent(deviceId)}/recording-days`, { params })
export const recordingFileUrl = (deviceId, fileName) => {
  const base = (http.defaults.baseURL || '').replace(/\/$/, '')
  const token = localStorage.getItem('video_mid_token')
  const q = token ? `?token=${encodeURIComponent(token)}` : ''
  // video 标签无法带 Authorization，若后端仅 Bearer 则需另开鉴权；先拼直链路径
  return `${base}/api/recordings/${encodeURIComponent(deviceId)}/${encodeURIComponent(fileName)}${q}`
}

