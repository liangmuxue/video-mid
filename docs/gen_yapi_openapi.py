# -*- coding: utf-8 -*-
"""生成带完整字段备注的 OpenAPI 3，供 YApi 导入。"""
import json
from pathlib import Path

# ---------- 通用字段定义（name, type, required?, description, example?, format?） ----------

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


CODE = prop("integer", "业务状态码：0=成功，非0=失败", 0)
MSG = prop("string", "提示信息：成功一般为 success，失败为错误原因", "success")

TS_MILLIS = prop("integer", "毫秒时间戳（Unix epoch millis）", 1726560000000, fmt="int64")

DEVICE_STATUS = prop(
    "integer",
    "设备状态：0=不可用（巡检自动设置，不可手改） 1=已启用（可直播可回放） 2=已停用（灰色可见不可播）",
    1,
    enum=[0, 1, 2],
)

DEVICE_BASE = {
    "id": prop("integer", "设备主键 ID（数据库自增）", 1, fmt="int64"),
    "deviceId": prop("string", "设备业务编码，全局唯一，如 CAM_EAST_01", "CAM_EAST_01"),
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
    "createdAt": {**TS_MILLIS, "description": "创建时间（毫秒时间戳）"},
    "updatedAt": {**TS_MILLIS, "description": "最后更新时间（毫秒时间戳）", "example": 1726646400000},
}

STREAM = {
    "id": prop("integer", "码流主键 ID", 11, fmt="int64"),
    "deviceId": prop("string", "所属设备编码", "CAM_EAST_01"),
    "streamType": prop("string", "码流类型：main=主码流，sub=子码流", "sub"),
    "channelId": prop("string", "通道 ID（国标等场景），可空", None, nullable=True),
    "streamUrl": prop(
        "string",
        "播放/取流地址（如 FLV/RTMP/HLS 等，按注册写入）",
        "http://8.130.74.232:8080/live/cam01_sub.live.flv",
    ),
    "streamName": prop("string", "码流显示名称，可空", "东门-子码流", nullable=True),
    "status": prop("string", "码流状态，如 ON/OFF", "ON"),
    "sortNo": prop("integer", "排序号，越小越靠前", 2),
    "liveEnabled": prop("boolean", "是否为该设备的业务直播流（同设备仅一条为 true）", True),
    "playCount": prop("integer", "当前播放人数（由 ZLM Hook 维护）", 0),
    "createdAt": {**TS_MILLIS, "description": "创建时间（毫秒时间戳）"},
    "updatedAt": {**TS_MILLIS, "description": "最后更新时间（毫秒时间戳）", "example": 1726646400000},
}

RECORDING = {
    "deviceId": prop("string", "设备编码", "CAM_EAST_01"),
    "fileName": prop("string", "录像文件名，一般为 yyyyMMdd_HHmmss.mp4", "20260910_143000.mp4"),
    "recordTime": {**TS_MILLIS, "description": "录制开始时刻（毫秒时间戳，从文件名解析）", "example": 1725952200000},
    "endTime": {**TS_MILLIS, "description": "录制结束时刻（毫秒时间戳，ffprobe 读取 MP4 时长；失败时不返回）", "example": 1725952500000, "nullable": True},
    "durationSeconds": prop("number", "录像时长（秒，ffprobe 读取；失败时不返回）", 300, nullable=True),
    "size": prop("integer", "文件大小，单位：字节", 12345678, fmt="int64"),
    "path": prop("string", "服务器本地绝对路径（内部用，对外可忽略）", "/data/.../20260910_143000.mp4"),
}

FOLDER_BASE = {
    "id": prop("integer", "目录主键 ID", 1, fmt="int64"),
    "parentId": prop("integer", "父目录 ID；根节点为 null", None, True, "int64"),
    "name": prop("string", "目录名称", "园区"),
    "sortNo": prop("integer", "同级排序号，越小越靠前", 1),
    "path": prop("string", "目录路径，如 /1/ 或 /1/2/", "/1/"),
    "createdAt": {**TS_MILLIS, "description": "创建时间（毫秒时间戳）"},
    "updatedAt": {**TS_MILLIS, "description": "最后更新时间（毫秒时间戳）"},
}


def obj(properties, required=None, desc=None):
    s = {"type": "object", "properties": properties}
    if required:
        s["required"] = required
    if desc:
        s["description"] = desc
    return s


def arr(item_schema, desc):
    return {"type": "array", "description": desc, "items": item_schema}


def wrap(data_schema, data_desc, example_data):
    return {
        "type": "object",
        "description": "统一响应包装",
        "properties": {
            "code": CODE,
            "message": MSG,
            "data": {**data_schema, "description": data_desc} if isinstance(data_schema, dict) and "type" in data_schema else data_schema,
        },
        "example": {"code": 0, "message": "success", "data": example_data},
    }


# 为 wrap 修正：data 为 schema 时保留 description
def resp_schema(data_prop, example_data):
    return {
        "type": "object",
        "description": "统一 JSON 响应：code/message/data",
        "properties": {
            "code": CODE,
            "message": MSG,
            "data": data_prop,
        },
        "example": {"code": 0, "message": "success", "data": example_data},
    }


DeviceListItem = obj(
    {**DEVICE_BASE, "streamCount": prop("integer", "该设备已注册码流数量", 2)},
    desc="设备列表项（不含码流明细）",
)

BizDeviceItem = obj(
    {
        **DEVICE_BASE,
        "streamCount": prop("integer", "该设备已注册码流数量", 2),
        "playable": prop("boolean", "业务端可否回放：status=2（已停用）时为 false，其余为 true", True),
        "livePlayable": prop("boolean", "业务端可否直播：仅 status=1（已启用）时为 true", True),
    },
    desc="业务端设备列表项",
)

StreamView = obj(STREAM, desc="码流信息")

DeviceDetail = obj(
    {
        **DEVICE_BASE,
        "streamCount": prop("integer", "码流数量，等于 streams 数组长度", 2),
        "streams": arr(StreamView, "该设备下全部码流列表"),
    },
    desc="设备详情（含码流列表）",
)

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
        "liveStream": {
            **BizLiveBrief,
            "nullable": True,
            "description": "当前业务直播流配置；未设置时为 null",
        },
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
        "ref": prop("integer", "当前播放引用人数（ZLM Hook 统计）", 1),
        "countBy": prop("string", "人数统计来源说明，固定为 zlm-hook", "zlm-hook"),
    },
    desc="开播/预览结果",
)

RecordingItem = obj(RECORDING, desc="录像文件信息（管理端，无外链）")

RecordingWithUrl = obj(
    {
        **RECORDING,
        "videoUrl": prop(
            "string",
            "可直接播放的绝对地址，指向开放接口 /api/open/recordings/{deviceId}/{fileName}，无需 Token",
            "http://8.130.74.232:8090/api/open/recordings/CAM_EAST_01/20260910_143000.mp4",
        ),
    },
    desc="带播放链接的录像项",
)

RecordClipItemRequest = obj(
    {
        "deviceId": prop("string", "【必填】设备业务编码", "CAM_EAST_01"),
        "at": prop("integer", "【必填】事件时间点，毫秒时间戳（也支持 10 位秒级）", 1730000030000, fmt="int64"),
        "seconds": prop(
            "integer",
            "时间戳前后各取 N 秒；不传默认 30，最大 150",
            30,
        ),
    },
    required=["deviceId", "at"],
    desc="批量截取录像：单条请求项",
)

ClipResult = obj(
    {
        "deviceId": prop("string", "设备编码", "CAM_EAST_01"),
        "at": {**TS_MILLIS, "description": "请求中的事件时间点（毫秒）", "example": 1730000030000},
        "seconds": prop("integer", "前后各取的秒数", 30),
        "startTime": {**TS_MILLIS, "description": "片段实际开始时间（毫秒）", "example": 1730000000000},
        "endTime": {**TS_MILLIS, "description": "片段实际结束时间（毫秒）", "example": 1730000060000},
        "durationSeconds": prop("number", "片段时长（秒）", 60),
        "videoUrl": prop(
            "string",
            "MP4 播放地址，指向对应环境的 /clip/file 接口",
            "http://8.130.74.232:8090/api/open/devices/CAM_EAST_01/clip/file?at=1730000030000&seconds=30",
        ),
        "clipUrl": prop("string", "同 videoUrl，兼容字段", None, nullable=True),
        "windowStart": {**TS_MILLIS, "description": "请求窗口起始（at - seconds）", "example": 1730000000000},
        "windowEnd": {**TS_MILLIS, "description": "请求窗口结束（at + seconds）", "example": 1730000060000},
        "sourceFiles": arr(prop("string", "源录像文件名"), "参与截取的原始 MP4 文件名列表"),
        "clipFileName": prop("string", "缓存片段文件名", "CAM_EAST_01_1730000030000_30.mp4"),
        "size": prop("integer", "片段文件大小（字节）", 5242880, fmt="int64"),
    },
    desc="录像片段截取结果（单条成功）",
)

