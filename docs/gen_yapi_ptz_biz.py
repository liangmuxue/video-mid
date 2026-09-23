# -*- coding: utf-8 -*-
"""生成云台监控 + 业务端 OpenAPI 3，供 YApi 导入（范围与 YAPI-云台监控与业务端.md 一致）。"""
import json
from pathlib import Path


def prop(typ, desc, example=None, nullable=False, fmt=None, enum=None, items=None):
    p = {"type": typ, "description": desc}
    if example is not None:
        p["example"] = example
    if nullable:
        p["nullable"] = True
    if fmt:
        p["format"] = fmt
    if enum:
        p["enum"] = enum
    if items is not None:
        p["items"] = items
    return p


def obj(properties, required=None, desc=None):
    s = {"type": "object", "properties": properties}
    if required:
        s["required"] = required
    if desc:
        s["description"] = desc
    return s


def arr(item_schema, desc):
    return {"type": "array", "description": desc, "items": item_schema}


def ref(name):
    return {"$ref": f"#/components/schemas/{name}"}


def q(name, typ, required, desc, example=None, fmt=None, enum=None, default=None):
    schema = {"type": typ, "description": desc}
    if example is not None:
        schema["example"] = example
    if fmt:
        schema["format"] = fmt
    if enum:
        schema["enum"] = enum
    if default is not None:
        schema["default"] = default
    p = {
        "name": name,
        "in": "query",
        "required": required,
        "description": desc,
        "schema": schema,
    }
    if example is not None:
        p["example"] = example
    return p


def path_p(name, typ, desc, example=None):
    p = {
        "name": name,
        "in": "path",
        "required": True,
        "description": desc,
        "schema": {"type": typ, "description": desc},
    }
    if example is not None:
        p["example"] = example
        p["schema"]["example"] = example
    return p


def resp_schema(data_prop, example_data):
    return {
        "type": "object",
        "description": "统一 JSON 响应：code / message / data",
        "properties": {
            "code": prop("integer", "业务状态码：0=成功，非 0=失败（一般为 -1）", 0),
            "message": prop("string", "提示信息：成功一般为 success，失败为错误原因", "success"),
            "data": data_prop,
        },
        "example": {"code": 0, "message": "success", "data": example_data},
    }


def ok(schema_or_ref, example_data=None):
    body = {"schema": schema_or_ref}
    if example_data is not None:
        body["example"] = {"code": 0, "message": "success", "data": example_data}
    return {
        "200": {
            "description": "成功。失败时 HTTP 多为 400，body 中 code!=0，message 为原因，data 常为 null",
            "content": {"application/json": body},
        }
    }


TS = prop("integer", "毫秒时间戳（Unix epoch millis）", 1726560000000, fmt="int64")
DEVICE_STATUS = prop(
    "integer",
    "设备状态：0=不可用（巡检离线，不可手改） 1=已启用（可直播可回放） 2=已停用（灰色可见不可播）",
    1,
    enum=[0, 1, 2],
)

DEVICE_BASE = {
    "id": prop("integer", "设备主键 ID（数据库自增）", 1, fmt="int64"),
    "deviceId": prop("string", "设备业务编码，全局唯一，后续接口都用这个", "CAM_EAST_01"),
    "name": prop("string", "设备名称", "东门球机"),
    "platformId": prop("string", "上级/平台 ID，可空", None, nullable=True),
    "folderId": prop("integer", "所属设备目录 ID，可空表示未归类", 2, True, "int64"),
    "status": DEVICE_STATUS,
    "manufacturer": prop("string", "厂商名称，可空", "宇视", nullable=True),
    "model": prop("string", "设备型号，可空", "IPC-B", nullable=True),
    "address": prop("string", "安装地址/位置描述，可空", "小区东门", nullable=True),
    "ptzType": prop("integer", "云台类型：常见 0=无云台，1=球机等，可空", 1, nullable=True),
    "gatewayId": prop("string", "网关/接入网关 ID，可空", None, nullable=True),
    "longitude": prop("number", "经度，可空", 116.4, nullable=True),
    "latitude": prop("number", "纬度，可空", 39.9, nullable=True),
    "createdAt": {**TS, "description": "创建时间（毫秒时间戳）"},
    "updatedAt": {**TS, "description": "最后更新时间（毫秒时间戳）", "example": 1726646400000},
}

