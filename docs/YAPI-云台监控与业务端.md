# 视频中台接口文档（YAPI）

范围：**云台监控**（`/api/uniview`、`/api/preview/start`）+ **业务端**（`/api/biz`，含录像片段截取）。  
不含管理端设备维护、ZLM Hook、开放接口 `/api/open/**`、国标独立入口。

YAPI 导入文件：`docs/yapi-ptz-biz-openapi.json`（OpenAPI 3.0，导入时选 OpenAPI / Swagger）。

- 服务地址：`http://{host}:8090`（当前示例：`http://8.130.74.232:8090`）
- 播放地址在 ZLM：`http://8.130.74.232:8080`
- Content-Type：JSON 接口为 `application/json; charset=UTF-8`
- 鉴权：`auth.enabled=true` 时，除登录外需 `Authorization: Bearer {token}`。给 `<video>` 播的地址带不了 Header，可在 URL 上加 `?token=` / `&token=`。当前 `auth.enabled` 为 `false`，可不带 Token。
- 当前对接模式：`uniview.data-source=live`。每台摄像机使用设备表上自己的 IP、端口、用户名、密码。响应里**没有密码**，只有 `passwordSet`。
- 示例设备：`UV_10135`（`39.185.236.176:10135`，通道 `0`）

统一包装：

```json
{ "code": 0, "message": "success", "data": {} }
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| code | integer | `0` 成功；业务失败为 `-1`（HTTP 多为 400/500） |
| message | string | 成功为 `success`；失败为原因，例如 `宇视设备不存在`、`设备未配置宇视地址`、`不支持的云台方向` |
| data | object / array / null | 业务数据；失败时多为 `null` |

和上一版的差别：

- 云台不再使用配置文件里的单一摄像机，也不再使用模拟设备 `TIC7632_*`。
- 码流不是固定主/子两路。保存设备时向摄像机查询实际启用的码流（主码流 / 辅码流 / 第三流）写入码流表；再次编辑保存会按摄像机当前结果更新。
- 播放地址可以先写在码流表里。第一人观看才拉流，已有人观看则复用，无人观看后停止拉流。

---

## 公共：登录

`auth.enabled=true` 时先登录。

**POST** `/api/auth/login`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| username | string | 是 | 用户名 |
| password | string | 是 | 密码 |

成功 `data`：`token`、`tokenType`（`Bearer`）、`expiresIn`（秒）、`user`（`id` / `username` / `nickname` / `status` / `lastLoginAt`）。

---

# 一、云台监控

设备必须已配置宇视 IP。云台指令发到**这台设备自己的**地址和账号。

方向键按住时重复调用 `move`，松开传 `direction=stop`。变倍、对焦、广角是点按：服务端下发动作后自行停止。

### 方向 `direction`

| 取值 | 含义 |
| --- | --- |
| up / down / left / right | 上 / 下 / 左 / 右 |
| left_up / left_down / right_up / right_down | 四个斜向 |
| stop | 停止 |
| 其它 | 失败：`不支持的云台方向` |

### 变倍 `action`

| 取值 | 含义 |
| --- | --- |
| in / zoom_in / tele | 拉近 |
| out / zoom_out / wide | 拉远 |
| 其它 | 失败：`不支持的变倍动作` |

### 对焦 `action`

| 取值 | 含义 |
| --- | --- |
| near / focus_near | 近焦 |
| far / focus_far | 远焦 |
| 其它 | 失败：`不支持的对焦动作` |

`speed` 默认 `4`，实际会限制在 **1~8**。

云台成功 `data` 常见字段：`success=true`、`mock=false`、`live=true`、`action`、`deviceId`、`ptzCmd`（数值）、`speed`、`response`（摄像机原始 JSON 字符串）。

---

## 1.1 云台设备列表

- **GET** `/api/uniview/ptz/devices`
- 只返回配置了宇视 IP 的设备。`presets` 来自摄像机；摄像机暂时查不到预置位时为空数组，列表仍然返回。

`data[]`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| deviceId | string | 设备编码，如 `UV_10135` |
| name | string | 名称 |
| manufacturer | string | 厂商 |
| model | string | 型号 |
| ptzType | integer | 云台类型 |
| host | string | 摄像机 IP |
| port | integer | LAPI 端口 |
| accessChannel | string | 通道号，IPC 一般为 `0` |
| presets | array | `{ index, name }` |

---

## 1.2 云台设备详情

- **GET** `/api/uniview/devices/{deviceId}`
- 返回码流表里的播放地址。**本接口不拉流。** 要出画面再调 1.3。

`data.streams[]`：`channelId`、`channelType`（`visible`）、`streamType`（`main` / `sub` / `third`）、`streamName`（主码流 / 辅码流 / 第三流）、`streamUrl`、`status`（`ON` 正在拉流，`OFF` 未拉流）。

---

## 1.3 开始预览（按需拉流）

云台页选中设备后调用。优先辅码流，没有再用主码流。

- **POST** `/api/preview/start`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| deviceId | string | 是 | 设备编码 |
| streamType | string | 否 | `main` / `sub` / `third`，默认 `sub` |

成功 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| deviceId | string | 设备编码 |
| streamType | string | 实际码流 |
| streamUrl | string | HTTP-FLV |
| playUrl | string | 与 `streamUrl` 相同，给播放器 |
| liveEnabled | boolean | 是否业务直播流 |
| ref | integer | 当前观看人数，由 ZLM 回调维护，本接口不加人数 |
| countBy | string | 固定 `zlm-hook` |

示例：`http://8.130.74.232:8080/live/uv_UV_10135_sub.live.flv`