ClipBatchItem = obj(
    {
        "deviceId": prop("string", "设备编码", "CAM_EAST_01"),
        "at": {**TS_MILLIS, "description": "事件时间点（毫秒）", "example": 1730000030000},
        "seconds": prop("integer", "前后各取的秒数", 30),
        "ok": prop("boolean", "本条是否成功", True),
        "error": prop("string", "失败原因；ok=false 时有值", None, nullable=True),
        "startTime": {**TS_MILLIS, "description": "成功时：片段开始时间", "nullable": True},
        "endTime": {**TS_MILLIS, "description": "成功时：片段结束时间", "nullable": True},
        "durationSeconds": prop("number", "成功时：片段时长（秒）", 60, nullable=True),
        "videoUrl": prop("string", "成功时：MP4 播放地址", None, nullable=True),
        "sourceFiles": arr(prop("string", "源录像文件名"), "成功时：源文件列表"),
    },
    desc="批量截取结果项（含成功/失败）",
)

GenericMap = obj({}, desc="键值对象，字段随 mock/live 模式变化")

FolderBase = obj(FOLDER_BASE, desc="目录基本信息")

# FolderNode 自引用：先占位再填
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

DeviceRequest = obj(
    {
        "deviceId": prop("string", "【必填】设备业务编码，创建后一般不改", "CAM_EAST_01"),
        "name": prop("string", "设备名称", "东门球机"),
        "platformId": prop("string", "上级/平台 ID，可空", None, nullable=True),
        "folderId": prop("integer", "所属目录 ID，可空", 2, True, "int64"),
        "status": prop("integer", "人工可写状态：1=已启用 2=已停用（0 不可用仅巡检写入）", 1, enum=[1, 2]),
        "manufacturer": prop("string", "厂商", "宇视"),
        "model": prop("string", "型号", "IPC-B"),
        "address": prop("string", "安装地址", "小区东门"),
        "ptzType": prop("integer", "云台类型", 1),
        "gatewayId": prop("string", "网关 ID，可空", None, nullable=True),
        "longitude": prop("number", "经度，可空", None, nullable=True),
        "latitude": prop("number", "纬度，可空", None, nullable=True),
    },
    required=["deviceId"],
    desc="创建设备/更新设备请求体",
)

DeviceFolderRequest = obj(
    {
        "parentId": prop("integer", "父目录 ID；不传或 null 表示挂在根下", 1, True, "int64"),
        "name": prop("string", "目录名称", "东门"),
        "sortNo": prop("integer", "同级排序号", 1),
    },
    desc="新建/更新目录请求体",
)

StreamRegisterRequest = obj(
    {
        "deviceId": prop("string", "【必填】设备编码；不存在时可顺带创建设备", "CAM_EAST_01"),
        "streamType": prop("string", "【必填】码流类型，如 main / sub", "sub"),
        "streamUrl": prop("string", "【必填】播放/取流 URL", "http://8.130.74.232:8080/live/cam01_sub.live.flv"),
        "streamName": prop("string", "码流名称，可空", "东门-子码流"),
        "channelId": prop("string", "通道 ID，可空", None, nullable=True),
        "status": prop("string", "码流状态，如 ON", "ON"),
        "sortNo": prop("integer", "排序号", 2),
        "liveEnabled": prop("boolean", "是否设为业务直播流；true 时同设备其他码流会取消直播标记", True),
        "deviceName": prop("string", "注册时顺带写入/更新的设备名称，可空", "东门球机"),
    },
    required=["deviceId", "streamType", "streamUrl"],
    desc="码流注册请求体（本接口无需登录）",
)

PreviewRequest = obj(
    {
        "deviceId": prop("string", "【必填】设备编码", "CAM_EAST_01"),
        "streamType": prop("string", "码流类型；不传默认 sub（子码流）", "sub"),
    },
    required=["deviceId"],
    desc="开始预览请求体",
)

# ---------- 示例 data ----------
EX_TS = 1726560000000
EX_TS2 = 1726646400000
EX_REC_TIME = 1725952200000

ex_folder_node = {
    "id": 1,
    "parentId": None,
    "name": "园区",
    "sortNo": 1,
    "path": "/1/",
    "deviceCount": 2,
    "totalDeviceCount": 5,
    "createdAt": EX_TS,
    "updatedAt": EX_TS,
    "children": [
        {
            "id": 2,
            "parentId": 1,
            "name": "东门",
            "sortNo": 1,
            "path": "/1/2/",
            "deviceCount": 3,
            "totalDeviceCount": 3,
            "createdAt": EX_TS,
            "updatedAt": EX_TS,
            "children": [],
        }
    ],
}

ex_device_list = {
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
    "streamCount": 2,
    "createdAt": EX_TS,
    "updatedAt": EX_TS2,
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
    "playCount": 1,
    "createdAt": EX_TS,
    "updatedAt": EX_TS2,
}

ex_recording_url = {
    "deviceId": "CAM_EAST_01",
    "fileName": "20260910_143000.mp4",
    "recordTime": EX_REC_TIME,
    "endTime": EX_REC_TIME + 300000,
    "durationSeconds": 300,
    "size": 12345678,
    "path": "/data/testdata-records/CAM_EAST_01/20260910_143000.mp4",
    "videoUrl": "http://8.130.74.232:8090/api/open/recordings/CAM_EAST_01/20260910_143000.mp4",
}

ex_recording_days = ["2026-09-10", "2026-09-15", "2026-09-17"]

ex_clip = {
    "deviceId": "CAM_EAST_01",
    "at": 1730000030000,
    "seconds": 30,
    "startTime": 1730000000000,
    "endTime": 1730000060000,
    "durationSeconds": 60,
    "videoUrl": "http://8.130.74.232:8090/api/open/devices/CAM_EAST_01/clip/file?at=1730000030000&seconds=30",
    "clipUrl": "http://8.130.74.232:8090/api/open/devices/CAM_EAST_01/clip/file?at=1730000030000&seconds=30",
    "windowStart": 1730000000000,
    "windowEnd": 1730000060000,
    "sourceFiles": ["20260910_143000.mp4"],
    "clipFileName": "CAM_EAST_01_1730000030000_30.mp4",
    "size": 5242880,
}

ex_clip_batch = [
    {**ex_clip, "ok": True},
    {
        "deviceId": "CAM_WEST_02",
        "at": 1730000100000,
        "seconds": 15,
        "ok": False,
        "error": "该时间点无可用录像",
    },
]

ex_live = {
    "deviceId": "CAM_EAST_01",
    "streamType": "sub",
    "streamUrl": "http://8.130.74.232:8080/live/cam01_sub.live.flv",
    "playUrl": "http://8.130.74.232:8080/live/cam01_sub.live.flv",
    "liveEnabled": True,
    "ref": 1,
    "countBy": "zlm-hook",
}


def q(name, typ, required, desc, example=None, fmt=None):
    p = {
        "name": name,
        "in": "query",
        "required": required,
        "description": desc,
        "schema": {"type": typ, "description": desc},
    }
    if example is not None:
        p["example"] = example
        p["schema"]["example"] = example
    if fmt:
        p["schema"]["format"] = fmt
    return p


def path_p(name, typ, desc, example=None, fmt=None):
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
    if fmt:
        p["schema"]["format"] = fmt
    return p


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


def ref(name):
    return {"$ref": f"#/components/schemas/{name}"}


