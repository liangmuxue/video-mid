<template>
  <div class="demo-page">
    <header class="demo-head">
      <h1>RecordingDayTimeline 工具包 Demo</h1>
      <p>
        独立工具包演示：全天 00:00:00 ~ 23:59:59 进度条；无起止时间选择框；
        灰区不可点；有录像段可点；播放定位到当天最早片段。
      </p>
    </header>

    <section class="demo-card">
      <h2>1. 挂载参数</h2>
      <label>
        <span>deviceId</span>
        <input v-model.trim="deviceId" type="text" placeholder="例如 CAM_EAST_01" />
      </label>
      <label>
        <span>API Base</span>
        <input v-model.trim="apiBase" type="text" placeholder="http://8.130.74.232:8090" />
      </label>
      <label>
        <span>Token（可选，管理端录像接口需要）</span>
        <input v-model.trim="token" type="text" placeholder="Bearer token，可空则走开放接口" />
      </label>
      <label class="check">
        <input v-model="useOpenApi" type="checkbox" />
        使用开放接口 /api/open（无需登录）
      </label>
      <label class="check">
        <input v-model="mockOnFail" type="checkbox" />
        API 不可达时自动使用模拟录像（便于本地演示）
      </label>
      <p class="tip">修改 deviceId / API 后点击下方「刷新」或改日期即可重新拉数。</p>
      <p v-if="usingMock" class="warn">当前后端不可达，工具包实例正在使用本地模拟录像数据。</p>
    </section>

    <section class="demo-card">
      <h2>2. 工具包实例（本页不含起止时间框）</h2>
      <RecordingDayTimeline
        v-if="deviceId"
        :key="deviceId + apiBase + String(useOpenApi) + String(mockOnFail)"
        :device-id="deviceId"
        :fetch-recordings="fetchRecordings"
        :get-video-url="getVideoUrl"
        :clip-seconds="300"
        :show-player="true"
        @play="onPlay"
        @loaded="onLoaded"
        @error="onError"
      />
      <p v-else class="tip">请先填写 deviceId</p>
    </section>

    <section class="demo-card">
      <h2>3. 事件日志</h2>
      <pre class="log">{{ logText }}</pre>
    </section>

    <section class="demo-card">
      <h2>4. 接到现有码流页的示例代码（勿直接改业务，按需粘贴）</h2>
      <pre class="code">{{ integrateSnippet }}</pre>
    </section>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import axios from 'axios'
import { RecordingDayTimeline } from '../src/index.js'

const deviceId = ref('CAM_EAST_01')
const apiBase = ref('http://8.130.74.232:8090')
const token = ref(localStorage.getItem('video_mid_token') || '')
const useOpenApi = ref(true)
const mockOnFail = ref(true)
const usingMock = ref(false)
const logs = ref([])

/** 公开样例视频，仅 Demo 模拟播放用 */
const MOCK_VIDEO = 'https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4'

function pushLog(msg) {
  const line = `[${new Date().toLocaleTimeString()}] ${msg}`
  logs.value = [line, ...logs.value].slice(0, 40)
  logText.value = logs.value.join('\n')
}

const logText = ref('（尚无事件）')