拉流规则：这路在 ZLM 上已经存在就直接返回同一地址；不存在才向摄像机取地址并拉流。最后一个观众离开后停止拉流。

---

## 1.4 方向移动

- **POST** `/api/uniview/ptz/move`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| deviceId | string | 是 | 设备编码 |
| direction | string | 是 | 见方向表，停止传 `stop` |
| speed | integer | 否 | 默认 4 |

---

## 1.5 变倍

- **POST** `/api/uniview/ptz/zoom`
- Body：`deviceId`、`action`、`speed`（可选）

## 1.6 对焦

- **POST** `/api/uniview/ptz/focus`
- Body：`deviceId`、`action`、`speed`（可选）

## 1.7 广角

- **POST** `/api/uniview/ptz/wide-angle`
- Body：`{ "deviceId": "UV_10135" }`
- 点按拉远，速度固定 4。

## 1.8 调用预置位

- **POST** `/api/uniview/ptz/preset/goto`
- Body：`deviceId`、`index`（必填）

## 1.9 保存预置位

- **POST** `/api/uniview/ptz/preset/save`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| deviceId | string | 是 | 设备编码 |
| index | integer | 是 | 预置位编号 |
| name | string | 是 | 名称，不能为空白 |
| overwrite | boolean | 否 | 默认 false。已存在且不覆盖时失败 |

## 1.10 抓拍

- **POST** `/api/uniview/ptz/snapshot`
- Body：`deviceId` 必填，`channelType` 默认 `visible`
- 图片内容在 `data.response`（摄像机返回的 JSON 字符串），没有单独的 `imageUrl`。

---

# 二、业务端 `/api/biz`

设备状态：`0` 不可用，`1` 已启用，`2` 已停用。  
直播仅 `status=1`。回放和截取在 `status=2` 时失败。

除目录树外，查询都是 **POST + JSON Body**。

设备上多出来的接入字段（列表、详情都有，**不含密码**）：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| host | string | 宇视 IP，可空 |
| port | integer | 端口，可空 |
| username | string | 用户名，可空 |
| passwordSet | boolean | 是否已保存密码 |
| accessChannel | string | 通道号 |
| accessStatus | string | `unknown` / `online` / `offline` / `auth_failed` |
| accessError | string | 最近一次失败原因，可空 |

