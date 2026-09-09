import axios from 'axios'

const http = axios.create({
  baseURL: 'http://8.130.74.232:8090',
  timeout: 15000
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('video_mid_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (resp) => {
    const body = resp.data
    if (body && typeof body.code === 'number') {
      if (body.code === 0) return body.data
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body
  },
  (err) => {
    const msg = err.response?.data?.message || err.message || '网络错误'
    if (err.response?.status === 401) {
      localStorage.removeItem('video_mid_token')
    }
    return Promise.reject(new Error(msg))
  }
)

export default http