function client() {
  const http = axios.create({
    baseURL: apiBase.value.replace(/\/$/, ''),
    timeout: 3000
  })
  http.interceptors.request.use((config) => {
    if (token.value) {
      config.headers.Authorization = `Bearer ${token.value}`
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
      const msg =
        err.code === 'ECONNABORTED'
          ? '请求超时'
          : err.response?.data?.message ||
            (err.message === 'Network Error' ? '无法连接后端 API（Network Error）' : err.message) ||
            '网络错误'
      return Promise.reject(new Error(msg))
    }
  )
  return http
}

/** 按当天生成几段模拟绿区，方便演示缩放/拖拽滑块 */
function buildMockRecords(from) {
  const day = String(from || '').slice(0, 10)
  return [
    {
      fileName: `${day.replace(/-/g, '')}_091000.mp4`,
      recordTime: `${day} 09:10:00`,
      endTime: `${day} 09:25:00`,
      videoUrl: MOCK_VIDEO
    },
    {
      fileName: `${day.replace(/-/g, '')}_140000.mp4`,
      recordTime: `${day} 14:00:00`,
      endTime: `${day} 14:40:00`,
      videoUrl: MOCK_VIDEO
    },
    {
      fileName: `${day.replace(/-/g, '')}_183000.mp4`,
      recordTime: `${day} 18:30:00`,
      endTime: `${day} 19:05:00`,
      videoUrl: MOCK_VIDEO
    }
  ]
}

async function fetchRecordings(id, { from, to }) {
  try {
    const http = client()
    let list
    if (useOpenApi.value) {
      list =
        (await http.get(`/api/open/devices/${encodeURIComponent(id)}/recordings`, {
          params: { from, to }
        })) || []
    } else {
      list = (await http.get('/api/recordings', { params: { deviceId: id, from, to } })) || []
    }
    usingMock.value = false
    return list
  } catch (e) {
    if (mockOnFail.value) {
      usingMock.value = true
      pushLog(`API 不可达，改用模拟录像：${e?.message || e}`)
      return buildMockRecords(from)
    }
    usingMock.value = false
    throw e
  }
}

function getVideoUrl(id, fileName, record) {
  if (record?.videoUrl) return record.videoUrl
  const base = apiBase.value.replace(/\/$/, '')
  if (useOpenApi.value) {
    return `${base}/api/open/recordings/${encodeURIComponent(id)}/${encodeURIComponent(fileName)}`
  }
  const q = token.value ? `?token=${encodeURIComponent(token.value)}` : ''
  return `${base}/api/recordings/${encodeURIComponent(id)}/${encodeURIComponent(fileName)}${q}`
}

function onPlay(payload) {
  pushLog(`play ${payload.clock} → ${payload.record?.fileName} seek=${payload.seekSeconds}s`)
}

function onLoaded(payload) {
  pushLog(`loaded date=${payload.date} records=${payload.records.length} segments=${payload.segments.length}`)
}

function onError(e) {
  pushLog(`error ${e?.message || e}`)
}

const integrateSnippet = [
  '<!-- 在现有码流页中：隐藏原有起止时间选择，挂载工具包 -->',
  '<script setup>',
  "import { RecordingDayTimeline } from '@/../toolkits/recording-day-timeline/src'",
  "import { fetchRecordings, recordingFileUrl } from '@/api/device'",
  "const deviceId = 'CAM_EAST_01'",
  '</' + 'script>',
  '',
  '<template>',
  '  <RecordingDayTimeline',
  '    :device-id="deviceId"',
  '    :fetch-recordings="(id, p) => fetchRecordings(id, p)"',
  '    :get-video-url="(id, name) => recordingFileUrl(id, name)"',
  '    :clip-seconds="300"',
  '  />',
  '</template>'
].join('\n')
</script>

<style>
html,
body {
  margin: 0;
  background: #0b1410;
  color: #e8f2ec;
  font-family: ui-sans-serif, system-ui, sans-serif;
}
.demo-page {
  max-width: 960px;
  margin: 0 auto;
  padding: 28px 16px 64px;
  display: grid;
  gap: 18px;
}
.demo-head h1 {
  margin: 0 0 8px;
  font-size: 28px;
}
.demo-head p,
.tip {
  color: #8aa396;
  margin: 0;
  line-height: 1.5;
}
.warn {
  margin: 0;
  color: #e0b06a;
  font-size: 13px;
  line-height: 1.5;
}
.demo-card {
  border: 1px solid rgba(140, 180, 150, 0.28);
  border-radius: 16px;
  padding: 16px;
  background: rgba(16, 32, 25, 0.7);
  display: grid;
  gap: 12px;
}
.demo-card h2 {
  margin: 0;
  font-size: 16px;
}
.demo-card label {
  display: grid;
  gap: 6px;
  font-size: 13px;
  color: #8aa396;
}
.demo-card input[type='text'],
.demo-card input:not([type]) {
  border: 1px solid rgba(140, 180, 150, 0.28);
  background: rgba(8, 16, 13, 0.65);
  color: #e8f2ec;
  border-radius: 10px;
  padding: 10px 12px;
}
.demo-card label.check {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #e8f2ec;
}
.log,
.code {
  margin: 0;
  padding: 12px;
  border-radius: 12px;
  background: #0a1210;
  border: 1px solid rgba(140, 180, 150, 0.2);
  overflow: auto;
  font-size: 12px;
  line-height: 1.45;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
