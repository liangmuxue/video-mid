import http from './http'

export const fetchUniviewConfig = () => http.get('/api/uniview/config')
export const fetchUniviewDevices = () => http.get('/api/uniview/devices')
export const fetchUniviewDevice = (deviceId) =>
  http.get(`/api/uniview/devices/${encodeURIComponent(deviceId)}`)
export const fetchPtzDevices = () => http.get('/api/uniview/ptz/devices')

export const ptzMove = (deviceId, direction, speed = 4) =>
  http.post(`/api/uniview/ptz/${encodeURIComponent(deviceId)}/move`, null, {
    params: { direction, speed }
  })

export const ptzZoom = (deviceId, action, speed = 4) =>
  http.post(`/api/uniview/ptz/${encodeURIComponent(deviceId)}/zoom`, null, {
    params: { action, speed }
  })

export const ptzFocus = (deviceId, action, speed = 4) =>
  http.post(`/api/uniview/ptz/${encodeURIComponent(deviceId)}/focus`, null, {
    params: { action, speed }
  })

export const ptzWideAngle = (deviceId) =>
  http.post(`/api/uniview/ptz/${encodeURIComponent(deviceId)}/wide-angle`)

export const ptzGotoPreset = (deviceId, index) =>
  http.post(`/api/uniview/ptz/${encodeURIComponent(deviceId)}/preset/${index}/goto`)

export const ptzSetPreset = (deviceId, index, name, overwrite = false, zoom) =>
  http.post(`/api/uniview/ptz/${encodeURIComponent(deviceId)}/preset/${index}`, null, {
    params: {
      name,
      overwrite,
      ...(zoom != null && Number.isFinite(Number(zoom)) ? { zoom } : {})
    }
  })
