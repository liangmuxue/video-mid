import http from './http'

export const fetchDevices = () => http.get('/api/devices')
export const createDevice = (data) => http.post('/api/devices', data)
export const updateDevice = (id, data) => http.put(`/api/devices/${id}`, data)
export const deleteDevice = (id) => http.delete(`/api/devices/${id}`)
export const registerStream = (data) => http.post('/api/streams/register', data)
export const deleteStream = (id) => http.delete(`/api/streams/${id}`)
export const previewStart = (data) => http.post('/api/preview/start', data)
export const previewStop = (data) => http.post('/api/preview/stop', data)