STREAM = {
    "id": prop("integer", "码流主键 ID", 11, fmt="int64"),
    "deviceId": prop("string", "所属设备编码", "CAM_EAST_01"),
    "streamType": prop("string", "码流类型：main=主码流，sub=子码流", "sub"),
    "channelId": prop("string", "通道 ID（国标等场景），可空", None, nullable=True),
    "streamUrl": prop(
        "string",
        "播放/取流地址（如 HTTP-FLV）",
        "http://8.130.74.232:8080/live/cam01_sub.live.flv",
    ),
    "streamName": prop("string", "码流显示名称，可空", "东门-子码流", nullable=True),
    "status": prop("string", "码流状态，如 ON/OFF", "ON"),
    "sortNo": prop("integer", "排序号，越小越靠前", 2),
    "liveEnabled": prop("boolean", "是否为该设备的业务直播流（同设备仅一条为 true）", True),
    "playCount": prop("integer", "当前播放人数（由 ZLM Hook 维护）", 0),
    "createdAt": {**TS, "description": "创建时间（毫秒时间戳）"},
    "updatedAt": {**TS, "description": "最后更新时间（毫秒时间戳）", "example": 1726646400000},
}

FOLDER_BASE = {
    "id": prop("integer", "目录主键 ID", 1, fmt="int64"),
    "parentId": prop("integer", "父目录 ID；根节点为 null", None, True, "int64"),
    "name": prop("string", "目录名称", "园区"),
    "sortNo": prop("integer", "同级排序号，越小越靠前", 1),
    "path": prop("string", "目录路径，如 /1/ 或 /1/2/", "/1/"),
    "createdAt": {**TS, "description": "创建时间（毫秒时间戳）"},
    "updatedAt": {**TS, "description": "最后更新时间（毫秒时间戳）"},
}

ex_stream = {
    "id": 11,
    "deviceId": "CAM_EAST_01",
    "streamType": "sub",
    "channelId": None,
    "streamUrl": "http://8.130.74.232:8080/live/cam01_sub.live.flv",
    "streamName": "东门-子码流",
    "status": "ON",
    "sortNo": 2,
    "liveEnabled": True,
    "playCount": 0,
    "createdAt": 1726560000000,
    "updatedAt": 1726646400000,
}

ex_device = {
    "id": 1,
    "deviceId": "CAM_EAST_01",
    "name": "东门球机",
    "platformId": None,
    "folderId": 2,
    "status": 1,
    "manufacturer": "宇视",
    "model": "IPC-B",
    "address": "小区东门",
    "ptzType": 1,
    "gatewayId": None,
    "longitude": 116.4,
    "latitude": 39.9,
    "createdAt": 1726560000000,
    "updatedAt": 1726646400000,
    "streamCount": 2,
    "playable": True,
    "livePlayable": True,
}

ex_folder = {
    "id": 1,
    "parentId": None,
    "name": "园区",
    "sortNo": 1,
    "path": "/1/",
    "createdAt": 1726560000000,
    "updatedAt": 1726646400000,
    "deviceCount": 2,
    "totalDeviceCount": 5,
    "children": [
        {
            "id": 2,
            "parentId": 1,
            "name": "东门",
            "sortNo": 1,
            "path": "/1/2/",
            "createdAt": 1726560000000,
            "updatedAt": 1726646400000,
            "deviceCount": 2,
            "totalDeviceCount": 2,
            "children": [],
        }
    ],
}

ex_live = {
    "deviceId": "CAM_EAST_01",
    "streamType": "sub",
    "streamUrl": "http://8.130.74.232:8080/live/cam01_sub.live.flv",
    "playUrl": "http://8.130.74.232:8080/live/cam01_sub.live.flv",
    "liveEnabled": True,
    "ref": 1,
    "countBy": "zlm-hook",
}

ex_recording = {
    "deviceId": "CAM_EAST_01",
    "fileName": "20260910_143000.mp4",
    "recordTime": 1725952200000,
    "endTime": 1725952500000,
    "durationSeconds": 300,
    "size": 12345678,
    "path": "/data/testdata-records/CAM_EAST_01/20260910_143000.mp4",
    "videoUrl": "http://8.130.74.232:8090/api/open/recordings/CAM_EAST_01/20260910_143000.mp4",
}

