# 视频中台接口文档（YAPI）

范围只有两部分：

- **云台监控**：云台设备、预览、方向 / 变倍 / 对焦 / 广角 / 预置位 / 抓拍
- **业务端**：目录、设备、直播、录像、片段截取

不含管理端设备维护、ZLM Hook、开放接口 `/api/open/**`、国标独立入口，也不含 `GET /api/uniview/config`、`GET /api/uniview/live/readiness`、`GET /api/uniview/devices`。

YAPI 导入文件：`docs/yapi-ptz-biz-openapi.json`（OpenAPI 3.0）。项目 → 数据管理 → 导入数据 → 类型选 swagger / openapi → 上传该 JSON。不要导入 `docs/yapi-openapi.json`，那是旧的全量文件。

- 服务地址：`http://{host}:8090`（当前示例：`http://8.130.74.232:8090`）
- 播放地址在 ZLM：`http://8.130.74.232:8080`
- JSON 接口：`Content-Type: application/json; charset=UTF-8`
- 鉴权：`auth.enabled=true` 时，除登录外加 `Authorization: Bearer {token}`。`<video>` 带不了 Header，URL 上加 `?token=` 或 `&token=`。当前 `auth.enabled` 为 `false`，可不带 Token。
- 示例设备：`UV_10135`（演示球机）。一台设备只对接一个平台。云台发给这台摄像机自己的地址；历史录像在它绑定的录像机上，不在摄像机上。

统一包装（文件流接口除外）：

```json
{ "code": 0, "message": "success", "data": {} }
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| code | integer | `0` 成功；业务失败为 `-1`（HTTP 多为 400/500，未登录为 401） |
| message | string | 成功为 `success`；失败为原因，例如 `设备未配置宇视地址`、`不支持的云台方向`、`仅「已启用」设备可直播` |
| data | object / array / null | 成功时的业务数据；失败时多为 `null` |

返回里只有调用方会用到的字段。没有密码，也没有经纬度、创建时间、服务器磁盘路径。

`auth.enabled=true` 时先 **POST** `/api/auth/login`。Body：`username`、`password`。成功 `data`：`token`、`tokenType`（`Bearer`）、`expiresIn`（秒）、`user`（`id`、`username`、`nickname`、`status`、`lastLoginAt`）。

---

# 一、云台监控

只列出已配置宇视地址的设备。指令发到这台摄像机自己的账号。

方向键按住时重复调用移动，松开传 `direction=stop`。变倍、对焦、广角是点按：服务端下发动作后自行停止。

`speed` 不传则为 `4`，实际限制在 **1~8**。

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

云台控制成功时，`data` 都有这些字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| success | boolean | 固定 `true` |
| mock | boolean | 真实对接为 `false` |
| live | boolean | 真实对接为 `true` |
| action | string | 本次动作：`move` / `zoom` / `focus` / `wide-angle` / `goto-preset` / `set-preset` / `snapshot` |
| deviceId | string | 设备编码 |
| response | string | 摄像机原始 JSON 字符串 |

方向、变倍、对焦、广角另外返回 `ptzCmd`（下发的指令数值）和 `speed`（实际速度）。调用预置位另有 `presetIndex`。保存预置位另有 `presetIndex`、`name`、`overwrite`。抓拍另有 `channelType`。

---

## 1.1 云台设备列表

- **说明**：给云台页选设备、展示预置位。只含已配置宇视地址的设备。预置位来自摄像机；暂时查不到时 `presets` 为空数组，设备仍然返回。
- **GET** `/api/uniview/ptz/devices`
- 无参数

`data` 为数组。每一项：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| deviceId | string | 设备编码，如 `UV_10135` |
| name | string | 名称，可空 |
| presets | array | 预置位。项为 `index`（integer，编号）、`name`（string，名称） |

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "deviceId": "UV_10135",
      "name": "演示球机",
      "presets": [{ "index": 1, "name": "门口" }]
    }
  ]
}
```

---