# 响应包装 schema 名称
schemas = {
    "FolderNode": FolderNode,
    "DeviceListItem": DeviceListItem,
    "BizDeviceItem": BizDeviceItem,
    "StreamView": StreamView,
    "DeviceDetail": DeviceDetail,
    "BizLiveStreamBrief": BizLiveBrief,
    "BizDeviceDetail": BizDeviceDetail,
    "LiveStartResult": LiveStart,
    "RecordingItem": RecordingItem,
    "RecordingWithUrl": RecordingWithUrl,
    "RecordClipItemRequest": RecordClipItemRequest,
    "ClipResult": ClipResult,
    "ClipBatchItem": ClipBatchItem,
    "GenericMap": GenericMap,
    "FolderBase": FolderBase,
    "DeviceRequest": DeviceRequest,
    "DeviceFolderRequest": DeviceFolderRequest,
    "StreamRegisterRequest": StreamRegisterRequest,
    "PreviewRequest": PreviewRequest,
    "RespFolderTree": resp_schema(arr(ref("FolderNode"), "目录树根节点数组"), [ex_folder_node]),
    "RespBizDeviceList": resp_schema(
        arr(ref("BizDeviceItem"), "业务端设备列表"),
        [{**ex_device_list, "playable": True, "livePlayable": True}],
    ),
    "RespBizDeviceDetail": resp_schema(
        ref("BizDeviceDetail"),
        {
            **ex_device_list,
            "playable": True,
            "livePlayable": True,
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
    "RespRecordingUrlList": resp_schema(arr(ref("RecordingWithUrl"), "录像列表（含 videoUrl）"), [ex_recording_url]),
    "RespFolderBase": resp_schema(
        ref("FolderBase"),
        {
            "id": 2,
            "parentId": 1,
            "name": "东门",
            "sortNo": 1,
            "path": "/1/2/",
            "createdAt": EX_TS,
            "updatedAt": EX_TS,
        },
    ),
    "RespVoid": resp_schema(
        {"nullable": True, "description": "无业务数据，固定为 null", "example": None},
        None,
    ),
    "RespDeviceList": resp_schema(arr(ref("DeviceListItem"), "设备列表"), [ex_device_list]),
    "RespDeviceDetail": resp_schema(
        ref("DeviceDetail"),
        {**ex_device_list, "streams": [ex_stream]},
    ),
    "RespStream": resp_schema(ref("StreamView"), ex_stream),
    "RespStreamList": resp_schema(arr(ref("StreamView"), "码流列表"), [ex_stream]),
    "RespRecordingList": resp_schema(
        arr(ref("RecordingItem"), "录像列表（无 videoUrl）"),
        [{k: v for k, v in ex_recording_url.items() if k != "videoUrl"}],
    ),
    "LoginRequest": obj(
        {
            "username": prop("string", "【必填】登录用户名", "admin"),
            "password": prop("string", "【必填】登录密码", "admin123"),
        },
        required=["username", "password"],
        desc="登录请求体",
    ),
    "UserView": obj(
        {
            "id": prop("integer", "用户主键 ID", 1, fmt="int64"),
            "username": prop("string", "登录用户名", "admin"),
            "nickname": prop("string", "昵称/显示名，可空", "管理员", nullable=True),
            "status": prop("integer", "账号状态：1=启用 0=禁用", 1, enum=[0, 1]),
            "lastLoginAt": {
                **TS_MILLIS,
                "description": "最近一次登录时间（毫秒时间戳）；从未登录为 null",
                "nullable": True,
            },
        },
        desc="当前用户信息",
    ),
    "LoginResult": obj(
        {
            "token": prop("string", "会话 JWT，后续请求放 Authorization: Bearer {token}", "eyJhbGciOi..."),
            "tokenType": prop("string", "令牌类型，固定 Bearer", "Bearer"),
            "expiresIn": prop("integer", "有效期秒数", 86400),
            "user": {
                "description": "登录用户基本信息",
                "allOf": [ref("UserView")],
            },
        },
        desc="登录成功返回 data",
    ),
    "RespLogin": resp_schema(
        ref("LoginResult"),
        {
            "token": "eyJhbGciOiJIUzI1NiJ9.example",
            "tokenType": "Bearer",
            "expiresIn": 86400,
            "user": {
                "id": 1,
                "username": "admin",
                "nickname": "管理员",
                "status": 1,
                "lastLoginAt": EX_TS2,
            },
        },
    ),
    "RespUser": resp_schema(
        ref("UserView"),
        {
            "id": 1,
            "username": "admin",
            "nickname": "管理员",
            "status": 1,
            "lastLoginAt": EX_TS2,
        },
    ),
    "RespRecordingDays": resp_schema(
        arr(prop("string", "有录像的日期 yyyy-MM-dd", "2026-09-17"), "某月内有录像的日期列表（升序）"),
        ex_recording_days,
    ),
    "RespClip": resp_schema(ref("ClipResult"), ex_clip),
    "RespClipBatch": resp_schema(arr(ref("ClipBatchItem"), "批量截取结果"), ex_clip_batch),
    "RespGenericMap": resp_schema(ref("GenericMap"), {"success": True, "mock": True}),
    "RespGenericMapList": resp_schema(arr(ref("GenericMap"), "对象列表"), [{"deviceId": "TIC7632_01"}]),
    "HealthDeps": obj(
        {
            "mysql": prop("string", "MySQL 连通性：UP 或 DOWN: 错误信息", "UP"),
            "redis": prop("string", "Redis 连通性：UP 或 DOWN: 错误信息", "UP"),
            "zlm": prop("string", "ZLMediaKit 连通性：UP 或 DOWN", "UP"),
            "status": prop("string", "汇总状态：三者都 UP 则为 UP，否则 DOWN", "UP"),
        },
        desc="依赖健康检查结果（非 ApiResponse 包装，直接返回对象）",
    ),
    "ZlmHookCommonBody": obj(
        {
            "app": prop("string", "ZLM 应用名 app，与码流注册 URL 中的 app 段对应", "live"),
            "stream": prop("string", "ZLM 流 ID stream，与码流注册 URL 中的流名对应", "cam01_sub"),
            "schema": prop("string", "协议，如 rtsp/rtmp/http-flv 等，可空", "http"),
            "id": prop("string", "播放器/会话 ID，用于去重计数，可空", "player-uuid-1"),
            "ip": prop("string", "客户端 IP，可空", "1.2.3.4"),
            "player": prop("boolean", "是否播放端（on_flow_report 使用）：true=播放器断开", True),
        },
        desc="ZLM Hook 回调常见字段（ZLM 还会附带其它字段，服务端按需读取）",
    ),
    "ZlmHookOk": obj(
        {
            "code": prop("integer", "ZLM 约定：0 表示成功", 0),
            "msg": prop("string", "提示信息（注意字段名为 msg，不是 message）", "success"),
            "close": prop(
                "boolean",
                "仅 on_stream_none_reader 返回：是否关闭流；本服务固定 false（不主动关流）",
                False,
            ),
        },
        desc="返回给 ZLM 的 Hook 响应",
    ),
}

doc = {
    "openapi": "3.0.3",
    "info": {
        "title": "极知视频中台 API",
        "description": (
            "分类：业务端 / 设备管理 / 录像回放 / 录像片段截取 / 直播 / 对外开放 / GB28181 / 宇视 PTZ / 系统鉴权 / 运维 / ZLM Hook。\n"
            "业务接口统一响应 { code, message, data }；code=0 成功。\n"
            "时间字段统一为毫秒时间戳（int64）；设备 status：0=不可用 1=已启用 2=已停用。\n"
            "业务直播默认使用子码流（sub）。\n"
            "鉴权：配置 auth.enabled=false（当前测试环境）时，全部 /api/** 免登录；"
            "auth.enabled=true 时需 Authorization: Bearer {token}，"
            "始终免鉴权：/api/open/**、POST /api/streams/register、POST /api/auth/login、"
            "/api/gb28181/catalog.xml、/health/**、/index/hook/**。\n"
            "录像片段：按 deviceId + at（毫秒）+ seconds（前后各 N 秒）截取 MP4；"
            "支持单条 GET /clip 与批量 POST /clips。"
        ),
        "version": "1.5.0",
    },
    "servers": [
        {"url": "http://8.130.74.232:8090", "description": "线上环境"},
        {"url": "http://127.0.0.1:8090", "description": "本地环境"},
    ],
    "tags": [
        {"name": "业务端", "description": "业务门户接口，需登录"},
        {"name": "设备管理", "description": "设备/目录/码流管理；码流注册无需登录"},
        {"name": "录像回放", "description": "管理端与业务端录像查询、整段 MP4 播放"},
        {"name": "录像片段截取", "description": "按时间点 ± seconds 秒截取 MP4，含批量接口"},
        {"name": "直播", "description": "预览开播、业务直播"},
        {"name": "对外开放（无鉴权）", "description": "/api/open/**，第三方免登录调用"},
        {"name": "GB28181", "description": "国标 mock/live：Catalog、SIP、INVITE 点播"},
        {"name": "宇视 PTZ", "description": "宇视 mock/live：设备、云台、预置位、抓拍"},
        {"name": "系统/鉴权", "description": "登录、登出、当前用户"},
        {"name": "运维/健康", "description": "依赖连通性检查"},
        {"name": "内部回调（ZLM Hook）", "description": "仅供 ZLMediaKit 回调，非前端业务接口"},
    ],
    "paths": {
        "/api/auth/login": {
            "post": {
                "tags": ["系统/鉴权"],
                "summary": "登录",
                "description": "无需鉴权。成功返回 JWT，后续请求 Header：Authorization: Bearer {token}。默认账号见部署说明（如 admin/admin123）。",
                "operationId": "authLogin",
                "security": [],
                "requestBody": {
                    "required": True,
                    "description": "用户名与密码",
                    "content": {
                        "application/json": {
                            "schema": ref("LoginRequest"),
                            "example": {"username": "admin", "password": "admin123"},
                        }
                    },
                },
                "responses": ok(
                    ref("RespLogin"),
                    {
                        "token": "eyJhbGciOiJIUzI1NiJ9.example",
                        "tokenType": "Bearer",
                        "expiresIn": 86400,
                        "user": {
                            "id": 1,
                            "username": "admin",
                            "nickname": "管理员",
                            "status": 1,
                            "lastLoginAt": EX_TS2,
                        },
                    },
                ),
            }
        },
        "/api/auth/logout": {
            "post": {
                "tags": ["系统/鉴权"],
                "summary": "登出",
                "description": "需登录。清除 Redis 中的会话缓存；无 Body。",
                "operationId": "authLogout",
                "responses": ok(ref("RespVoid"), None),
            }
        },
        "/api/auth/me": {
            "get": {
                "tags": ["系统/鉴权"],
                "summary": "当前登录用户",
                "description": "需登录。根据 Token 返回当前用户信息。",
                "operationId": "authMe",
                "responses": ok(
                    ref("RespUser"),
                    {
                        "id": 1,
                        "username": "admin",
                        "nickname": "管理员",
                        "status": 1,
                        "lastLoginAt": EX_TS2,
                    },
                ),
            }
        },
        "/health/deps": {
            "get": {
                "tags": ["运维/健康"],
                "summary": "依赖连通性检查",
                "description": "无需鉴权。直接返回对象（不是 {code,message,data} 包装），用于运维探活。",
                "operationId": "healthDeps",
                "security": [],
                "responses": {
                    "200": {
                        "description": "探活结果",
                        "content": {
                            "application/json": {
                                "schema": ref("HealthDeps"),
                                "example": {
                                    "mysql": "UP",
                                    "redis": "UP",
                                    "zlm": "UP",
                                    "status": "UP",
                                },
                            }
                        },
                    }
                },
            }
        },
        "/index/hook/on_play": {
            "post": {
                "tags": ["内部回调（ZLM Hook）"],
                "summary": "ZLM-有播放器开始拉流",
                "description": "ZLM on_play 回调。服务端对齐/增加 Redis 播放人数。无需鉴权。仅 ZLM 调用。",
                "operationId": "zlmOnPlay",
                "security": [],
                "requestBody": {
                    "required": True,
                    "description": "ZLM 推送的 Hook 参数",
                    "content": {
                        "application/json": {
                            "schema": ref("ZlmHookCommonBody"),
                            "example": {
                                "app": "live",
                                "stream": "cam01_sub",
                                "schema": "http",
                                "id": "player-1",
                                "ip": "1.2.3.4",
                            },
                        }
                    },
                },
                "responses": {
                    "200": {
                        "description": "告知 ZLM 处理成功",
                        "content": {
                            "application/json": {
                                "schema": ref("ZlmHookOk"),
                                "example": {"code": 0, "msg": "success"},
                            }
                        },
                    }
                },
            }
        },
        "/index/hook/on_flow_report": {
            "post": {
                "tags": ["内部回调（ZLM Hook）"],
                "summary": "ZLM-播放/推流断开上报",
                "description": "ZLM on_flow_report。播放器断开时回写 Redis 真实人数。无需鉴权。",
                "operationId": "zlmOnFlowReport",
                "security": [],
                "requestBody": {
                    "required": True,
                    "description": "含 player 标记：true 表示播放端",
                    "content": {
                        "application/json": {
                            "schema": ref("ZlmHookCommonBody"),
                            "example": {
                                "player": True,
                                "app": "live",
                                "stream": "cam01_sub",
                                "schema": "http",
                                "id": "player-1",
                            },
                        }
                    },
                },
                "responses": {
                    "200": {
                        "description": "告知 ZLM 处理成功",
                        "content": {
                            "application/json": {
                                "schema": ref("ZlmHookOk"),
                                "example": {"code": 0, "msg": "success"},
                            }
                        },
                    }
                },
            }
        },
        "/index/hook/on_stream_none_reader": {
            "post": {
                "tags": ["内部回调（ZLM Hook）"],
                "summary": "ZLM-流已无人观看",
                "description": "ZLM on_stream_none_reader。Redis 播放人数置 0；响应 close=false 不主动关流。无需鉴权。",
                "operationId": "zlmOnNoneReader",
                "security": [],
                "requestBody": {
                    "required": True,
                    "description": "至少含 app、stream",
                    "content": {
                        "application/json": {
                            "schema": ref("ZlmHookCommonBody"),
                            "example": {"app": "live", "stream": "cam01_sub"},
                        }
                    },
                },
                "responses": {
                    "200": {
                        "description": "返回 close=false，保留流",
                        "content": {
                            "application/json": {
                                "schema": ref("ZlmHookOk"),
                                "example": {"code": 0, "msg": "success", "close": False},
                            }
                        },
                    }
                },
            }
        },
        "/api/biz/folders/tree": {
            "get": {
                "tags": ["业务端"],
                "summary": "业务端-目录树",
                "description": "返回完整设备目录树，含本级/子孙设备数量。无 Query 参数。",
                "operationId": "bizFolderTree",
                "responses": ok(ref("RespFolderTree"), [ex_folder_node]),
            }
        },
        "/api/biz/devices": {
            "get": {
                "tags": ["业务端"],
                "summary": "业务端-设备列表",
                "description": "含已停用设备；返回 playable、livePlayable。",
                "operationId": "bizDevices",
                "parameters": [
                    q("folderId", "integer", False, "按目录过滤：目录主键 ID；不传则返回全部设备", 2, "int64"),
                    q(
                        "includeChildren",
                        "boolean",
                        False,
                        "当传入 folderId 时：true=包含子孙目录下设备（默认 true）；false=仅本目录",
                        True,
                    ),
                ],
                "responses": ok(
                    ref("RespBizDeviceList"),
                    [{**ex_device_list, "playable": True, "livePlayable": True}],
                ),
            }
        },
        "/api/biz/devices/{deviceId}": {
            "get": {
                "tags": ["业务端"],
                "summary": "业务端-设备详情",
                "description": "含 streams 与 liveStream（业务直播流摘要）。",
                "operationId": "bizDeviceDetail",
                "parameters": [
                    path_p("deviceId", "string", "路径参数：设备业务编码 deviceId", "CAM_EAST_01"),
                ],
                "responses": ok(
                    ref("RespBizDeviceDetail"),
                    {
                        **ex_device_list,
                        "playable": True,
                        "livePlayable": True,
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
                "tags": ["直播", "业务端"],
                "summary": "业务端-开始直播",
                "description": "仅 status=1（已启用）可直播；使用该设备 liveEnabled 的码流（默认 sub）。无 Body。",
                "operationId": "bizStartLive",
                "parameters": [
                    path_p("deviceId", "string", "路径参数：设备业务编码", "CAM_EAST_01"),
                ],
                "responses": ok(ref("RespLiveStart"), ex_live),
            }
        },
        "/api/biz/devices/{deviceId}/recording-days": {
            "get": {
                "tags": ["录像回放", "业务端"],
                "summary": "业务端-有录像的日期",
                "description": "返回指定年/月内有录像的日期列表（yyyy-MM-dd），供回放日历展示。status=2 不可调用。",
                "operationId": "bizRecordingDays",
                "parameters": [
                    path_p("deviceId", "string", "路径参数：设备业务编码", "CAM_EAST_01"),
                    q("year", "integer", True, "【必填】年份，如 2026", 2026),
                    q("month", "integer", True, "【必填】月份 1~12", 9),
                ],
                "responses": ok(ref("RespRecordingDays"), ex_recording_days),
            }
        },
        "/api/biz/devices/{deviceId}/recordings": {
            "get": {
                "tags": ["录像回放", "业务端"],
                "summary": "业务端-录像列表",
                "description": "status=2（已停用）不可回放。from/to 为毫秒时间戳（可选）。",
                "operationId": "bizRecordings",
                "parameters": [
                    path_p("deviceId", "string", "路径参数：设备业务编码", "CAM_EAST_01"),
                    q("from", "integer", False, "开始时间（含），毫秒时间戳；不传则不限起始", 1726502400000, "int64"),
                    q("to", "integer", False, "结束时间（含），毫秒时间戳；不传则不限结束", 1726588799999, "int64"),
                ],
                "responses": ok(ref("RespRecordingUrlList"), [ex_recording_url]),
            }
        },
        "/api/biz/clips": {
            "post": {
                "tags": ["录像片段截取", "业务端"],
                "summary": "业务端-批量截取录像片段",
                "description": "Body 为 JSON 数组，每项含 deviceId、at（毫秒）、seconds（前后各 N 秒，默认 30）。"
                "单条失败不影响其它条目（ok=false + error）。单次最多 50 条。status=2 不可回放。",
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
                "responses": ok(ref("RespClipBatch"), ex_clip_batch),
            }
        },
        "/api/biz/devices/{deviceId}/clip": {
            "get": {
                "tags": ["录像片段截取", "业务端"],
                "summary": "业务端-单条截取（元数据）",
                "description": "返回 videoUrl、startTime、endTime；MP4 通过 videoUrl 播放。",
                "operationId": "bizClip",
                "parameters": [
                    path_p("deviceId", "string", "设备业务编码", "CAM_EAST_01"),
                    q("at", "integer", True, "【必填】事件时间点，毫秒时间戳", 1730000030000, "int64"),
                    q("seconds", "integer", False, "前后各取秒数，默认 30，最大 150", 30),
                ],
                "responses": ok(ref("RespClip"), ex_clip),
            }
        },
        "/api/biz/devices/{deviceId}/clip/file": {
            "get": {
                "tags": ["录像片段截取", "业务端"],
                "summary": "业务端-片段 MP4 直链",
                "description": "返回 video/mp4 二进制流，供 videoUrl 播放。",
                "operationId": "bizClipFile",
                "parameters": [
                    path_p("deviceId", "string", "设备业务编码", "CAM_EAST_01"),
                    q("at", "integer", True, "【必填】毫秒时间戳", 1730000030000, "int64"),
                    q("seconds", "integer", False, "前后各取秒数，默认 30", 30),
                ],
                "responses": {
                    "200": {
                        "description": "MP4 文件流",
                        "content": {
                            "video/mp4": {
                                "schema": {"type": "string", "format": "binary", "description": "片段 MP4"}
                            }
                        },
                    }
                },
            }
        },
        "/api/device-folders/tree": {
            "get": {
                "tags": ["设备管理"],
                "summary": "设备目录树",
                "description": "管理端目录树。无 Query 参数。",
                "operationId": "folderTree",
                "responses": ok(ref("RespFolderTree"), [ex_folder_node]),
            }
        },
        "/api/device-folders": {
            "post": {
                "tags": ["设备管理"],
                "summary": "新建设备目录",
                "operationId": "folderCreate",
                "requestBody": {
                    "required": True,
                    "description": "目录创建参数",
                    "content": {
                        "application/json": {
                            "schema": ref("DeviceFolderRequest"),
                            "example": {"parentId": 1, "name": "东门", "sortNo": 1},
                        }
                    },
                },
                "responses": ok(
                    ref("RespFolderBase"),
                    {
                        "id": 2,
                        "parentId": 1,
                        "name": "东门",
                        "sortNo": 1,
                        "path": "/1/2/",
                        "createdAt": EX_TS,
                        "updatedAt": EX_TS,
                    },
                ),
            }
        },
        "/api/device-folders/{id}": {
            "put": {
                "tags": ["设备管理"],
                "summary": "更新设备目录",
                "operationId": "folderUpdate",
                "parameters": [
                    path_p("id", "integer", "路径参数：目录主键 ID", 2, "int64"),
                ],
                "requestBody": {
                    "required": True,
                    "description": "待更新字段",
                    "content": {
                        "application/json": {
                            "schema": ref("DeviceFolderRequest"),
                            "example": {"name": "东门区域", "sortNo": 2},
                        }
                    },
                },
                "responses": ok(
                    ref("RespFolderBase"),
                    {
                        "id": 2,
                        "parentId": 1,
                        "name": "东门区域",
                        "sortNo": 2,
                        "path": "/1/2/",
                        "createdAt": EX_TS,
                        "updatedAt": EX_TS2,
                    },
                ),
            },
            "delete": {
                "tags": ["设备管理"],
                "summary": "删除设备目录",
                "description": "有子目录或仍挂设备时可能失败，以 message 为准。",
                "operationId": "folderDelete",
                "parameters": [
                    path_p("id", "integer", "路径参数：目录主键 ID", 2, "int64"),
                ],
                "responses": ok(ref("RespVoid"), None),
            },
        },
        "/api/devices": {
            "get": {
                "tags": ["设备管理"],
                "summary": "设备列表",
                "operationId": "deviceList",
                "parameters": [
                    q("folderId", "integer", False, "目录主键 ID；不传返回全部", 2, "int64"),
                    q(
                        "includeChildren",
                        "boolean",
                        False,
                        "是否包含子孙目录设备，默认 true",
                        True,
                    ),
                ],
                "responses": ok(ref("RespDeviceList"), [ex_device_list]),
            },
            "post": {
                "tags": ["设备管理"],
                "summary": "创建设备",
                "operationId": "deviceCreate",
                "requestBody": {
                    "required": True,
                    "description": "设备字段，deviceId 必填",
                    "content": {
                        "application/json": {
                            "schema": ref("DeviceRequest"),
                            "example": {
                                "deviceId": "CAM_EAST_01",
                                "name": "东门球机",
                                "folderId": 2,
                                "status": 1,
                                "manufacturer": "宇视",
                                "model": "IPC-B",
                                "address": "小区东门",
                            },
                        }
                    },
                },
                "responses": ok(
                    ref("RespDeviceDetail"),
                    {**ex_device_list, "streamCount": 0, "streams": []},
                ),
            },
        },
        "/api/devices/by-device-id/{deviceId}": {
            "get": {
                "tags": ["设备管理"],
                "summary": "按 deviceId 查设备详情",
                "operationId": "deviceByDeviceId",
                "parameters": [
                    path_p("deviceId", "string", "路径参数：设备业务编码", "CAM_EAST_01"),
                ],
                "responses": ok(
                    ref("RespDeviceDetail"),
                    {**ex_device_list, "streams": [ex_stream]},
                ),
            }
        },
        "/api/devices/{id}": {
            "get": {
                "tags": ["设备管理"],
                "summary": "设备详情（主键 id）",
                "operationId": "deviceDetail",
                "parameters": [
                    path_p("id", "integer", "路径参数：设备主键 ID（非 deviceId）", 1, "int64"),
                ],
                "responses": ok(
                    ref("RespDeviceDetail"),
                    {**ex_device_list, "streams": [ex_stream]},
                ),
            },
            "put": {
                "tags": ["设备管理"],
                "summary": "更新设备",
                "operationId": "deviceUpdate",
                "parameters": [
                    path_p("id", "integer", "路径参数：设备主键 ID", 1, "int64"),
                ],
                "requestBody": {
                    "required": True,
                    "description": "完整或部分设备字段（以服务端实现为准，建议带齐常用字段）",
                    "content": {
                        "application/json": {
                            "schema": ref("DeviceRequest"),
                            "example": {
                                "deviceId": "CAM_EAST_01",
                                "name": "东门球机-改",
                                "folderId": 2,
                                "status": 2,
                            },
                        }
                    },
                },
                "responses": ok(
                    ref("RespDeviceDetail"),
                    {**ex_device_list, "name": "东门球机-改", "status": 2, "streams": [ex_stream]},
                ),
            },
            "delete": {
                "tags": ["设备管理"],
                "summary": "删除设备",
                "operationId": "deviceDelete",
                "parameters": [
                    path_p("id", "integer", "路径参数：设备主键 ID", 1, "int64"),
                ],
                "responses": ok(ref("RespVoid"), None),
            },
        },
        "/api/streams/register": {
            "post": {
                "tags": ["设备管理"],
                "summary": "码流注册（无鉴权）",
                "description": "写入/更新 stream_url；拦截器已放行，无需 Token。",
                "operationId": "streamRegister",
                "security": [],
                "requestBody": {
                    "required": True,
                    "description": "码流注册参数，deviceId/streamType/streamUrl 必填",
                    "content": {
                        "application/json": {
                            "schema": ref("StreamRegisterRequest"),
                            "example": {
                                "deviceId": "CAM_EAST_01",
                                "streamType": "sub",
                                "streamUrl": "http://8.130.74.232:8080/live/cam01_sub.live.flv",
                                "streamName": "东门-子码流",
                                "deviceName": "东门球机",
                                "liveEnabled": True,
                            },
                        }
                    },
                },
                "responses": ok(ref("RespStream"), ex_stream),
            }
        },
        "/api/streams/{id}": {
            "delete": {
                "tags": ["设备管理"],
                "summary": "删除码流",
                "operationId": "streamDelete",
                "parameters": [
                    path_p("id", "integer", "路径参数：码流主键 ID", 11, "int64"),
                ],
                "responses": ok(ref("RespVoid"), None),
            }
        },
        "/api/streams/{id}/live": {
            "put": {
                "tags": ["直播"],
                "summary": "设为业务直播流",
                "description": "同设备互斥：将该码流 liveEnabled 置 true，其余置 false。无 Body。",
                "operationId": "streamSetLive",
                "parameters": [
                    path_p("id", "integer", "路径参数：码流主键 ID", 11, "int64"),
                ],
                "responses": ok(ref("RespStream"), ex_stream),
            }
        },
        "/api/preview/start": {
            "post": {
                "tags": ["直播"],
                "summary": "开始预览",
                "description": "返回已注册播放地址；人数由 ZLM Hook 维护。",
                "operationId": "previewStart",
                "requestBody": {
                    "required": True,
                    "description": "预览参数",
                    "content": {
                        "application/json": {
                            "schema": ref("PreviewRequest"),
                            "example": {"deviceId": "CAM_EAST_01", "streamType": "sub"},
                        }
                    },
                },
                "responses": ok(ref("RespLiveStart"), ex_live),
            }
        },
        "/api/recordings": {
            "get": {
                "tags": ["录像回放"],
                "summary": "管理端-录像列表",
                "description": "不含 videoUrl；播放请用 GET /api/recordings/{deviceId}/{fileName}。from/to 为毫秒时间戳。",
                "operationId": "recordingsList",
                "parameters": [
                    q("deviceId", "string", True, "【必填】设备业务编码", "CAM_EAST_01"),
                    q("from", "integer", False, "开始时间（含），毫秒时间戳", 1726502400000, "int64"),
                    q("to", "integer", False, "结束时间（含），毫秒时间戳", 1726588799999, "int64"),
                ],
                "responses": ok(
                    ref("RespRecordingList"),
                    [{k: v for k, v in ex_recording_url.items() if k != "videoUrl"}],
                ),
            }
        },
        "/api/recordings/days": {
            "get": {
                "tags": ["录像回放"],
                "summary": "管理端-有录像的日期",
                "description": "返回指定年/月内有录像的日期列表（yyyy-MM-dd），供回放日历展示。",
                "operationId": "recordingsDays",
                "parameters": [
                    q("deviceId", "string", True, "【必填】设备业务编码", "CAM_EAST_01"),
                    q("year", "integer", True, "【必填】年份，如 2026", 2026),
                    q("month", "integer", True, "【必填】月份 1~12", 9),
                ],
                "responses": ok(ref("RespRecordingDays"), ex_recording_days),
            }
        },
        "/api/recordings/clip": {
            "get": {
                "tags": ["录像片段截取"],
                "summary": "管理端-单条截取（元数据）",
                "description": "按 deviceId + at + seconds 截取前后各 N 秒，返回 videoUrl、startTime、endTime。",
                "operationId": "recordingsClip",
                "parameters": [
                    q("deviceId", "string", True, "【必填】设备业务编码", "CAM_EAST_01"),
                    q("at", "integer", True, "【必填】事件时间点，毫秒时间戳", 1730000030000, "int64"),
                    q("seconds", "integer", False, "前后各取秒数，默认 30", 30),
                ],
                "responses": ok(ref("RespClip"), ex_clip),
            }
        },
        "/api/recordings/clips": {
            "post": {
                "tags": ["录像片段截取"],
                "summary": "管理端-批量截取录像片段",
                "description": "Body 为 JSON 数组 [{ deviceId, at, seconds }, ...]，单次最多 50 条。",
                "operationId": "recordingsClipsBatch",
                "requestBody": {
                    "required": True,
                    "content": {
                        "application/json": {
                            "schema": arr(ref("RecordClipItemRequest"), "批量截取请求"),
                            "example": [{"deviceId": "CAM_EAST_01", "at": 1730000030000, "seconds": 30}],
                        }
                    },
                },
                "responses": ok(ref("RespClipBatch"), ex_clip_batch),
            }
        },
        "/api/recordings/clip/file": {
            "get": {
                "tags": ["录像片段截取"],
                "summary": "管理端-片段 MP4 直链",
                "description": "返回 video/mp4。测试环境 auth.enabled=false 时无需 token；"
                "否则 video 标签可带 ?token= 或 Header Bearer。",
                "operationId": "recordingsClipFile",
                "parameters": [
                    q("deviceId", "string", True, "【必填】设备业务编码", "CAM_EAST_01"),
                    q("at", "integer", True, "【必填】毫秒时间戳", 1730000030000, "int64"),
                    q("seconds", "integer", False, "前后各取秒数，默认 30", 30),
                    q("token", "string", False, "可选：JWT，供 video 标签无法带 Header 时使用", None),
                ],
                "responses": {
                    "200": {
                        "description": "MP4 文件流",
                        "content": {
                            "video/mp4": {
                                "schema": {"type": "string", "format": "binary", "description": "片段 MP4"}
                            }
                        },
                    }
                },
            }
        },
        "/api/recordings/{deviceId}/{fileName}": {
            "get": {
                "tags": ["录像回放"],
                "summary": "管理端-录像文件",
                "description": "返回 video/mp4 二进制流，非 JSON。",
                "operationId": "recordingFile",
                "parameters": [
                    path_p("deviceId", "string", "路径参数：设备业务编码", "CAM_EAST_01"),
                    path_p("fileName", "string", "路径参数：录像文件名，如 20260910_143000.mp4", "20260910_143000.mp4"),
                ],
                "responses": {
                    "200": {
                        "description": "MP4 文件流。Content-Type: video/mp4；Content-Disposition: inline",
                        "content": {
                            "video/mp4": {
                                "schema": {
                                    "type": "string",
                                    "format": "binary",
                                    "description": "录像文件二进制内容",
                                }
                            }
                        },
                    }
                },
            }
        },
        "/api/open/devices": {
            "get": {
                "tags": ["对外开放（无鉴权）"],
                "summary": "开放-查询设备",
                "description": "无需登录。name、deviceId 均可选，模糊匹配；都不传返回全部。",
                "operationId": "openSearchDevices",
                "security": [],
                "parameters": [
                    q("name", "string", False, "设备名称，模糊匹配；可选", "东门"),
                    q("deviceId", "string", False, "设备编码，模糊匹配；可选", "CAM_EAST"),
                ],
                "responses": ok(ref("RespDeviceList"), [ex_device_list]),
            }
        },
        "/api/open/devices/{deviceId}/streams": {
            "get": {
                "tags": ["对外开放（无鉴权）"],
                "summary": "开放-设备码流列表",
                "description": "无需登录。deviceId 精确匹配；设备不存在返回失败。",
                "operationId": "openListStreams",
                "security": [],
                "parameters": [
                    path_p("deviceId", "string", "路径参数：设备业务编码（精确）", "CAM_EAST_01"),
                ],
                "responses": ok(ref("RespStreamList"), [ex_stream]),
            }
        },
        "/api/open/devices/{deviceId}/recording-days": {
            "get": {
                "tags": ["对外开放（无鉴权）"],
                "summary": "开放-有录像的日期",
                "description": "无需登录。返回指定年/月内有录像的日期 yyyy-MM-dd 列表。",
                "operationId": "openRecordingDays",
                "security": [],
                "parameters": [
                    path_p("deviceId", "string", "路径参数：设备业务编码", "CAM_EAST_01"),
                    q("year", "integer", True, "【必填】年份", 2026),
                    q("month", "integer", True, "【必填】月份 1~12", 9),
                ],
                "responses": ok(ref("RespRecordingDays"), ex_recording_days),
            }
        },
        "/api/open/devices/{deviceId}/recordings": {
            "get": {
                "tags": ["对外开放（无鉴权）"],
                "summary": "开放-历史录像列表",
                "description": "无需登录。返回含 videoUrl；from/to 为毫秒时间戳；无录像时 data=[]。",
                "operationId": "openListRecordings",
                "security": [],
                "parameters": [
                    path_p("deviceId", "string", "路径参数：设备业务编码", "CAM_EAST_01"),
                    q("from", "integer", False, "开始时间（含），毫秒时间戳", 1726502400000, "int64"),
                    q("to", "integer", False, "结束时间（含），毫秒时间戳", 1726588799999, "int64"),
                ],
                "responses": ok(ref("RespRecordingUrlList"), [ex_recording_url]),
            }
        },
        "/api/open/recordings/{deviceId}/{fileName}": {
            "get": {
                "tags": ["对外开放（无鉴权）"],
                "summary": "开放-录像文件直链",
                "description": "无需登录。返回 video/mp4，供 videoUrl 直接播放。",
                "operationId": "openRecordingFile",
                "security": [],
                "parameters": [
                    path_p("deviceId", "string", "路径参数：设备业务编码", "CAM_EAST_01"),
                    path_p("fileName", "string", "路径参数：录像文件名", "20260910_143000.mp4"),
                ],
                "responses": {
                    "200": {
                        "description": "MP4 文件流，无鉴权可直接访问",
                        "content": {
                            "video/mp4": {
                                "schema": {
                                    "type": "string",
                                    "format": "binary",
                                    "description": "录像文件二进制内容",
                                }
                            }
                        },
                    }
                },
            }
        },
        "/api/open/clips": {
            "post": {
                "tags": ["对外开放（无鉴权）", "录像片段截取"],
                "summary": "开放-批量截取录像片段",
                "description": "无需登录。Body 为 JSON 数组 [{ deviceId, at, seconds }, ...]。",
                "operationId": "openClipsBatch",
                "security": [],
                "requestBody": {
                    "required": True,
                    "content": {
                        "application/json": {
                            "schema": arr(ref("RecordClipItemRequest"), "批量截取请求"),
                            "example": [{"deviceId": "CAM_EAST_01", "at": 1730000030000, "seconds": 30}],
                        }
                    },
                },
                "responses": ok(ref("RespClipBatch"), ex_clip_batch),
            }
        },
        "/api/open/devices/{deviceId}/clip": {
            "get": {
                "tags": ["对外开放（无鉴权）", "录像片段截取"],
                "summary": "开放-单条截取（元数据）",
                "description": "无需登录。返回 videoUrl、startTime、endTime（毫秒）。",
                "operationId": "openClip",
                "security": [],
                "parameters": [
                    path_p("deviceId", "string", "设备业务编码", "CAM_EAST_01"),
                    q("at", "integer", True, "【必填】毫秒时间戳", 1730000030000, "int64"),
                    q("seconds", "integer", False, "前后各取秒数，默认 30", 30),
                ],
                "responses": ok(ref("RespClip"), ex_clip),
            }
        },
        "/api/open/devices/{deviceId}/clip/info": {
            "get": {
                "tags": ["对外开放（无鉴权）", "录像片段截取"],
                "summary": "开放-单条截取（同 /clip）",
                "description": "与 GET /api/open/devices/{deviceId}/clip 相同，保留兼容。",
                "operationId": "openClipInfo",
                "security": [],
                "parameters": [
                    path_p("deviceId", "string", "设备业务编码", "CAM_EAST_01"),
                    q("at", "integer", True, "【必填】毫秒时间戳", 1730000030000, "int64"),
                    q("seconds", "integer", False, "前后各取秒数，默认 30", 30),
                ],
                "responses": ok(ref("RespClip"), ex_clip),
            }
        },
        "/api/open/devices/{deviceId}/clip/file": {
            "get": {
                "tags": ["对外开放（无鉴权）", "录像片段截取"],
                "summary": "开放-片段 MP4 直链",
                "description": "无需登录。返回 video/mp4。",
                "operationId": "openClipFile",
                "security": [],
                "parameters": [
                    path_p("deviceId", "string", "设备业务编码", "CAM_EAST_01"),
                    q("at", "integer", True, "【必填】毫秒时间戳", 1730000030000, "int64"),
                    q("seconds", "integer", False, "前后各取秒数，默认 30", 30),
                ],
                "responses": {
                    "200": {
                        "description": "MP4 文件流",
                        "content": {
                            "video/mp4": {
                                "schema": {"type": "string", "format": "binary", "description": "片段 MP4"}
                            }
                        },
                    }
                },
            }
        },
        "/api/gb28181/config": {
            "get": {
                "tags": ["GB28181"],
                "summary": "GB28181-配置",
                "description": "返回 enabled、dataSource(mock/live)、upper、media 等配置。",
                "operationId": "gb28181Config",
                "responses": ok(ref("RespGenericMap"), {"enabled": True, "dataSource": "mock", "mock": True}),
            }
        },
        "/api/gb28181/status": {
            "get": {
                "tags": ["GB28181"],
                "summary": "GB28181-运行状态",
                "operationId": "gb28181Status",
                "responses": ok(ref("RespGenericMap"), {"mode": "模拟下级平台", "registeredDevices": 2}),
            }
        },
        "/api/gb28181/catalog": {
            "get": {
                "tags": ["GB28181"],
                "summary": "GB28181-Catalog（JSON）",
                "description": "模拟/真实下级设备与通道目录。",
                "operationId": "gb28181Catalog",
                "responses": ok(ref("RespGenericMapList"), [{"deviceId": "34020000001320000001"}]),
            }
        },
        "/api/gb28181/catalog.xml": {
            "get": {
                "tags": ["GB28181"],
                "summary": "GB28181-Catalog（XML）",
                "description": "国标 XML 格式 Catalog；无需鉴权。",
                "operationId": "gb28181CatalogXml",
                "security": [],
                "responses": {
                    "200": {
                        "description": "application/xml",
                        "content": {
                            "application/xml": {
                                "schema": {"type": "string", "description": "国标 Catalog XML"}
                            }
                        },
                    }
                },
            }
        },
        "/api/gb28181/registry": {
            "get": {
                "tags": ["GB28181"],
                "summary": "GB28181-live 注册表",
                "description": "live 模式 SIP 注册设备列表。",
                "operationId": "gb28181Registry",
                "responses": ok(ref("RespGenericMapList"), []),
            }
        },
        "/api/gb28181/registry/reload-from-db": {
            "post": {
                "tags": ["GB28181"],
                "summary": "GB28181-从 DB 重载注册表",
                "description": "live 模式：从 MySQL 重新填充 Catalog 注册表。",
                "operationId": "gb28181RegistryReload",
                "responses": ok(ref("RespGenericMap"), {"devices": 2}),
            }
        },
        "/api/gb28181/sip/status": {
            "get": {
                "tags": ["GB28181"],
                "summary": "GB28181-SIP 栈状态",
                "operationId": "gb28181SipStatus",
                "responses": ok(ref("RespGenericMap"), {"listening": True}),
            }
        },
        "/api/gb28181/sip/sessions": {
            "get": {
                "tags": ["GB28181"],
                "summary": "GB28181-SIP REGISTER 会话",
                "description": "仅 live 模式。",
                "operationId": "gb28181SipSessions",
                "responses": ok(ref("RespGenericMapList"), []),
            }
        },
        "/api/gb28181/sip/catalog-query/{deviceId}": {
            "post": {
                "tags": ["GB28181"],
                "summary": "GB28181-发送 Catalog Query",
                "description": "live 模式：向已注册下级发送 Catalog 查询。",
                "operationId": "gb28181SipCatalogQuery",
                "parameters": [path_p("deviceId", "string", "下级设备 ID", "34020000001320000001")],
                "responses": ok(ref("RespGenericMap"), {"deviceId": "34020000001320000001", "sent": True}),
            }
        },
        "/api/gb28181/devices/{deviceId}/play": {
            "post": {
                "tags": ["GB28181", "直播"],
                "summary": "GB28181-设备点播",
                "description": "按设备业务 ID 国标点播，逻辑同业务端 /live。",
                "operationId": "gb28181PlayDevice",
                "parameters": [path_p("deviceId", "string", "设备业务编码", "CAM_EAST_01")],
                "responses": ok(ref("RespLiveStart"), ex_live),
            }
        },
        "/api/gb28181/biz-devices": {
            "get": {
                "tags": ["GB28181"],
                "summary": "GB28181-业务对齐设备视图",
                "operationId": "gb28181BizDevices",
                "responses": ok(ref("RespGenericMapList"), []),
            }
        },
        "/api/gb28181/sync-check": {
            "get": {
                "tags": ["GB28181"],
                "summary": "GB28181-Catalog 与 DB 一致性校验",
                "operationId": "gb28181SyncCheck",
                "responses": ok(ref("RespGenericMap"), {"ok": True}),
            }
        },
        "/api/gb28181/channels/{channelId}/invite": {
            "post": {
                "tags": ["GB28181", "直播"],
                "summary": "GB28181-通道 INVITE 点播",
                "description": "mock 返回演示 playUrl；live 接 RTP。",
                "operationId": "gb28181Invite",
                "parameters": [path_p("channelId", "string", "国标通道 ID", "34020000001320000002")],
                "responses": ok(ref("RespGenericMap"), {"playUrl": "http://8.130.74.232:8080/live/xxx.flv"}),
            }
        },
        "/api/gb28181/channels/{channelId}/bye": {
            "post": {
                "tags": ["GB28181"],
                "summary": "GB28181-停止点播（BYE）",
                "description": "仅 live 模式。",
                "operationId": "gb28181Bye",
                "parameters": [path_p("channelId", "string", "国标通道 ID", "34020000001320000002")],
                "responses": ok(ref("RespGenericMap"), {"stopped": True}),
            }
        },
        "/api/gb28181/invite/sessions": {
            "get": {
                "tags": ["GB28181"],
                "summary": "GB28181-当前 INVITE 会话",
                "description": "仅 live 模式。",
                "operationId": "gb28181InviteSessions",
                "responses": ok(ref("RespGenericMapList"), []),
            }
        },
        "/api/uniview/config": {
            "get": {
                "tags": ["宇视 PTZ"],
                "summary": "宇视-配置",
                "description": "dataSource: mock / live。",
                "operationId": "univiewConfig",
                "responses": ok(ref("RespGenericMap"), {"dataSource": "mock", "mock": True}),
            }
        },
        "/api/uniview/live/readiness": {
            "get": {
                "tags": ["宇视 PTZ"],
                "summary": "宇视-live 联调就绪检查",
                "operationId": "univiewLiveReadiness",
                "responses": ok(ref("RespGenericMap"), {"configured": False}),
            }
        },
        "/api/uniview/devices": {
            "get": {
                "tags": ["宇视 PTZ"],
                "summary": "宇视-设备列表",
                "operationId": "univiewDevices",
                "responses": ok(ref("RespGenericMapList"), [{"deviceId": "TIC7632_01", "name": "云台1"}]),
            }
        },
        "/api/uniview/devices/{deviceId}": {
            "get": {
                "tags": ["宇视 PTZ"],
                "summary": "宇视-设备详情",
                "operationId": "univiewDevice",
                "parameters": [path_p("deviceId", "string", "设备编码", "TIC7632_01")],
                "responses": ok(ref("RespGenericMap"), {"deviceId": "TIC7632_01"}),
            }
        },
        "/api/uniview/ptz/devices": {
            "get": {
                "tags": ["宇视 PTZ"],
                "summary": "宇视-PTZ 设备列表（含预置位）",
                "operationId": "univiewPtzDevices",
                "responses": ok(ref("RespGenericMapList"), [{"deviceId": "TIC7632_01", "presets": []}]),
            }
        },
        "/api/uniview/ptz/{deviceId}/move": {
            "post": {
                "tags": ["宇视 PTZ"],
                "summary": "宇视-云台方向",
                "description": "direction: up/down/left/right 等。",
                "operationId": "univiewPtzMove",
                "parameters": [
                    path_p("deviceId", "string", "设备编码", "TIC7632_01"),
                    q("direction", "string", True, "方向：up/down/left/right", "up"),
                    q("speed", "integer", False, "速度 1~8，默认 4", 4),
                ],
                "responses": ok(ref("RespGenericMap"), {"success": True, "mock": True}),
            }
        },
        "/api/uniview/ptz/{deviceId}/zoom": {
            "post": {
                "tags": ["宇视 PTZ"],
                "summary": "宇视-变倍",
                "operationId": "univiewPtzZoom",
                "parameters": [
                    path_p("deviceId", "string", "设备编码", "TIC7632_01"),
                    q("action", "string", True, "in / out", "in"),
                    q("speed", "integer", False, "速度，默认 4", 4),
                ],
                "responses": ok(ref("RespGenericMap"), {"success": True}),
            }
        },
        "/api/uniview/ptz/{deviceId}/focus": {
            "post": {
                "tags": ["宇视 PTZ"],
                "summary": "宇视-对焦",
                "operationId": "univiewPtzFocus",
                "parameters": [
                    path_p("deviceId", "string", "设备编码", "TIC7632_01"),
                    q("action", "string", True, "near / far", "near"),
                    q("speed", "integer", False, "速度，默认 4", 4),
                ],
                "responses": ok(ref("RespGenericMap"), {"success": True}),
            }
        },
        "/api/uniview/ptz/{deviceId}/wide-angle": {
            "post": {
                "tags": ["宇视 PTZ"],
                "summary": "宇视-一键广角",
                "operationId": "univiewPtzWideAngle",
                "parameters": [path_p("deviceId", "string", "设备编码", "TIC7632_01")],
                "responses": ok(ref("RespGenericMap"), {"success": True}),
            }
        },
        "/api/uniview/ptz/{deviceId}/preset/{index}/goto": {
            "post": {
                "tags": ["宇视 PTZ"],
                "summary": "宇视-调用预置位",
                "operationId": "univiewPtzGotoPreset",
                "parameters": [
                    path_p("deviceId", "string", "设备编码", "TIC7632_01"),
                    path_p("index", "integer", "预置位编号 1~1024", 1),
                ],
                "responses": ok(ref("RespGenericMap"), {"success": True}),
            }
        },
        "/api/uniview/ptz/{deviceId}/preset/{index}": {
            "post": {
                "tags": ["宇视 PTZ"],
                "summary": "宇视-保存预置位",
                "operationId": "univiewPtzSetPreset",
                "parameters": [
                    path_p("deviceId", "string", "设备编码", "TIC7632_01"),
                    path_p("index", "integer", "预置位编号", 1),
                    q("name", "string", True, "预置位名称", "东门"),
                    q("overwrite", "boolean", False, "是否覆盖已存在预置位，默认 false", False),
                ],
                "responses": ok(ref("RespGenericMap"), {"success": True, "index": 1, "name": "东门"}),
            }
        },
        "/api/uniview/ptz/{deviceId}/snapshot": {
            "post": {
                "tags": ["宇视 PTZ"],
                "summary": "宇视-抓拍",
                "operationId": "univiewPtzSnapshot",
                "parameters": [
                    path_p("deviceId", "string", "设备编码", "TIC7632_01"),
                    q("channelType", "string", False, "visible / thermal，默认 visible", "visible"),
                ],
                "responses": ok(ref("RespGenericMap"), {"imageUrl": "/api/uniview/mock/snapshot/TIC7632_01.jpg"}),
            }
        },
    },
    "components": {"schemas": schemas},
}

out = Path(__file__).resolve().parent / "yapi-openapi.json"
out.write_text(json.dumps(doc, ensure_ascii=False, indent=2), encoding="utf-8")
print("Wrote", out)
print("paths", len(doc["paths"]), "schemas", len(schemas))