ex_clip_ok = {
    "deviceId": "CAM_EAST_01",
    "at": 1730000030000,
    "seconds": 30,
    "startTime": 1730000000000,
    "endTime": 1730000060000,
    "durationSeconds": 60,
    "videoUrl": "http://8.130.74.232:8090/api/biz/devices/CAM_EAST_01/clip/file?at=1730000030000&seconds=30",
    "clipUrl": "http://8.130.74.232:8090/api/biz/devices/CAM_EAST_01/clip/file?at=1730000030000&seconds=30",
    "windowStart": 1730000000000,
    "windowEnd": 1730000060000,
    "sourceFiles": ["20260910_143000.mp4"],
    "clipFileName": "CAM_EAST_01_1730000030000_30.mp4",
    "size": 5242880,
    "ok": True,
}

ex_clip_fail = {
    "deviceId": "CAM_WEST_02",
    "at": 1730000100000,
    "seconds": 15,
    "ok": False,
    "error": "该时间点无可用录像",
}

ex_ptz_move = {
    "success": True,
    "mock": True,
    "action": "mock-move",
    "deviceId": "TIC7632_01",
    "direction": "up",
    "speed": 4,
}

PTZ_DEV = "TIC7632_01"

FolderNode = obj(
    {
        **FOLDER_BASE,
        "deviceCount": prop("integer", "本目录直接挂载的设备数量（不含子目录）", 2),
        "totalDeviceCount": prop("integer", "本目录及所有子孙目录下的设备总数", 5),
        "children": {
            "type": "array",
            "description": "子目录列表；叶子节点为空数组 []",
            "items": {"$ref": "#/components/schemas/FolderNode"},
        },
    },
    desc="目录树节点",
)

BizDeviceItem = obj(
    {
        **DEVICE_BASE,
        "streamCount": prop("integer", "该设备已注册码流数量", 2),
        "playable": prop("boolean", "可否回放/截取：status=2（已停用）时为 false，其余为 true", True),
        "livePlayable": prop("boolean", "可否直播：仅 status=1（已启用）时为 true", True),
    },
    desc="业务端设备列表项（不含码流明细）",
)

StreamView = obj(STREAM, desc="码流信息")

BizLiveBrief = obj(
    {
        "id": prop("integer", "业务直播流的码流主键 ID", 11, fmt="int64"),
        "streamType": prop("string", "业务直播使用的码流类型，默认优先 sub", "sub"),
        "streamUrl": prop("string", "业务直播播放地址", "http://8.130.74.232:8080/live/cam01_sub.live.flv"),
        "streamName": prop("string", "码流名称", "东门-子码流"),
        "liveEnabled": prop("boolean", "固定为 true，表示当前即为业务直播流", True),
    },
    desc="业务直播流摘要；设备未配置业务直播流时整个字段为 null",
)

BizDeviceDetail = obj(
    {
        **DEVICE_BASE,
        "streamCount": prop("integer", "码流数量", 2),
        "playable": prop("boolean", "可否回放：status=2 时为 false", True),
        "livePlayable": prop("boolean", "可否直播：仅 status=1 时为 true", True),
        "streams": arr(StreamView, "设备下全部码流"),
        "liveStream": {**BizLiveBrief, "nullable": True, "description": "当前业务直播流配置；未设置时为 null"},
    },
    desc="业务端设备详情",
)

LiveStart = obj(
    {
        "deviceId": prop("string", "设备编码", "CAM_EAST_01"),
        "streamType": prop("string", "实际开播使用的码流类型", "sub"),
        "streamUrl": prop("string", "取流/播放地址", "http://8.130.74.232:8080/live/cam01_sub.live.flv"),
        "playUrl": prop("string", "前端播放地址，与 streamUrl 相同", "http://8.130.74.232:8080/live/cam01_sub.live.flv"),
        "liveEnabled": prop("boolean", "该码流是否标记为业务直播流", True),
        "ref": prop("integer", "当前播放引用人数（ZLM Hook 统计；本接口不加人数）", 1),
        "countBy": prop("string", "人数统计来源，普通流为 zlm-hook；国标点播为 gb28181", "zlm-hook"),
        "streamName": prop("string", "码流名称（国标点播时可能有）", None, nullable=True),
        "channelId": prop("string", "国标通道 ID（国标点播时可能有）", None, nullable=True),
        "transport": prop("string", "国标点播时为 gb28181", None, nullable=True),
        "dataSource": prop("string", "国标点播时为 mock 或 live", None, nullable=True),
        "gb28181": obj({}, desc="国标 INVITE 原始结果（仅国标点播时有）"),
    },
    desc="业务端开播结果",
)

