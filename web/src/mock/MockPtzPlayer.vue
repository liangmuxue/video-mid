<template>
  <div class="mock-ptz-player">
    <div class="viewport">
      <div class="stage" :style="simStyle">
        <div class="fake-world" aria-hidden="true">
          <span class="mk n">北 · 周界</span>
          <span class="mk s">南 · 入口</span>
          <span class="mk w">西 · 停车场</span>
          <span class="mk e">东 · 岗卡</span>
          <span class="mk c">云台中心</span>
        </div>
        <div class="video-layer" :class="{ faded: streamFailed }">
          <StreamPlayer ref="playerRef" :url="url" @error="streamFailed = true" />
        </div>
      </div>
      <div class="crosshair" aria-hidden="true" />
      <p v-if="simLabel" class="hud">{{ simLabel }}</p>
    </div>
    <p class="iso-note">模拟画面，仅 mock。删除目录 web/src/mock 即可拿掉。</p>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import StreamPlayer from '../components/StreamPlayer.vue'
import { cssTransform, describePose } from './ptzSim'

const props = defineProps({
  url: { type: String, required: true },
  pose: { type: Object, required: true }
})

const playerRef = ref(null)
const streamFailed = ref(false)
const simStyle = computed(() => cssTransform(props.pose))
const simLabel = computed(() => describePose(props.pose))

watch(() => props.url, () => {
  streamFailed.value = false
})

defineExpose({
  getVideoElement: () => playerRef.value?.getVideoElement?.()
})
</script>

<style scoped>
.mock-ptz-player { display: grid; gap: 8px; }
.viewport {
  position: relative;
  aspect-ratio: 16 / 9;
  background: #000;
  border-radius: 12px;
  border: 1px solid var(--line);
  overflow: hidden;
}
.stage {
  width: 100%;
  height: 100%;
  position: relative;
  transform-origin: center center;
  transition: transform 0.16s ease-out, filter 0.16s ease-out;
}
.fake-world {
  position: absolute;
  inset: -40%;
  background:
    radial-gradient(circle at 50% 50%, #1a3320 0%, #07140c 55%, #030806 100%),
    repeating-linear-gradient(0deg, transparent 0 46px, #2d5a3833 46px 48px),
    repeating-linear-gradient(90deg, transparent 0 46px, #2d5a3833 46px 48px);
  background-blend-mode: overlay;
}
.mk {
  position: absolute;
  color: #c8f06a;
  font-size: 13px;
  font-weight: 600;
  text-shadow: 0 0 8px #000;
  white-space: nowrap;
}
.mk.n { top: 12%; left: 50%; transform: translateX(-50%); }
.mk.s { bottom: 12%; left: 50%; transform: translateX(-50%); }
.mk.w { left: 10%; top: 50%; }
.mk.e { right: 10%; top: 50%; color: #7ee0ff; }
.mk.c { left: 50%; top: 50%; transform: translate(-50%, 28px); color: #fff; font-size: 12px; }
.video-layer {
  position: absolute;
  inset: 0;
}
.video-layer.faded :deep(.video) { opacity: 0; }
.video-layer :deep(.player-wrap) { height: 100%; gap: 0; }
.video-layer :deep(.video) {
  height: 100%;
  max-height: none;
  border: 0;
  border-radius: 0;
  object-fit: cover;
  background: transparent;
}
.video-layer :deep(.hint),
.video-layer :deep(.error) { display: none; }
.crosshair {
  pointer-events: none;
  position: absolute;
  inset: 0;
  background:
    linear-gradient(#c8f06a88, #c8f06a88) center/1px 22% no-repeat,
    linear-gradient(#c8f06a88, #c8f06a88) center/22% 1px no-repeat;
}
.hud {
  pointer-events: none;
  position: absolute;
  left: 10px;
  bottom: 10px;
  margin: 0;
  padding: 4px 8px;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.55);
  color: #d8f5c4;
  font-size: 12px;
}
.iso-note { margin: 0; font-size: 11px; color: var(--muted); }
</style>
