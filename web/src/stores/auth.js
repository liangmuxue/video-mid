import { defineStore } from 'pinia'
import { ref } from 'vue'
import api from '../api/http'

const TOKEN_KEY = 'video_mid_token'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '')
  const user = ref(null)

  function setSession(payload) {
    token.value = payload.token
    user.value = payload.user
    localStorage.setItem(TOKEN_KEY, payload.token)
  }

  function clear() {
    token.value = ''
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
  }

  async function login(username, password) {
    const data = await api.post('/api/auth/login', { username, password })
    setSession(data)
    return data
  }

  async function fetchMe() {
    const data = await api.get('/api/auth/me')
    user.value = data
    return data
  }

  async function logout() {
    try {
      if (token.value) await api.post('/api/auth/logout')
    } finally {
      clear()
    }
  }

  return { token, user, login, fetchMe, logout, clear }
})
