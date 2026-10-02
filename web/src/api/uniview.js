import http from './http'

export const fetchUniviewConfig = () => http.get('/api/uniview/config')
export const fetchUniviewDevices = () => http.get('/api/uniview/devices')
export const fetchUniviewDevice = (deviceId) =>
  http.get(`/api/ptz/devices/${encodeURIComponent(deviceId)}`)
export const fetchPtzDevices = () => http.get('/api/ptz/devices')

export const ptzMove = (deviceId, direction, speed = 4) =>
  http.post('/api/ptz/move', { deviceId, direction, speed })

export const ptzZoom = (deviceId, action, speed = 4) =>
  http.post('/api/ptz/zoom', { deviceId, action, speed })

export const ptzFocus = (deviceId, action, speed = 4) =>
  http.post('/api/ptz/focus', { deviceId, action, speed })

export const ptzWideAngle = (deviceId) =>
  http.post('/api/ptz/wide-angle', { deviceId })

export const ptzGotoPreset = (deviceId, index) =>
  http.post('/api/ptz/preset/goto', { deviceId, index })

export const ptzSetPreset = (deviceId, index, name, overwrite = false) =>
  http.post('/api/ptz/preset/save', { deviceId, index, name, overwrite })
