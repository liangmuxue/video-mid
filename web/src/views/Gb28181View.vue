<template>
  <AppShell>
    <section class="gb-page">
      <header class="gb-head">
        <div>
          <h1>国标 GB28181</h1>
          <p class="sub">
            {{ config?.mock ? '模拟下级平台' : '真实 SIP 上级（骨架）' }} ·
            <span class="badge" :class="config?.mock ? 'mock' : 'live'">
              {{ config?.mock ? 'mock' : 'live' }}
            </span>
          </p>
        </div>
        <div class="head-actions">
          <button
            v-if="!config?.mock"
            type="button"
            class="ghost"
            :disabled="reloadingRegistry"
            @click="reloadRegistry"
          >
            {{ reloadingRegistry ? '同步中…' : '从 DB 同步 Catalog' }}
          </button>
          <button type="button" class="ghost" @click="reload">刷新</button>
        </div>
      </header>

      <p v-if="error" class="error">{{ error }}</p>
      <p v-if="loading" class="muted">加载中…</p>

      <template v-else>
        <div class="grid2">
          <article class="panel">
            <h2>上级平台配置</h2>
            <dl v-if="config">
              <div><dt>SIP ID</dt><dd class="mono">{{ config.upper?.sipId }}</dd></div>
              <div><dt>SIP 域</dt><dd class="mono">{{ config.upper?.sipDomain }}</dd></div>
              <div><dt>地址</dt><dd class="mono">{{ config.upper?.sipIp }}:{{ config.upper?.sipPort }}</dd></div>
              <div><dt>RTP 端口</dt><dd class="mono">{{ config.media?.rtpPortMin }} – {{ config.media?.rtpPortMax }}</dd></div>
              <div><dt>传输</dt><dd>{{ config.media?.transport }}</dd></div>
            </dl>
          </article>

          <article class="panel">
            <h2>运行状态</h2>
            <dl v-if="status">
              <div><dt>模式</dt><dd>{{ status.mode }}</dd></div>
              <div><dt>注册设备</dt><dd>{{ status.registeredDevices }}</dd></div>
              <div><dt>业务端国标直播</dt><dd>{{ status.preferForBizLive ? '已开启' : '关闭' }}</dd></div>
              <div v-if="status.sip"><dt>SIP</dt><dd>{{ status.sip.note || status.sip.mode }}</dd></div>
              <div v-if="status.sip?.sipStackRunning != null"><dt>SIP 栈</dt><dd>{{ status.sip.sipStackRunning ? '运行中' : '未运行' }}</dd></div>
              <div v-if="status.sip?.sipSessions != null"><dt>SIP 会话</dt><dd>{{ status.sip.sipSessions }}</dd></div>
              <div v-if="status.sip?.mediaIp"><dt>媒体 IP</dt><dd class="mono">{{ status.sip.mediaIp }}</dd></div>
              <div v-if="status.sip?.zlmApp"><dt>ZLM App</dt><dd class="mono">{{ status.sip.zlmApp }}</dd></div>
              <div><dt>说明</dt><dd>{{ status.note }}</dd></div>
            </dl>
            <p class="hint">
              live 模式：Catalog 来自 SIP 注册表（开发期可 DB 种子）；INVITE 开 ZLM RTP 端口，不使用 RTSP。
            </p>
          </article>
        </div>

        <article class="panel" :class="{ ok: sync?.ok, bad: sync && !sync.ok }">
          <div class="panel-head">
            <h2>业务数据一致性（任务 2.1）</h2>
            <span v-if="sync" class="sync-tag">{{ sync.ok ? '一致' : '不一致' }}</span>
          </div>
          <p v-if="sync?.ok" class="ok-text">Catalog 与 MySQL 设备/通道 ID 完全一致。</p>
          <ul v-else-if="sync" class="sync-list">
            <li v-if="sync.missingDevices?.length">缺少设备：{{ sync.missingDevices.join(', ') }}</li>
            <li v-if="sync.missingChannels?.length">缺少通道：{{ sync.missingChannels.join(', ') }}</li>
            <li v-if="sync.extraDevices?.length">多余设备：{{ sync.extraDevices.join(', ') }}</li>
            <li v-if="sync.extraChannels?.length">多余通道：{{ sync.extraChannels.join(', ') }}</li>
          </ul>
          <p class="hint">请执行 <code>sql/uniview_mock_seed.sql</code> 与 mock-devices.json 对齐。</p>
        </article>

        <article v-if="!config?.mock && sipSessions.length" class="panel">
          <h2>SIP REGISTER 会话</h2>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>设备 ID</th>
                  <th>Contact</th>
                  <th>过期</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="s in sipSessions" :key="s.deviceId">
                  <td class="mono">{{ s.deviceId }}</td>
                  <td class="mono">{{ s.contactHost }}:{{ s.contactPort }}</td>
                  <td>{{ s.expiresSeconds }}s</td>
                  <td>
                    <button type="button" class="link" @click="queryCatalog(s.deviceId)">Catalog 查询</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </article>

        <article class="panel">
          <div class="panel-head">
            <h2>{{ config?.mock ? '模拟 Catalog' : '注册 Catalog' }}</h2>
            <a class="link" :href="catalogXmlUrl" target="_blank" rel="noopener">查看 catalog.xml</a>
          </div>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>设备</th>
                  <th>国标 ID</th>
                  <th>通道</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <template v-for="d in catalog" :key="d.deviceId">
                  <tr v-for="(ch, idx) in d.channels || []" :key="ch.channelId">
                    <td v-if="idx === 0" :rowspan="(d.channels || []).length">{{ d.name }}</td>
                    <td v-if="idx === 0" :rowspan="(d.channels || []).length" class="mono">{{ d.gbDeviceId }}</td>
                    <td>
                      <div class="mono">{{ ch.channelId }}</div>
                      <div class="muted">{{ ch.streamName }}</div>
                    </td>
                    <td>
                      <button type="button" class="link" @click="testInvite(ch.channelId)">INVITE</button>
                      <button v-if="!config?.mock" type="button" class="link danger" @click="stopInvite(ch.channelId)">BYE</button>
                    </td>
                  </tr>
                </template>
              </tbody>
            </table>
          </div>
        </article>

        <article v-if="inviteResult" class="panel invite-result">
          <h2>INVITE 结果</h2>
          <p v-if="inviteResult.playSource" class="hint">
            播放来源：{{ inviteResult.playSource }}
            <span v-if="inviteResult.rtpPlayUrl"> · RTP 地址：{{ inviteResult.rtpPlayUrl }}</span>
          </p>
          <pre>{{ JSON.stringify(inviteResult, null, 2) }}</pre>
          <StreamPlayer v-if="inviteResult.playUrl" :url="inviteResult.playUrl" />
        </article>
      </template>
    </section>
  </AppShell>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import AppShell from '../components/AppShell.vue'