## 1.2 云台设备详情

- **说明**：取这台摄像机已启用码流的播放地址。**不拉流。** 要出画面再调 1.3。
- **GET** `/api/uniview/devices/{deviceId}`

| 参数 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| deviceId | path | string | 是 | 设备编码 |

`data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| deviceId | string | 设备编码 |
| name | string | 名称 |
| streams | array | 已启用码流 |

`streams[]`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| channelType | string | 可见光为 `visible` |
| streamType | string | `main` 主码流 / `sub` 辅码流 / `third` 第三流 |
| streamUrl | string | HTTP-FLV。此时 ZLM 上可能还没有流 |

未配置宇视地址：`设备未配置宇视地址`。

---

## 1.3 开始预览

- **说明**：云台页选中设备后调用。不传 `streamType` 时用 `sub`。这路在 ZLM 上已经有流就直接返回同一地址；没有才向摄像机取地址并拉流。最后一个观众离开后停止拉流。播放器用 `playUrl`。
- **POST** `/api/preview/start`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| deviceId | string | 是 | 设备编码 |
| streamType | string | 否 | `main` / `sub` / `third`，默认 `sub` |

`data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| deviceId | string | 设备编码 |
| streamType | string | 实际码流 |
| streamUrl | string | HTTP-FLV |
| playUrl | string | 与 `streamUrl` 相同 |
| liveEnabled | boolean | 是否业务直播流 |
| ref | integer | 当前观看人数，由 ZLM 回调维护，本接口不加人数 |
| countBy | string | 固定 `zlm-hook` |

示例播放地址：`http://8.130.74.232:8080/live/uv_UV_10135_sub.live.flv`

失败：`码流未注册`、`码流地址为空，请先注册 streamUrl`。

---

## 1.4 方向移动

- **说明**：按住方向键时重复调用；松开传 `stop`。
- **POST** `/api/uniview/ptz/move`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| deviceId | string | 是 | 设备编码 |
| direction | string | 是 | 见方向表 |
| speed | integer | 否 | 默认 4，限制 1~8 |

```json
{ "deviceId": "UV_10135", "direction": "up", "speed": 4 }
```

---

## 1.5 变倍

- **说明**：点按拉近或拉远，服务端下发后自行停止。
- **POST** `/api/uniview/ptz/zoom`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| deviceId | string | 是 | 设备编码 |
| action | string | 是 | 见变倍表 |
| speed | integer | 否 | 默认 4，限制 1~8 |

---

## 1.6 对焦

- **说明**：点按近焦或远焦，服务端下发后自行停止。
- **POST** `/api/uniview/ptz/focus`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| deviceId | string | 是 | 设备编码 |
| action | string | 是 | 见对焦表 |
| speed | integer | 否 | 默认 4，限制 1~8 |

---

## 1.7 广角

- **说明**：点按拉远。速度固定 4。
- **POST** `/api/uniview/ptz/wide-angle`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| deviceId | string | 是 | 设备编码 |

```json
{ "deviceId": "UV_10135" }
```

---

## 1.8 调用预置位

- **说明**：转到摄像机上已有的预置位。编号来自 1.1 的 `presets[].index`。
- **POST** `/api/uniview/ptz/preset/goto`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| deviceId | string | 是 | 设备编码 |
| index | integer | 是 | 预置位编号 |

---

## 1.9 保存预置位

- **说明**：把当前姿态存成预置位。同号已存在且 `overwrite` 不为 true 时失败。
- **POST** `/api/uniview/ptz/preset/save`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| deviceId | string | 是 | 设备编码 |
| index | integer | 是 | 预置位编号 |
| name | string | 是 | 名称，不能为空白 |
| overwrite | boolean | 否 | 默认 false。已存在且不覆盖时失败 |

---

## 1.10 抓拍

- **说明**：向摄像机要一张当前画面。没有单独的图片 URL，内容在 `data.response`。
- **POST** `/api/uniview/ptz/snapshot`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| deviceId | string | 是 | 设备编码 |
| channelType | string | 否 | 默认 `visible` |

