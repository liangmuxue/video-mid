# video-mid

## 数据模型

- `device`：设备表
- `device_stream`：码流表（含 `stream_url`，由注册写入）
- Redis：仅 `stream:ref:{deviceId}:{main|sub}` 播放引用计数

## 关键接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/streams/register` | 注册码流地址（免登录） |
| GET | `/api/devices` | 设备列表（含码流） |
| POST | `/api/preview/start` | 预览（返回已注册地址，计数+1） |
| POST | `/api/preview/stop` | 停止预览（计数-1） |

## 启动

```bash
mysql -uroot -p123456 < init.sql
# 或已有库：mysql -uroot -p123456 < sql/device_and_stream.sql
mvn -DskipTests package && java -jar target/video-mid.jar
cd web && npm install && npm run dev
```
