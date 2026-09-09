<template>
  <div class="shell">
    <header class="top">
      <div class="left">
        <p class="brand">极知 · VIDEO MID</p>
        <nav>
          <router-link to="/">概览</router-link>
          <router-link to="/devices">设备管理</router-link>
        </nav>
      </div>
      <div class="user">
        <span>{{ displayName }}</span>
        <button type="button" @click="onLogout">退出</button>
      </div>
    </header>
    <main><slot /></main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const displayName = computed(() => auth.user?.nickname || auth.user?.username || '用户')

async function onLogout() {
  await auth.logout()
  await router.replace('/login')
}
</script>

<style scoped>
.shell { min-height: 100vh; padding: 24px clamp(16px, 3vw, 40px) 48px; }
.top {
  display: flex; justify-content: space-between; align-items: center; gap: 16px;
  margin-bottom: 28px; padding-bottom: 16px; border-bottom: 1px solid var(--line);
}
.left { display: flex; align-items: center; gap: 28px; flex-wrap: wrap; }
.brand { margin: 0; letter-spacing: 0.18em; font-size: 12px; color: var(--accent-2); font-weight: 600; }
nav { display: flex; gap: 8px; }
nav a { padding: 8px 14px; border-radius: 999px; color: var(--muted); border: 1px solid transparent; }
nav a.router-link-active { color: var(--text); border-color: var(--line); background: rgba(61,186,122,.12); }
.user { display: flex; align-items: center; gap: 12px; color: var(--muted); }
.user button {
  border: 1px solid var(--line); background: transparent; color: var(--text);
  border-radius: 999px; padding: 8px 14px; cursor: pointer;
}
</style>