码流字段在详情的 `streams[]` 中：`streamType`、`streamIndex`（0/1/2）、`streamName`、`streamUrl`、`status`、`zlmApp`（`live`）、`zlmStream`（`uv_{设备编码}_{类型}`）、`liveEnabled`、`playCount`。`channelId` 宇视为空。

---

## 2.1 目录树

- **GET** `/api/biz/folders/tree`
- 节点含 `deviceCount`（本级）、`totalDeviceCount`（含子目录）、`children`。

## 2.2 设备列表

- **POST** `/api/biz/devices/list`
- Body 可 `{}`。`folderId` 可选；`includeChildren` 默认 `true`。
- 含已停用设备。用 `playable`、`livePlayable` 判断能否回放、直播。不含码流明细，有 `streamCount`。

## 2.3 设备详情

- **POST** `/api/biz/devices/detail`
- Body：`{ "deviceId": "UV_10135" }`
- 含 `streams` 和 `liveStream`（业务直播流摘要，没有则为 `null`）。不拉流。

`liveStream`：`id`、`streamType`、`streamUrl`、`streamName`、`liveEnabled=true`。

## 2.4 开始直播

- **POST** `/api/biz/devices/live`
- Body：`{ "deviceId": "UV_10135" }`
- 仅已启用设备。已配置宇视 IP 时不走国标，按业务直播流拉流（优先辅码流）。返回与 1.3 相同，播放 `playUrl`。
- 失败：`仅「已启用」设备可直播`、`设备未配置可直播码流`。

## 2.5 有录像的日期

- **POST** `/api/biz/devices/recording-days`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| deviceId | string | 是 | 设备编码 |
| year | integer | 是 | 年 |
| month | integer | 是 | 月，1~12 |

`data` 为 `yyyy-MM-dd` 字符串数组，升序。已停用设备不能查。

## 2.6 录像列表

- **POST** `/api/biz/devices/recordings`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| deviceId | string | 是 | 设备编码 |
| from | integer | 否 | 开始毫秒时间戳 |
| to | integer | 否 | 结束毫秒时间戳 |

与文件时段有交集即返回，新的在前。`videoUrl` 指向 `/api/open/recordings/{deviceId}/{fileName}`，不需登录。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| fileName | string | `yyyyMMdd_HHmmss.mp4` |
| recordTime | integer | 开始毫秒 |
| endTime | integer | 结束毫秒，探测失败时可能没有 |
| durationSeconds | number | 时长秒 |
| size | integer | 字节 |
| videoUrl | string | 可直接播放 |

这些录像来自中台录像目录，不是摄像机卡内录像。

## 2.7 批量截取片段

- **POST** `/api/biz/clips`
- Body 是 **JSON 数组**，不是 `{ "items": [] }`。最多 50 条。单条失败不影响其它条。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| deviceId | string | 是 | 设备编码 |
| at | integer | 是 | 时间点。10 位是秒，11~13 位是毫秒。也可用字符串。别名 `timestamp` / `time` / `ts` |
| seconds | integer | 否 | 前后各取的秒数，默认 30，最大 150 |

成功条：`ok=true`，以及 `startTime`、`endTime`、`durationSeconds`、`videoUrl`、`clipUrl`（与 videoUrl 相同）、`sourceFiles`、`clipFileName`、`size`。  
失败条：`ok=false`，`error` 如 `该时间点无可用录像`。

```json
[
  { "deviceId": "UV_10135", "at": 1730000030000, "seconds": 30 }
]
```

`videoUrl` 形如：

`http://8.130.74.232:8090/api/biz/devices/UV_10135/clip/file?at=1730000030000&seconds=30`

该地址返回 MP4 文件流，不是 JSON。
