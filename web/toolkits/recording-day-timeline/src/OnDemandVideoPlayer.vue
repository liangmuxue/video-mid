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
import { loadAndPlayOne, unloadVideo } from './onDemandPlay.js'

const emit = defineEmits(['timeupdate', 'ended', 'play', 'pause'])
const el = ref(null)

async function playOne(url, seekSeconds = 0) {
  const video = el.value
  if (!video || !url) return
  await loadAndPlayOne(video, { url, seekSeconds })
}

function stopAndUnload() {
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