---

# 二、业务端 `/api/biz`

设备状态：`0` 不可用，`1` 已启用，`2` 已停用。  
直播只允许 `status=1`。回放和截取在 `status=2` 时失败，文案为 `设备已停用，无法回放`。

除目录树、片段文件流外，查询都是 **POST + JSON Body**。

设备列表和详情只返回下面这些字段，不含登录信息、码流明细：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| deviceId | string | 设备编码 |
| name | string | 名称，可空 |
| address | string | 安装地址，可空 |
| folderId | integer | 所属目录 ID，未分组时为 null |
| status | integer | `0` / `1` / `2` |
| playable | boolean | `status` 不是已停用时为 true，可以回放、截取 |
| livePlayable | boolean | 仅 `status=1` 为 true，可以直播 |

---

## 2.1 目录树

- **说明**：左侧目录。设备数已汇总子目录。
- **GET** `/api/biz/folders/tree`
- 无参数

`data` 为根节点数组。节点：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | integer | 目录 ID |
| parentId | integer | 父目录 ID，根为 null |
| name | string | 名称 |
| sortNo | integer | 同级排序，小的在前 |
| deviceCount | integer | 本级直接挂的设备数 |
| totalDeviceCount | integer | 含子目录的设备总数 |
| children | array | 子目录，结构相同；没有则为空数组 |

---

## 2.2 设备列表

- **说明**：按目录列出设备，含已停用。用 `playable`、`livePlayable` 决定能不能回放、直播。
- **POST** `/api/biz/devices/list`
- Body 可以是 `{}`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| folderId | integer | 否 | 目录 ID。不传返回全部 |
| includeChildren | boolean | 否 | 传了 `folderId` 时是否包含子目录，默认 true |

`data` 为 2 节开头的设备对象数组。

---

## 2.3 设备详情

- **说明**：单台设备，并带上业务直播流地址。不拉流。
- **POST** `/api/biz/devices/detail`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| deviceId | string | 是 | 设备编码 |

`data` 为设备对象，另加：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| liveStream | object / null | 没有业务直播流时为 null |

`liveStream`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| streamType | string | `main` / `sub` / `third` |
| streamUrl | string | HTTP-FLV。此时可能尚未拉流 |

---

## 2.4 开始直播

- **说明**：仅已启用设备。已配置宇视地址时按业务直播流拉流（优先辅码流）。返回和 1.3 相同，播放 `playUrl`。
- **POST** `/api/biz/devices/live`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| deviceId | string | 是 | 设备编码 |

失败：`仅「已启用」设备可直播`、`设备未配置可直播码流`。

---

## 2.5 有录像的日期

- **说明**：给回放日历打点。已绑定录像机的设备查录像机；没绑定的查中台本地 MP4。已停用设备不能查。
- **POST** `/api/biz/devices/recording-days`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| deviceId | string | 是 | 设备编码 |
| year | integer | 是 | 年 |
| month | integer | 是 | 月，1~12 |

`data` 为 `yyyy-MM-dd` 字符串数组，升序。

---

## 2.6 录像列表

- **说明**：查一段时间里的录像，新的在前。已停用设备不能查。
- **POST** `/api/biz/devices/recordings`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| deviceId | string | 是 | 设备编码 |
| from | integer | 否 | 开始毫秒时间戳。不传不限起点 |
| to | integer | 否 | 结束毫秒时间戳。不传不限终点 |

与文件时段有交集即返回。

`data[]` 公共字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| deviceId | string | 设备编码 |
| fileName | string | 文件名 |
| recordTime | integer | 开始毫秒 |
| endTime | integer | 结束毫秒。本地文件探测失败时可能没有 |
| size | integer | 字节。录像机上的文件可能为 0 |

已绑定录像机时另有 `source`，值为 `nvr`。这种记录**没有** `videoUrl`，播放走 2.7。

