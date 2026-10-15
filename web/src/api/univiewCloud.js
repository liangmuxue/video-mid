import http from './http'

export const syncUniviewCloud = () => http.post('/api/uniview-cloud/sync', {}, { timeout: 120000 })
export const stopUniviewCloudLive = (deviceId) =>
  http.post('/api/uniview-cloud/live/stop', { deviceId })
