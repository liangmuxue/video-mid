# ZLM Hook 对接说明（播放人数同步到 Redis）

目标：浏览器/播放器关掉后，ZLM 感知断开，回调 Java，自动减少 Redis `stream:ref:{deviceId}:{main|sub}`。

## 1. 部署 video-mid

确保本服务对外可被 **ZLM 所在机器**访问，例如：

`http://8.130.74.232:8090`

下列接口已免登录：

- `POST /index/hook/on_play`
- `POST /index/hook/on_flow_report`
- `POST /index/hook/on_stream_none_reader`

## 2. 修改 ZLM `config.ini` 的 `[hook]`

把 `{VIDEO_MID}` 换成你的 video-mid 地址（不要末尾斜杠）：

```ini
[hook]
enable=1
timeoutSec=10
retry=1
retry_delay=3.0

on_play=http://{VIDEO_MID}/index/hook/on_play
on_flow_report=http://{VIDEO_MID}/index/hook/on_flow_report
on_stream_none_reader=http://{VIDEO_MID}/index/hook/on_stream_none_reader
```

改完后重启 ZLM。

## 3. 工作原理

| 事件 | 时机 | Java 动作 |
|------|------|-----------|
| `on_play` | 有人开始拉流（HTTP-FLV 等） | Redis +1 |
| `on_flow_report`（`player=true`） | 播放器断开 | Redis -1 |
| `on_stream_none_reader` | 该路流已无人看 | Redis 置 0（兜底） |

码流表里的 `stream_url`（如 `rtmp://host/live/cam01_sub`）会解析出 `app=live`、`stream=cam01_sub`，与 hook 里的 app/stream 匹配后定位到 `deviceId` + `streamType`。

## 4. 注意

1. 管理端预览请直接播 ZLM 的 HTTP-FLV；人数以 hook 为准（`/api/preview/start` 不再 +1）。
2. 推流断开也会触发 `on_flow_report`，但 `player=false`，Java **不会**减播放人数。
3. 若 hook 匹配不上，检查码流 `stream_url` 的 app/stream 是否与 ZLM 一致。
4. 防火墙需放行 ZLM → video-mid 的 HTTP 访问。
