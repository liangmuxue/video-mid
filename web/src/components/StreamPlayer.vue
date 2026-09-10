<template>
  <div class="player-wrap">
    <video ref="videoRef" class="video" controls autoplay muted playsinline />
    <p class="hint">码流地址：{{ url }}</p>
    <p v-if="hint" class="hint">{{ hint }}</p>
    <p v-if="err" class="error">{{ err }}</p>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import Hls from 'hls.js'
import mpegts from 'mpegts.js'
import {
  isHttpFlv,
  isHttpPlayable,
  isRtmp,
  normalizeZlmHttpUrl,
  parseRtmpUrl
} from '../utils/streamUrl'

const props = defineProps({
  url: { type: String, required: true }
})

const videoRef = ref(null)
const hint = ref('')
const err = ref('')

let hls = null
let flvPlayer = null

function cleanup() {
  if (hls) {
    hls.destroy()
    hls = null
  }
  if (flvPlayer) {
    try {
      flvPlayer.pause()
      flvPlayer.unload()
      flvPlayer.detachMediaElement()
      flvPlayer.destroy()
    } catch (_) {
      /* ignore */
    }
    flvPlayer = null
  }
  const el = videoRef.value
  if (el) {
    el.removeAttribute('src')
    el.load()
  }
}

function playHls(playUrl, el) {
  hint.value = `HLS 播放：${playUrl}`
  if (Hls.isSupported()) {
    hls = new Hls({ enableWorker: true, lowLatencyMode: true })
    hls.loadSource(playUrl)
    hls.attachMedia(el)
    hls.on(Hls.Events.MANIFEST_PARSED, () => {
      el.play().catch(() => {})
    })
    hls.on(Hls.Events.ERROR, (_, data) => {
      if (data?.fatal) {
        err.value = `HLS 失败：${data.type} / ${data.details}（地址 ${playUrl}）`
      }
    })
    return
  }
  if (el.canPlayType('application/vnd.apple.mpegurl')) {
    el.src = playUrl
    el.play().catch(() => {})
    return
  }
  throw new Error('当前浏览器不支持 HLS')
}

function playFlv(playUrl, el) {
  hint.value = `HTTP-FLV 播放：${playUrl}`
  if (!mpegts.getFeatureList().mseLivePlayback) {
    throw new Error('当前浏览器不支持 MSE/FLV 直播')
  }
  flvPlayer = mpegts.createPlayer(
    { type: 'flv', url: playUrl, isLive: true, hasAudio: true, hasVideo: true },
    { enableStashBuffer: false, stashInitialSize: 128, lazyLoad: false }
  )
  flvPlayer.attachMediaElement(el)
  flvPlayer.load()
  flvPlayer.play().catch(() => {})
  flvPlayer.on(mpegts.Events.ERROR, (type, detail) => {
    err.value = `FLV 失败：${type} / ${detail}（地址 ${playUrl}）`
  })
}

async function attach() {
  cleanup()
  err.value = ''
  hint.value = ''
  const el = videoRef.value
  if (!el || !props.url) return

  try {
    if (isRtmp(props.url)) {
      const info = parseRtmpUrl(props.url)
      if (!info) {
        err.value = 'RTMP 解析失败，示例：rtmp://8.130.74.232/live/cam01_sub'
        return
      }
      // ZLM 的 HLS ts 常要 Cookie，浏览器跨域拉不到 → 优先 HTTP-FLV
      playFlv(info.flvUrl, el)
      return
    }

    if (isHttpPlayable(props.url)) {
      let playUrl = normalizeZlmHttpUrl(props.url)
      if (playUrl !== props.url) {
        hint.value = `已将播放口从 80 纠正为 ZLM HTTP（${playUrl}）`
      }
      if (isHttpFlv(playUrl) || /\.live\.flv/i.test(playUrl)) {
        playFlv(playUrl, el)
      } else if (/\.m3u8(\?|$)/i.test(playUrl) || /\/hls\.m3u8/i.test(playUrl)) {
        playHls(playUrl, el)
      } else {
        el.src = playUrl
        await el.play().catch(() => {})
      }
      return
    }

    err.value = '仅支持 RTMP / HTTP(S) 预览。RTMP 示例：rtmp://8.130.74.232/live/cam01_sub'
  } catch (e) {
    err.value =
      (e.message || '播放失败') +
      '。请确认流已推到 ZLM，且 HTTP 口为 8080（可用 VITE_ZLM_HTTP_PORT 覆盖）。'
    cleanup()
  }
}

onMounted(attach)
watch(() => props.url, attach)
onBeforeUnmount(cleanup)
</script>

<style scoped>
.player-wrap {
  display: grid;
  gap: 8px;
}
.video {
  width: 100%;
  max-height: 420px;
  background: #000;
  border-radius: 12px;
  border: 1px solid var(--line);
}
.hint {
  margin: 0;
  font-size: 12px;
  color: var(--muted);
  word-break: break-all;
}
.error {
  margin: 0;
  font-size: 13px;
  color: var(--danger);
}
</style>
