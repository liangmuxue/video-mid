<!--
  按需拉流播放器：同一时刻最多持有 1 路视频 src。
  无原生 controls，避免出现分片时长进度（如 xx/300）。
  全天进度条由外层 RecordingDayTimeline 底部时间轴承担。
-->
<template>
  <video
    ref="el"
    class="rdt-video rdt-ondemand-video"
    preload="none"
    playsinline
    @timeupdate="emit('timeupdate', $event)"
    @ended="emit('ended', $event)"
    @play="emit('play', $event)"
    @pause="emit('pause', $event)"
  />
</template>

<script setup>
import { onBeforeUnmount, ref } from 'vue'
import mpegts from 'mpegts.js'
import { loadAndPlayOne, unloadVideo } from './onDemandPlay.js'

const emit = defineEmits(['timeupdate', 'ended', 'play', 'pause'])
const el = ref(null)
let flvPlayer = null

function isNvrPlayback(url) {
  return /\/playback\.flv(\?|$)/.test(url || '')
}

function destroyFlv() {
  if (!flvPlayer) return
  const current = flvPlayer
  flvPlayer = null
  try {
    current.pause()
    current.unload()
    current.detachMediaElement()
    current.destroy()
  } catch (_) {
    /* ignore */
  }
}

async function playOne(url, seekSeconds = 0) {
  const video = el.value
  if (!video || !url) return
  if (isNvrPlayback(url)) {
    destroyFlv()
    unloadVideo(video)
    if (!mpegts.getFeatureList().mseLivePlayback) return
    // 录像机回放是 H.264 + G.711。mpegts 不支持 G.711，遇到后会中断整路播放，所以只解视频。
    flvPlayer = mpegts.createPlayer(
      { type: 'flv', url, isLive: true, hasAudio: false, hasVideo: true },
      { enableWorker: false, lazyLoad: false }
    )
    flvPlayer.attachMediaElement(video)
    flvPlayer.load()
    flvPlayer.play().catch(() => {})
    return
  }
  destroyFlv()
  await loadAndPlayOne(video, { url, seekSeconds })
}

function stopAndUnload() {
  destroyFlv()
  unloadVideo(el.value)
}

function getElement() {
  return el.value
}

onBeforeUnmount(() => {
  stopAndUnload()
})

defineExpose({ playOne, stopAndUnload, getElement })
</script>

<style scoped>
.rdt-ondemand-video {
  width: 100%;
  max-height: 420px;
  min-height: 200px;
  background: #000;
  display: block;
  border: 0;
  border-radius: 0;
}
</style>
