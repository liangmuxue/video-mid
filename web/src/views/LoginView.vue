<template>
  <div class="page">
    <section class="hero">
      <p class="brand">极知 · VIDEO MID</p>
      <h1>视频中台</h1>
      <p class="sub">国标调度 · 流媒体 · 运维登录</p>
    </section>

    <form class="card" @submit.prevent="onSubmit">
      <h2>登录</h2>
      <label>
        <span>账号</span>
        <input v-model.trim="username" autocomplete="username" placeholder="admin" />
      </label>
      <label>
        <span>密码</span>
        <input v-model="password" type="password" autocomplete="current-password" placeholder="••••••••" />
      </label>
      <p v-if="error" class="error">{{ error }}</p>
      <button type="submit" :disabled="loading">{{ loading ? '登录中…' : '进入系统' }}</button>
      <p class="hint">默认账号 admin / admin123</p>
    </form>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const username = ref('admin')
const password = ref('')
const loading = ref(false)
const error = ref('')

async function onSubmit() {
  error.value = ''
  if (!username.value || !password.value) {
    error.value = '请输入账号和密码'
    return
  }
  loading.value = true
  try {
    await auth.login(username.value, password.value)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirect || '/')
  } catch (e) {
    error.value = e.message || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  align-items: stretch;
}

.hero {
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  padding: 8vh 8vw 10vh;
  position: relative;
  overflow: hidden;
}

.hero::after {
  content: '';
  position: absolute;
  inset: 12% 18% auto auto;
  width: min(42vw, 420px);
  aspect-ratio: 4 / 3;
  border: 1px solid rgba(200, 240, 106, 0.28);
  border-radius: 18px;
  background:
    linear-gradient(135deg, rgba(61, 186, 122, 0.18), transparent 60%),
    repeating-linear-gradient(
      -18deg,
      transparent,
      transparent 10px,
      rgba(140, 190, 160, 0.08) 10px,
      rgba(140, 190, 160, 0.08) 11px
    );
  transform: rotate(-6deg);
  pointer-events: none;
}

.brand {
  margin: 0 0 18px;
  letter-spacing: 0.22em;
  font-size: 12px;
  color: var(--accent-2);
  font-weight: 600;
}

h1 {
  margin: 0;
  font-family: Syne, 'IBM Plex Sans', sans-serif;
  font-size: clamp(42px, 6vw, 72px);
  line-height: 0.95;
  letter-spacing: -0.03em;
}

.sub {
  margin: 18px 0 0;
  color: var(--muted);
  max-width: 28ch;
  font-size: 16px;
}

.card {
  margin: auto;
  width: min(420px, calc(100% - 48px));
  padding: 36px 32px 28px;
  border: 1px solid var(--line);
  border-radius: 20px;
  background: var(--panel);
  backdrop-filter: blur(14px);
  box-shadow: var(--shadow);
  display: grid;
  gap: 16px;
}

.card h2 {
  margin: 0 0 8px;
  font-family: Syne, sans-serif;
  font-size: 28px;
}

label {
  display: grid;
  gap: 8px;
}

label span {
  font-size: 13px;
  color: var(--muted);
}

input {
  width: 100%;
  border: 1px solid var(--line);
  background: rgba(8, 16, 13, 0.65);
  color: var(--text);
  border-radius: 12px;
  padding: 12px 14px;
  outline: none;
}

input:focus {
  border-color: rgba(61, 186, 122, 0.7);
  box-shadow: 0 0 0 3px rgba(61, 186, 122, 0.15);
}

button {
  margin-top: 8px;
  border: 0;
  border-radius: 12px;
  padding: 13px 16px;
  background: linear-gradient(135deg, var(--accent), #2f9a65);
  color: #04140c;
  font-weight: 600;
  cursor: pointer;
}

button:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.error {
  margin: 0;
  color: var(--danger);
  font-size: 13px;
}

.hint {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 12px;
  text-align: center;
}

@media (max-width: 900px) {
  .page {
    grid-template-columns: 1fr;
  }
  .hero {
    padding: 48px 24px 12px;
    min-height: 28vh;
  }
  .hero::after {
    display: none;
  }
}
</style>