RecordingWithUrl = obj(
    {
        "deviceId": prop("string", "设备编码", "CAM_EAST_01"),
        "fileName": prop("string", "录像文件名，一般为 yyyyMMdd_HHmmss.mp4", "20260910_143000.mp4"),
        "recordTime": {**TS, "description": "录制开始时刻（毫秒，从文件名解析）", "example": 1725952200000},
        "endTime": {
            **TS,
            "description": "录制结束时刻（毫秒，ffprobe 读取 MP4 时长；失败时不返回）",
            "example": 1725952500000,
            "nullable": True,
        },
        "durationSeconds": prop("number", "录像时长（秒，ffprobe 读取；失败时不返回）", 300, nullable=True),
        "size": prop("integer", "文件大小，单位：字节", 12345678, fmt="int64"),
        "path": prop("string", "服务器本地绝对路径（内部用，对外可忽略）", "/data/.../20260910_143000.mp4"),
        "videoUrl": prop(
            "string",
            "可直接播放的绝对地址，指向 /api/open/recordings/{deviceId}/{fileName}，无需 Token",
            "http://8.130.74.232:8090/api/open/recordings/CAM_EAST_01/20260910_143000.mp4",
        ),
    },
    desc="带播放链接的录像项",
)

RecordClipItemRequest = obj(
    {
        "deviceId": prop("string", "【必填】设备业务编码", "CAM_EAST_01"),
        "at": prop(
            "integer",
            "【必填】事件时间点。10 位=秒，11~13 位=毫秒。也可用字符串。别名 timestamp / time / ts",
            1730000030000,
            fmt="int64",
        ),
        "seconds": prop("integer", "时间戳前后各取 N 秒；不传默认 30，须 >0，最大 150", 30),
    },
    required=["deviceId", "at"],
    desc="批量截取录像：单条请求项",
)

ClipBatchItem = obj(
    {
        "ok": prop("boolean", "本条是否成功", True),
        "error": prop("string", "失败原因；ok=false 时有值，如「该时间点无可用录像」", None, nullable=True),
        "deviceId": prop("string", "设备编码；失败时也可能带回请求值", "CAM_EAST_01", nullable=True),
        "at": {**TS, "description": "事件时间点（已规范为毫秒）；能解析则带回", "example": 1730000030000, "nullable": True},
        "seconds": prop("integer", "前后各取的秒数（请求值或默认 30）", 30),
        "windowStart": {**TS, "description": "成功时：请求窗口起点 = at − seconds×1000", "nullable": True},
        "windowEnd": {**TS, "description": "成功时：请求窗口终点 = at + seconds×1000", "nullable": True},
        "startTime": {**TS, "description": "成功时：片段实际开始（受录像覆盖裁切）", "nullable": True},
        "endTime": {**TS, "description": "成功时：片段实际结束", "nullable": True},
        "durationSeconds": prop("number", "成功时：实际时长秒（四舍五入）", 60, nullable=True),
        "videoUrl": prop(
            "string",
            "成功时：可播的 MP4 地址，给播放器直接使用。开启鉴权时 <video> 请在 URL 后加 token",
            "http://8.130.74.232:8090/api/biz/devices/CAM_EAST_01/clip/file?at=1730000030000&seconds=30",
            nullable=True,
        ),
        "clipUrl": prop("string", "成功时：与 videoUrl 相同（兼容字段）", None, nullable=True),
        "sourceFiles": arr(prop("string", "源录像文件名"), "成功时：参与截取的原始 MP4 文件名列表"),
        "clipFileName": prop("string", "成功时：缓存片段文件名", "CAM_EAST_01_1730000030000_30.mp4", nullable=True),
        "size": prop("integer", "成功时：片段文件大小（字节）", 5242880, True, "int64"),
    },
    desc="批量截取结果项（含成功/失败）",
)

