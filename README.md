# video-mid 视频中台

极知AI 视频中台（GB/T 28181 上级），严格按《极知AI-视频中台总体架构》实现。

## 模块

| 包 | 职责 |
|----|------|
| `api` | 北向 REST：设备、预览、回放、云台 |
| `sip` | JAIN-SIP 国标上级：注册/心跳/Catalog/INVITE/BYE |
| `session` | Redis 流引用计数、流生命周期 |
| `media` | ZLM openRtpServer + Hook |
| `ptz` | WebSocket 服务端 + Redis 云台队列 |
| `device` | MySQL 设备表、Catalog 解析入库 |

## 硬性约束

1. 设备等业务元数据只存 **MySQL**，禁止文件扫描/内存/Redis 持久化设备数据  
2. **Redis** 仅运行时：`stream:ref`、播放地址、`ptz:queue`、`gw:session`、`device:online`  
3. 国标码流 RTP/PS 只进 **ZLMediaKit**，Java 不处理媒体字节  

## 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8（库名 `video_mid`，用户 `root` / `123456`）
- Redis
- ZLMediaKit（配置 Hook 指向本服务）

## 初始化

```bash
mysql -uroot -p123456 < init.sql
```

修改 `src/main/resources/application.yml` 中的 SIP / ZLM 地址后：

```bash
mvn -DskipTests package
java -jar target/video-mid.jar
```

## 北向接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/devices` | 设备列表（读 MySQL） |
| POST | `/preview/start` | 直播预览 |
| POST | `/preview/stop` | 停止直播 |
| POST | `/playback/clip` | 按时间回放 |
| POST | `/ptz/move` | 云台（立刻 accepted） |

网关连接：`ws://host:8080/ptz/gateway?id=小区编号`

ZLM Hook：`/index/hook/on_stream_changed`、`/index/hook/on_stream_none_reader`
