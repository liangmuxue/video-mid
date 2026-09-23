import http from './http'

export const fetchUniviewConfig = () => http.get('/api/uniview/config')
export const fetchUniviewDevices = () => http.get('/api/uniview/devices')
export const fetchUniviewDevice = (deviceId) =>
  http.get(`/api/uniview/devices/${encodeURIComponent(deviceId)}`)
export const fetchPtzDevices = () => http.get('/api/uniview/ptz/devices')

export const ptzMove = (deviceId, direction, speed = 4) =>
  http.post('/api/uniview/ptz/move', { deviceId, direction, speed })

export const ptzZoom = (deviceId, action, speed = 4) =>
  http.post('/api/uniview/ptz/zoom', { deviceId, action, speed })

export const ptzFocus = (deviceId, action, speed = 4) =>
  http.post('/api/uniview/ptz/focus', { deviceId, action, speed })

export const ptzWideAngle = (deviceId) =>
  http.post('/api/uniview/ptz/wide-angle', { deviceId })

export const ptzGotoPreset = (deviceId, index) =>
  http.post('/api/uniview/ptz/preset/goto', { deviceId, index })

export const ptzSetPreset = (deviceId, index, name, overwrite = false) =>
  http.post('/api/uniview/ptz/preset/save', { deviceId, index, name, overwrite })
