# RecordingDayTimeline · 历史录像全天进度条工具包

独立前端工具包：只读录像接口，映射为当天 **00:00:00 ~ 23:59:59** 的 24 小时进度条。

> **约束遵守**
> - 仅新增本目录代码，不修改项目其他业务页
> - CSS 使用 `rdt-` 前缀 + 组件 `scoped`，隔离样式
> - **无**开始/结束时间选择框（仅日期）
> - **只读**录像数据，不提供新增/编辑/删除

## 功能

1. 固定展示选中日全天进度条  
2. 点击「播放」→ 跳到当天最早有录像片段起点并播放  
3. 无录像时间段灰色且 `pointer-events: none`，不可点；有录像段可点跳转  
4. 当天无录像 → 全灰 + 播放按钮禁用  
5. 通过注入的 `fetchRecordings` / `getVideoUrl` 对接现有接口，不耦合业务实现
6. **按需拉流**：播放到哪个时段才请求该时段视频；同一时刻只持有 1 路 `src`；切换前 unload 旧流；禁止一次性预加载/下载全天录像（时间轴仅拉元数据列表）
7. **进度条位置**：0–24 小时全天时间轴固定在播放器最底部；已去掉原生分片进度条及分片计数（如 xx/300）
8. **影视轴交互**：滚轮缩放、绿段按住拖拽滑块实时选时换流、拖拽框选放大、Alt/Shift+拖拽平移、双击/「全天」还原；放大后显示分秒刻度；灰区不可点/不可拖

## 目录

```
recording-day-timeline/
  src/
    index.js
    RecordingDayTimeline.vue
    DayTimelineBar.vue       # 影视风格可缩放时间轴
    timelineZoom.js          # 缩放/平移/刻度
    timeUtils.js
    onDemandPlay.js          # 按需拉流工具
    OnDemandVideoPlayer.vue  # 单路按需播放器
  demo/
    index.html
    DemoApp.vue
    main.js
    vite.config.js
  README.md
  package.json
```

## 安装 Demo 依赖并运行

```bash
cd web/toolkits/recording-day-timeline
npm install
npm run demo
```

浏览器打开：http://localhost:5199/

## 在现有页面挂载

业务侧已在设备列表弹层使用 `web/src/components/RecordingPlaybackPanel.vue`（不新开路由）。
其余页面也可自行嵌入：

```vue
<script setup>
import { RecordingDayTimeline } from '../../toolkits/recording-day-timeline/src'
import { fetchRecordings, recordingFileUrl } from '../api/device'

defineProps({ deviceId: String })
</script>

<template>
  <!-- 隐藏/不要再放起止时间 datetime-local -->
  <RecordingDayTimeline
    :device-id="deviceId"
    :fetch-recordings="(id, p) => fetchRecordings(id, p)"
    :get-video-url="(id, name) => recordingFileUrl(id, name)"
    :clip-seconds="300"
    @play="(e) => console.log('play', e)"
  />
</template>
```

开放接口示例：

```js
fetchRecordings: (id, { from, to }) =>
  http.get(`/api/open/devices/${id}/recordings`, { params: { from, to } })

getVideoUrl: (id, fileName, record) =>
  record.videoUrl || `${base}/api/open/recordings/${id}/${fileName}`
```

## Props

| 属性 | 说明 |
|------|------|
| `deviceId` | 设备 ID |
| `initialDate` | 初始日期 `yyyy-MM-dd` |
| `clipSeconds` | 无 endTime 时默认片长，默认 300 |
| `fetchRecordings` | `(deviceId, {from,to}) => Promise<Array>` |
| `getVideoUrl` | `(deviceId, fileName, record) => url` |
| `showPlayer` | 是否内置 `<video>`，默认 true |

## 事件

- `play` / `seek`：开始或跳转播放  
- `loaded`：当天数据加载完成  
- `date-change`：日期变更  
- `error`：加载失败  

工具包内部会把 `from/to` 固定为当天 `00:00:00` / `23:59:59` 再请求接口，页面无需再提供起止时间控件。
