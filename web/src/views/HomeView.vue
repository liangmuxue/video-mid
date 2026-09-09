<template>
  <AppShell>
    <section class="panel">
      <h1>控制台</h1>
      <p>设备与码流已分表；播放地址通过注册写入，Redis 只计播放数。</p>
      <dl>
        <div><dt>用户名</dt><dd>{{ auth.user?.username }}</dd></div>
        <div><dt>昵称</dt><dd>{{ auth.user?.nickname || '-' }}</dd></div>
      </dl>
      <router-link class="cta" to="/devices">进入设备管理 →</router-link>
    </section>
  </AppShell>
</template>

<script setup>
import AppShell from '../components/AppShell.vue'
import { useAuthStore } from '../stores/auth'
const auth = useAuthStore()
</script>

<style scoped>
.panel {
  border: 1px solid var(--line); border-radius: 18px; padding: 28px;
  background: var(--panel); max-width: 720px;
}
h1 { margin: 0 0 8px; font-family: Syne, sans-serif; font-size: 32px; }
.panel > p { margin: 0 0 20px; color: var(--muted); }
dl { margin: 0 0 24px; display: grid; gap: 12px; }
dl > div {
  display: grid; grid-template-columns: 100px 1fr; gap: 12px;
  padding: 10px 0; border-top: 1px solid var(--line);
}
dt { color: var(--muted); }
dd { margin: 0; }
.cta {
  display: inline-flex; padding: 12px 16px; border-radius: 12px;
  background: linear-gradient(135deg, var(--accent), #2f9a65);
  color: #04140c; font-weight: 600;
}
</style>
