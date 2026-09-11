import axios from 'axios'

/**
 * 开发环境走 Vite 同域代理（/api → 后端），避免浏览器直连跨域/断网误报。
 * 生产可设 VITE_API_BASE，例如 https://api.example.com
 */
const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '',
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
    let msg = err.response?.data?.message || err.message || '网络错误'
    if (err.code === 'ERR_NETWORK' || err.message === 'Network Error') {
      msg = '无法连接后端服务，请确认 API 已启动并可访问'
    } else if (err.code === 'ECONNABORTED') {
      msg = '请求超时，请稍后重试'
    } else if (err.response?.status === 502 || err.response?.status === 503 || err.response?.status === 504) {
      msg = '后端服务暂时不可用，请稍后重试'
    } else if (err.response?.status === 500 && (msg === 'Network Error' || !err.response?.data?.message)) {
      msg = '后端服务异常或不可达'
    }
    if (err.response?.status === 401) {
      localStorage.removeItem('video_mid_token')
    }
    return Promise.reject(new Error(msg))
  }
)

export default http
