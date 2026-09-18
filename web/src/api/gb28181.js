import http from './http'

export const fetchGb28181Config = () => http.get('/api/gb28181/config')
export const fetchGb28181Status = () => http.get('/api/gb28181/status')
export const fetchGb28181Catalog = () => http.get('/api/gb28181/catalog')
export const fetchGb28181BizDevices = () => http.get('/api/gb28181/biz-devices')
export const fetchGb28181SyncCheck = () => http.get('/api/gb28181/sync-check')
export const gb28181Invite = (channelId) =>
  http.post(`/api/gb28181/channels/${encodeURIComponent(channelId)}/invite`)
export const gb28181Bye = (channelId) =>
  http.post(`/api/gb28181/channels/${encodeURIComponent(channelId)}/bye`)
export const fetchGb28181InviteSessions = () => http.get('/api/gb28181/invite/sessions')
export const gb28181PlayDevice = (deviceId) =>
  http.post(`/api/gb28181/devices/${encodeURIComponent(deviceId)}/play`)
export const fetchGb28181SipStatus = () => http.get('/api/gb28181/sip/status')
export const fetchGb28181SipSessions = () => http.get('/api/gb28181/sip/sessions')
export const gb28181SipCatalogQuery = (deviceId) =>
  http.post(`/api/gb28181/sip/catalog-query/${encodeURIComponent(deviceId)}`)
export const fetchGb28181Registry = () => http.get('/api/gb28181/registry')
export const reloadGb28181RegistryFromDb = () => http.post('/api/gb28181/registry/reload-from-db')

export const gb28181CatalogXmlUrl = () => {
  const base = (http.defaults.baseURL || '').replace(/\/$/, '')
  return `${base}/api/gb28181/catalog.xml`
}
