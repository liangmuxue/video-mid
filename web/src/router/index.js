import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import LoginView from '../views/LoginView.vue'
import HomeView from '../views/HomeView.vue'
import DevicesView from '../views/DevicesView.vue'
import DeviceStreamsView from '../views/DeviceStreamsView.vue'
import BizPortalView from '../views/BizPortalView.vue'
import PtzMonitorView from '../views/PtzMonitorView.vue'
import Gb28181View from '../views/Gb28181View.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    { path: '/', name: 'home', component: HomeView },
    { path: '/devices', name: 'devices', component: DevicesView },
    { path: '/devices/:deviceId/streams', name: 'device-streams', component: DeviceStreamsView },
    { path: '/ptz', name: 'ptz', component: PtzMonitorView },
    { path: '/gb28181', name: 'gb28181', component: Gb28181View },
    { path: '/biz', name: 'biz', component: BizPortalView },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.meta.public) {
    if (auth.token && to.name === 'login') return { name: 'home' }
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
