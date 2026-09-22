import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import LoginView from '../views/LoginView.vue'
import HomeView from '../views/HomeView.vue'
import DevicesView from '../views/DevicesView.vue'
import DeviceStreamsView from '../views/DeviceStreamsView.vue'
import BizPortalView from '../views/BizPortalView.vue'
import PtzMonitorView from '../views/PtzMonitorView.vue'
import Gb28181View from '../views/Gb28181View.vue'
import RecordClipView from '../views/RecordClipView.vue'

/** 测试环境默认不要求登录；生产构建可设 VITE_AUTH_REQUIRED=true */
const authRequired = import.meta.env.VITE_AUTH_REQUIRED === 'true'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    { path: '/', name: 'home', component: HomeView, meta: { public: true } },
    { path: '/devices', name: 'devices', component: DevicesView, meta: { public: true } },
    { path: '/devices/:deviceId/streams', name: 'device-streams', component: DeviceStreamsView, meta: { public: true } },
    { path: '/ptz', name: 'ptz', component: PtzMonitorView, meta: { public: true } },
    { path: '/gb28181', name: 'gb28181', component: Gb28181View, meta: { public: true } },
    { path: '/biz', name: 'biz', component: BizPortalView, meta: { public: true } },
    { path: '/record-clips', name: 'record-clips', component: RecordClipView, meta: { public: true } },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (!authRequired || to.meta.public) {
    if (authRequired && auth.token && to.name === 'login') return { name: 'home' }
    return true
  }
  if (!auth.token) return { name: 'login', query: { redirect: to.fullPath } }
  if (!auth.user) {
    try {
      await auth.fetchMe()
    } catch {
      auth.clear()
      return { name: 'login' }
    }
  }
  return true
})

export default router