import StreamPlayer from '../components/StreamPlayer.vue'
import {
  fetchGb28181Catalog,
  fetchGb28181Config,
  fetchGb28181Status,
  fetchGb28181SyncCheck,
  fetchGb28181SipSessions,
  gb28181CatalogXmlUrl,
  gb28181Invite,
  gb28181Bye,
  gb28181SipCatalogQuery,
  reloadGb28181RegistryFromDb
} from '../api/gb28181'

const loading = ref(true)
const reloadingRegistry = ref(false)
const error = ref('')
const config = ref(null)
const status = ref(null)
const sync = ref(null)
const catalog = ref([])
const sipSessions = ref([])
const inviteResult = ref(null)
const catalogXmlUrl = gb28181CatalogXmlUrl()

async function reload() {
  loading.value = true
  error.value = ''
  try {
    config.value = await fetchGb28181Config()
    status.value = await fetchGb28181Status()
    sync.value = await fetchGb28181SyncCheck()
    catalog.value = await fetchGb28181Catalog()
    if (!config.value?.mock) {
      try {
        sipSessions.value = await fetchGb28181SipSessions()
      } catch {
        sipSessions.value = []
      }
    } else {
      sipSessions.value = []
    }
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function reloadRegistry() {
  reloadingRegistry.value = true
  error.value = ''
  try {
    await reloadGb28181RegistryFromDb()
    await reload()
  } catch (e) {
    error.value = e.message || 'Catalog 同步失败'
  } finally {
    reloadingRegistry.value = false
  }
}

async function queryCatalog(deviceId) {
  try {
    await gb28181SipCatalogQuery(deviceId)
    await reload()
  } catch (e) {
    error.value = e.message || 'Catalog 查询失败'
  }
}

async function testInvite(channelId) {
  try {
    inviteResult.value = await gb28181Invite(channelId)
  } catch (e) {
    error.value = e.message || 'INVITE 失败'
  }
}

async function stopInvite(channelId) {
  try {
    inviteResult.value = await gb28181Bye(channelId)
  } catch (e) {
    error.value = e.message || 'BYE 失败'
  }
}

onMounted(reload)
</script>

<style scoped>
.gb-page { display: grid; gap: 16px; }
.gb-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; }
.head-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.gb-head h1 { margin: 0; font-family: Syne, sans-serif; font-size: 28px; }
.sub { margin: 6px 0 0; color: var(--muted); font-size: 13px; }
.badge { font-size: 11px; padding: 2px 8px; border-radius: 999px; }
.badge.mock { background: rgba(200,240,106,.2); color: var(--accent-2); }
.badge.live { background: rgba(61,186,122,.2); color: var(--accent); }
.grid2 { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
@media (max-width: 900px) { .grid2 { grid-template-columns: 1fr; } }
.panel {
  border: 1px solid var(--line); border-radius: 16px; padding: 16px; background: var(--panel);
}
.panel.ok { border-color: rgba(61,186,122,.5); }
.panel.bad { border-color: rgba(239,107,91,.5); }
.panel h2 { margin: 0 0 12px; font-size: 17px; }
.panel-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; margin-bottom: 8px; }
.panel-head h2 { margin: 0; }
dl { margin: 0; display: grid; gap: 8px; }
dl > div { display: grid; grid-template-columns: 100px 1fr; gap: 8px; font-size: 13px; }
dt { color: var(--muted); }
dd { margin: 0; }
.mono { font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 12px; }
.muted { color: var(--muted); font-size: 12px; }
.hint { font-size: 12px; color: var(--muted); margin: 10px 0 0; }
.error { color: var(--danger); }
.ghost { border: 1px solid var(--line); background: transparent; color: var(--text); border-radius: 10px; padding: 8px 14px; cursor: pointer; }
.link { color: var(--accent); background: none; border: 0; cursor: pointer; text-decoration: none; margin-right: 8px; }
.link.danger { color: var(--danger); }
.sync-tag { font-size: 12px; padding: 2px 8px; border-radius: 999px; background: rgba(61,186,122,.15); }
.panel.bad .sync-tag { background: rgba(239,107,91,.15); color: var(--danger); }
.ok-text { color: var(--accent); margin: 0; }
.sync-list { margin: 0; padding-left: 18px; color: var(--danger); font-size: 13px; }
.table-wrap { overflow: auto; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th, td { border-top: 1px solid var(--line); padding: 10px 8px; text-align: left; vertical-align: top; }
th { color: var(--muted); font-weight: 500; }
.invite-result pre {
  background: rgba(0,0,0,.25); border-radius: 10px; padding: 12px; overflow: auto;
  font-size: 12px; margin: 0 0 12px;
}
code { font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 12px; }
</style>