PtzResult = obj(
    {
        "success": prop("boolean", "指令是否已受理", True),
        "mock": prop("boolean", "是否模拟模式；live 时为 false 或不出现", True),
        "live": prop("boolean", "是否真实宇视；mock 时不出现", None, nullable=True),
        "action": prop("string", "动作标识，如 mock-move / move / mock-zoom", "mock-move"),
        "deviceId": prop("string", "设备编码", PTZ_DEV),
        "direction": prop("string", "移动：请求方向原文或宇视 PTZCmd；变倍/对焦 live 时为 ZoomTele 等", "up", nullable=True),
        "speed": prop("integer", "速度（请求值）", 4, nullable=True),
        "presetIndex": prop("integer", "预置位编号（预置位接口）", 1, nullable=True),
        "overwrite": prop("boolean", "保存预置位时是否覆盖", False, nullable=True),
        "index": prop("integer", "保存预置位后的编号（与 presetIndex 相同）", 1, nullable=True),
        "name": prop("string", "保存后的预置位名称", "东门全景", nullable=True),
        "zoom": prop("number", "广角 mock 固定 1.0；保存预置位时为中台记录倍率", 1.0, nullable=True),
        "channelType": prop("string", "抓拍通道类型 visible / thermal", "visible", nullable=True),
        "imageUrl": prop("string", "mock 抓拍图片路径", "/api/uniview/mock/snapshot/TIC7632_01.jpg", nullable=True),
        "response": prop("string", "live 时宇视 LAPI 原始 JSON 字符串", None, nullable=True),
    },
    desc="云台控制结果。mock 与 live 字段略有差异，未出现的字段可忽略",
)

schemas = {
    "FolderNode": FolderNode,
    "BizDeviceItem": BizDeviceItem,
    "StreamView": StreamView,
    "BizLiveStreamBrief": BizLiveBrief,
    "BizDeviceDetail": BizDeviceDetail,
    "LiveStartResult": LiveStart,
    "RecordingWithUrl": RecordingWithUrl,
    "RecordClipItemRequest": RecordClipItemRequest,
    "ClipBatchItem": ClipBatchItem,
    "PtzResult": PtzResult,
    "RespFolderTree": resp_schema(arr(ref("FolderNode"), "目录树根节点数组"), [ex_folder]),
    "RespBizDeviceList": resp_schema(arr(ref("BizDeviceItem"), "业务端设备列表"), [ex_device]),
    "RespBizDeviceDetail": resp_schema(
        ref("BizDeviceDetail"),
        {
            **ex_device,
            "streams": [ex_stream],
            "liveStream": {
                "id": 11,
                "streamType": "sub",
                "streamUrl": ex_stream["streamUrl"],
                "streamName": "东门-子码流",
                "liveEnabled": True,
            },
        },
    ),
    "RespLiveStart": resp_schema(ref("LiveStartResult"), ex_live),
    "RespRecordingDays": resp_schema(
        arr(prop("string", "有录像的日期 yyyy-MM-dd", "2026-09-17"), "某月内有录像的日期列表（升序）"),
        ["2026-09-10", "2026-09-15", "2026-09-17"],
    ),
    "RespRecordingUrlList": resp_schema(arr(ref("RecordingWithUrl"), "录像列表（含 videoUrl）"), [ex_recording]),
    "RespClipBatch": resp_schema(arr(ref("ClipBatchItem"), "批量截取结果，顺序与请求一致"), [ex_clip_ok, ex_clip_fail]),
    "RespPtz": resp_schema(ref("PtzResult"), ex_ptz_move),
}

ptz_device = path_p("deviceId", "string", "路径参数：云台设备编码（调用方自行持有）", PTZ_DEV)
speed_q = q("speed", "integer", False, "速度，默认 4；live 限制 1~8", 4, default=4)

