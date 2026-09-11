<!--
  设备录像回放面板：封装独立工具包 RecordingDayTimeline，
  供设备列表等业务页嵌入（弹层），不新开独立路由页。
-->
<template>
  <div class="rbp-root">
    <header class="rbp-head">
      <div>
        <h2>录像回放</h2>
        <p class="rbp-sub">
          <span class="mono">{{ deviceId }}</span>
          <span v-if="deviceName"> · {{ deviceName }}</span>
        </p>
      </div>
      <button type="button" class="rbp-close" @click="emit('close')">关闭</button>
    </header>

    <RecordingDayTimeline
      :key="deviceId"
      :device-id="deviceId"
      :fetch-recordings="onFetch"
      :get-video-url="onVideoUrl"
      :clip-seconds="clipSeconds"
      :show-player="true"
    />
  </div>
</template>

<script setup>
import { RecordingDayTimeline } from '../../toolkits/recording-day-timeline/src'
import { fetchRecordings, recordingFileUrl } from '../api/device'

defineProps({
  deviceId: { type: String, required: true },
  deviceName: { type: String, default: '' },
  clipSeconds: { type: Number, default: 300 }
})

const emit = defineEmits(['close'])

function onFetch(id, params) {
  return fetchRecordings(id, params)
}

function onVideoUrl(id, fileName, record) {
  if (record?.videoUrl) return record.videoUrl
  return recordingFileUrl(id, fileName)
}
</script>

<style scoped>
.rbp-root {
  display: grid;
  gap: 14px;
  max-height: min(90vh, 920px);
  overflow: auto;
}
.rbp-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}
.rbp-head h2 {
  margin: 0;
  font-family: Syne, sans-serif;
  font-size: 22px;
}
.rbp-sub {
  margin: 6px 0 0;
  color: var(--muted);
  font-size: 13px;
}
.mono {
  font-family: ui-monospace, Menlo, Consolas, monospace;
}
.rbp-close {
  border: 1px solid var(--line);
  background: transparent;
  color: var(--text);
  border-radius: 12px;
  padding: 8px 14px;
  cursor: pointer;
  flex-shrink: 0;
}
</style>