没绑定录像机时是本地 MP4，另有：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| durationSeconds | number | 时长秒。探测失败时没有 |
| videoUrl | string | 可直接播放，指向 `/api/open/recordings/{deviceId}/{fileName}`，不需登录 |

片段截取（2.8、2.9）只切本地 MP4，不切录像机上的历史录像。

---

## 2.7 录像机回放

- **说明**：`source=nvr` 的记录用这个地址播放。服务向录像机取这段时间的流，转成 FLV 再输出。不是 JSON。
- **GET** `/api/recordings/playback.flv`

| 参数 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| deviceId | query | string | 是 | 设备编码 |
| begin | query | integer | 是 | 开始时间。毫秒或秒都可以，大于 `1e10` 按毫秒 |
| end | query | integer | 是 | 结束时间，规则同 begin |
| token | query | string | 否 | `auth.enabled=true` 时，`<video>` 把登录 token 放这里 |

`begin` 用列表里的 `recordTime`，拖动进度时加上偏移毫秒。`end` 用 `endTime`。响应 `Content-Type` 为 `video/x-flv`。

没绑定录像机：`该设备没有绑定录像设备`。开始不早于结束：`回放开始时间必须早于结束时间`。

---

## 2.8 按时间点截取

- **说明**：以 `at` 为中心，前后各 `seconds` 秒，从本地 MP4 切出一段。只生成播放地址，文件在访问 2.10 时才输出。
- **GET** `/api/biz/devices/{deviceId}/clip`

| 参数 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| deviceId | path | string | 是 | 设备编码 |
| at | query | string | 是 | 时间点。10 位是秒，11~13 位是毫秒 |
| seconds | query | integer | 否 | 前后各取的秒数，默认 30，须大于 0，最大 150 |

成功 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| deviceId | string | 设备编码 |
| at | integer | 时间点，已换成毫秒 |
| seconds | integer | 实际使用的前后秒数 |
| windowStart | integer | 请求窗口起点，`at - seconds×1000` |
| windowEnd | integer | 请求窗口终点，`at + seconds×1000` |
| startTime | integer | 片段实际开始，受录像覆盖裁切 |
| endTime | integer | 片段实际结束 |
| durationSeconds | number | 实际时长秒 |
| videoUrl | string | 给播放器的 MP4 地址，即 2.10 |
| clipUrl | string | 与 `videoUrl` 相同 |
| sourceFiles | array | 参与截取的原始文件名 |
| clipFileName | string | 缓存片段文件名 |
| size | integer | 片段字节数 |

该时间点没有本地录像：`该时间点无可用录像`。

---

## 2.9 批量截取

- **说明**：一次切多段。Body 是 **JSON 数组**，不是 `{ "items": [] }`。最多 50 条。单条失败不影响其它条。
- **POST** `/api/biz/clips`

数组每一项：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| deviceId | string | 是 | 设备编码 |
| at | integer / string | 是 | 时间点。10 位是秒，11~13 位是毫秒。别名 `timestamp` / `time` / `ts` |
| seconds | integer | 否 | 前后各取的秒数，默认 30，最大 150 |

```json
[
  { "deviceId": "UV_10135", "at": 1730000030000, "seconds": 30 }
]
```

`data` 与请求顺序一致。成功条 `ok=true`，并带 2.8 的那些字段。失败条 `ok=false`，并有 `deviceId`、`at`、`seconds`、`error`。

---

## 2.10 片段文件

- **说明**：2.8、2.9 里 `videoUrl` 的实际文件。响应是 MP4，不是 JSON。
- **GET** `/api/biz/devices/{deviceId}/clip/file`

| 参数 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| deviceId | path | string | 是 | 设备编码 |
| at | query | string | 是 | 与截取时相同的时间点 |
| seconds | query | integer | 否 | 与截取时相同，默认 30 |
| token | query | string | 否 | 开启鉴权时给 `<video>` 用 |

`Content-Type` 为 `video/mp4`。