doc = {
    "openapi": "3.0.3",
    "info": {
        "title": "视频中台 - 云台监控 / 业务端",
        "description": (
            "仅含云台控制与业务端（含批量片段截取）。\n"
            "统一响应 { code, message, data }；code=0 成功。时间字段为毫秒时间戳。\n"
            "设备 status：0=不可用 1=已启用 2=已停用。直播仅 status=1；回放/截取 status=2 不可用。\n"
            "云台 deviceId 示例 TIC7632_01；业务端 deviceId 示例 CAM_EAST_01，两套不是同一套 ID。\n"
            "鉴权：auth.enabled=false 时免登录；true 时 Header Authorization: Bearer {token}。"
            "批量截取返回的 videoUrl 给 video 标签播时，可在 URL 上加 token。"
        ),
        "version": "1.0.0",
    },
    "servers": [
        {"url": "http://8.130.74.232:8090", "description": "线上环境"},
        {"url": "http://127.0.0.1:8090", "description": "本地环境"},
    ],
    "tags": [
        {
            "name": "云台监控",
            "description": "宇视云台控制。无 Body，参数走 Path + Query。调用方自行持有 deviceId。",
        },
        {
            "name": "业务端",
            "description": "目录树、设备、直播、录像、批量截取片段。",
        },
    ],
    "paths": {
        "/api/uniview/ptz/{deviceId}/move": {
            "post": {
                "tags": ["云台监控"],
                "summary": "云台-方向移动",
                "description": (
                    "连续转动，按一次发一次。无 JSON Body。\n"
                    "direction：up/down/left/right/left_up/left_down/right_up/right_down；"
                    "未知方向在 live 下发 Stop。停止没有独立接口。"
                ),
                "operationId": "ptzMove",
                "parameters": [
                    ptz_device,
                    q(
                        "direction",
                        "string",
                        True,
                        "【必填】方向：up / down / left / right / left_up / left_down / right_up / right_down",
                        "up",
                        enum=["up", "down", "left", "right", "left_up", "left_down", "right_up", "right_down"],
                    ),
                    speed_q,
                ],
                "responses": ok(ref("RespPtz"), ex_ptz_move),
            }
        },
        "/api/uniview/ptz/{deviceId}/zoom": {
            "post": {
                "tags": ["云台监控"],
                "summary": "云台-变倍",
                "description": (
                    "无 JSON Body。推荐 action=zoom_in（拉近）/ zoom_out（拉远）。\n"
                    "live 仅 zoom_in（忽略大小写）→ ZoomTele，其余一律 ZoomWide。"
                ),
                "operationId": "ptzZoom",
                "parameters": [
                    ptz_device,
                    q("action", "string", True, "【必填】变倍动作，推荐 zoom_in / zoom_out", "zoom_in"),
                    speed_q,
                ],
                "responses": ok(
                    ref("RespPtz"),
                    {
                        "success": True,
                        "mock": True,
                        "action": "mock-zoom",
                        "deviceId": PTZ_DEV,
                        "speed": 4,
                    },
                ),
            }
        },
        "/api/uniview/ptz/{deviceId}/focus": {
            "post": {
                "tags": ["云台监控"],
                "summary": "云台-对焦",
                "description": (
                    "无 JSON Body。推荐 action=focus_near（近焦）/ focus_far（远焦）。\n"
                    "live 仅 focus_near → FocusNear，其余一律 FocusFar。"
                ),
                "operationId": "ptzFocus",
                "parameters": [
                    ptz_device,
                    q("action", "string", True, "【必填】对焦动作，推荐 focus_near / focus_far", "focus_near"),
                    speed_q,
                ],
                "responses": ok(
                    ref("RespPtz"),
                    {
                        "success": True,
                        "mock": True,
                        "action": "mock-focus",
                        "deviceId": PTZ_DEV,
                        "speed": 4,
                    },
                ),
            }
        },
        "/api/uniview/ptz/{deviceId}/wide-angle": {
            "post": {
                "tags": ["云台监控"],
                "summary": "云台-一键广角",
                "description": "拉到最广。无 Query。live 以 ZoomWide、速度 4 连续变倍。",
                "operationId": "ptzWideAngle",
                "parameters": [ptz_device],
                "responses": ok(
                    ref("RespPtz"),
                    {
                        "success": True,
                        "mock": True,
                        "action": "mock-wide-angle",
                        "deviceId": PTZ_DEV,
                        "zoom": 1.0,
                    },
                ),
            }
        },
        "/api/uniview/ptz/{deviceId}/preset/{index}/goto": {
            "post": {
                "tags": ["云台监控"],
                "summary": "云台-调用预置位",
                "description": "转到已保存预置位。编号与保存时的 index 一致。无 Body。",
                "operationId": "ptzGotoPreset",
                "parameters": [
                    ptz_device,
                    path_p("index", "integer", "预置位编号，建议 1~1024", 1),
                ],
                "responses": ok(
                    ref("RespPtz"),
                    {
                        "success": True,
                        "mock": True,
                        "action": "mock-goto-preset",
                        "deviceId": PTZ_DEV,
                        "presetIndex": 1,
                    },
                ),
            }
        },
        "/api/uniview/ptz/{deviceId}/preset/{index}": {
            "post": {
                "tags": ["云台监控"],
                "summary": "云台-保存预置位",
                "description": (
                    "把当前姿态存为预置位。name 不能为空。"
                    "已存在且 overwrite=false 时失败：预置位 n 已存在，请勾选覆盖。"
                ),
                "operationId": "ptzSetPreset",
                "parameters": [
                    ptz_device,
                    path_p("index", "integer", "预置位编号", 1),
                    q("name", "string", True, "【必填】预置位名称，不能为空或纯空格", "东门全景"),
                    q("overwrite", "boolean", False, "是否覆盖已有同号预置位，默认 false", False, default=False),
                ],
                "responses": ok(
                    ref("RespPtz"),
                    {
                        "success": True,
                        "mock": True,
                        "action": "mock-set-preset",
                        "deviceId": PTZ_DEV,
                        "presetIndex": 1,
                        "overwrite": False,
                        "index": 1,
                        "name": "东门全景",
                        "zoom": 1.0,
                    },
                ),
            }
        },
        "/api/uniview/ptz/{deviceId}/snapshot": {
            "post": {
                "tags": ["云台监控"],
                "summary": "云台-抓拍",
                "description": "服务端抓拍。mock 返回 imageUrl；live 结果在 response 字符串。channelType 默认 visible。",
                "operationId": "ptzSnapshot",
                "parameters": [
                    ptz_device,
                    q(
                        "channelType",
                        "string",
                        False,
                        "通道类型：visible=可见光（默认），thermal=热成像",
                        "visible",
                        enum=["visible", "thermal"],
                        default="visible",
                    ),
                ],
                "responses": ok(
                    ref("RespPtz"),
                    {
                        "success": True,
                        "mock": True,
                        "action": "mock-snapshot",
                        "deviceId": PTZ_DEV,
                        "channelType": "visible",
                        "imageUrl": "/api/uniview/mock/snapshot/TIC7632_01.jpg",
                    },
                ),
            }
        },
        "/api/biz/folders/tree": {
            "get": {
                "tags": ["业务端"],
                "summary": "业务端-目录树",
                "description": "返回完整设备目录树。deviceCount=本级设备数，totalDeviceCount=含子孙。无 Query。",
                "operationId": "bizFolderTree",
                "responses": ok(ref("RespFolderTree"), [ex_folder]),
            }
        },
        "/api/biz/devices": {
            "get": {
                "tags": ["业务端"],
                "summary": "业务端-设备列表",
                "description": "含已停用设备；用 playable、livePlayable 判断可否回放/直播。不返回码流明细。",
                "operationId": "bizDevices",
                "parameters": [
                    q("folderId", "integer", False, "按目录过滤：目录主键 ID；不传则返回全部设备", 2, "int64"),
                    q(
                        "includeChildren",
                        "boolean",
                        False,
                        "当传入 folderId 时：true=包含子孙目录下设备（默认 true）；false=仅本目录",
                        True,
                        default=True,
                    ),
                ],
                "responses": ok(ref("RespBizDeviceList"), [ex_device]),
            }
        },
        "/api/biz/devices/{deviceId}": {
            "get": {
                "tags": ["业务端"],
                "summary": "业务端-设备详情",
                "description": "含 streams 与 liveStream（业务直播流摘要，未配置为 null）。不存在时 message=设备不存在: {deviceId}。",
                "operationId": "bizDeviceDetail",
                "parameters": [
                    path_p("deviceId", "string", "路径参数：设备业务编码 deviceId", "CAM_EAST_01"),
                ],
                "responses": ok(
                    ref("RespBizDeviceDetail"),
                    {
                        **ex_device,
                        "streams": [ex_stream],
                        "liveStream": {
                            "id": 11,
                            "streamType": "sub",
                            "streamUrl": ex_stream["streamUrl"],
                            "streamName": "东门-子码流",
                            "liveEnabled": True,
                        },
                    },
                ),
            }
        },
        "/api/biz/devices/{deviceId}/live": {
            "post": {
                "tags": ["业务端"],
                "summary": "业务端-开始直播",
                "description": (
                    "仅 status=1 可直播，否则「仅「已启用」设备可直播」。无 Body。"
                    "优先国标 INVITE（配置开启时），否则用已注册 streamUrl。"
                    "前端播 playUrl 或 streamUrl。"
                ),
                "operationId": "bizStartLive",
                "parameters": [
                    path_p("deviceId", "string", "路径参数：设备业务编码", "CAM_EAST_01"),
                ],
                "responses": ok(ref("RespLiveStart"), ex_live),
            }
        },
        "/api/biz/devices/{deviceId}/recording-days": {
            "get": {
                "tags": ["业务端"],
                "summary": "业务端-有录像的日期",
                "description": "返回指定年/月内有录像的日期列表（yyyy-MM-dd，升序），供回放日历。status=2 不可调用。",
                "operationId": "bizRecordingDays",
                "parameters": [
                    path_p("deviceId", "string", "路径参数：设备业务编码", "CAM_EAST_01"),
                    q("year", "integer", True, "【必填】年份 1970~2100", 2026),
                    q("month", "integer", True, "【必填】月份 1~12", 9),
                ],
                "responses": ok(ref("RespRecordingDays"), ["2026-09-10", "2026-09-15", "2026-09-17"]),
            }
        },
        "/api/biz/devices/{deviceId}/recordings": {
            "get": {
                "tags": ["业务端"],
                "summary": "业务端-录像列表",
                "description": (
                    "status=2 不可回放。from/to 为毫秒时间戳（可选），与文件时段有交集即返回，新的在前。"
                    "videoUrl 指向开放接口，无需登录。"
                ),
                "operationId": "bizRecordings",
                "parameters": [
                    path_p("deviceId", "string", "路径参数：设备业务编码", "CAM_EAST_01"),
                    q("from", "integer", False, "开始时间（含），毫秒时间戳；不传则不限起始", 1726502400000, "int64"),
                    q("to", "integer", False, "结束时间（含），毫秒时间戳；不传则不限结束", 1726588799999, "int64"),
                ],
                "responses": ok(ref("RespRecordingUrlList"), [ex_recording]),
            }
        },
        "/api/biz/clips": {
            "post": {
                "tags": ["业务端"],
                "summary": "业务端-批量截取录像片段",
                "description": (
                    "Body 为 JSON 数组（不是 {items:[]}），每项含 deviceId、at、seconds。"
                    "以 at 为中心前后各 seconds 秒（默认 30，最大 150）。只切一条时数组放一项即可。"
                    "单条失败不影响其它（ok=false + error）。整单最多 50 条。"
                    "成功条的 videoUrl 给播放器直接播放。"
                ),
                "operationId": "bizClipsBatch",
                "requestBody": {
                    "required": True,
                    "description": "截取参数数组",
                    "content": {
                        "application/json": {
                            "schema": arr(ref("RecordClipItemRequest"), "批量截取请求"),
                            "example": [
                                {"deviceId": "CAM_EAST_01", "at": 1730000030000, "seconds": 30},
                                {"deviceId": "CAM_WEST_02", "at": 1730000100000, "seconds": 15},
                            ],
                        }
                    },
                },
                "responses": ok(ref("RespClipBatch"), [ex_clip_ok, ex_clip_fail]),
            }
        },
    },
    "components": {
        "schemas": schemas,
        "securitySchemes": {
            "bearerAuth": {
                "type": "http",
                "scheme": "bearer",
                "bearerFormat": "JWT",
                "description": "auth.enabled=true 时需要。登录后填 JWT，不要带 Bearer 前缀（YApi 会加）。",
            }
        },
    },
    "security": [{"bearerAuth": []}],
}

out = Path(__file__).resolve().parent / "yapi-ptz-biz-openapi.json"
out.write_text(json.dumps(doc, ensure_ascii=False, indent=2), encoding="utf-8")
print("Wrote", out)
print("paths", len(doc["paths"]))
