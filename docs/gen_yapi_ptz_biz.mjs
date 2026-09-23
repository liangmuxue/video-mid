import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

function prop(typ, desc, example, nullable, fmt, enumVals, items) {
  const p = { type: typ, description: desc };
  if (example !== undefined && example !== null) p.example = example;
  if (nullable === true) p.nullable = true;
  if (fmt) p.format = fmt;
  if (enumVals) p.enum = enumVals;
  if (items !== undefined && items !== null) p.items = items;
  return p;
}

function obj(properties, required, desc) {
  if (typeof required === "string") {
    desc = required;
    required = undefined;
  }
  const s = { type: "object", properties };
  if (required) s.required = required;
  if (desc) s.description = desc;
  return s;
}

function arr(item_schema, desc) {
  return { type: "array", description: desc, items: item_schema };
}

function ref(name) {
  return { $ref: `#/components/schemas/${name}` };
}

function q(name, typ, required, desc, example, fmt, enumVals, defaultVal) {
  const schema = { type: typ, description: desc };
  if (example !== undefined && example !== null) schema.example = example;
  if (fmt) schema.format = fmt;
  if (enumVals) schema.enum = enumVals;
  if (defaultVal !== undefined) schema.default = defaultVal;
  const p = { name, in: "query", required, description: desc, schema };
  if (example !== undefined && example !== null) p.example = example;
  return p;
}

function path_p(name, typ, desc, example) {
  const p = {
    name,
    in: "path",
    required: true,
    description: desc,
    schema: { type: typ, description: desc },
  };
  if (example !== undefined && example !== null) {
    p.example = example;
    p.schema.example = example;
  }
  return p;
}

function resp_schema(data_prop, example_data) {
  return {
    type: "object",
    description: "统一 JSON 响应：code / message / data",
    properties: {
      code: prop("integer", "业务状态码：0=成功，非 0=失败（一般为 -1）", 0),
      message: prop("string", "提示信息：成功一般为 success，失败为错误原因", "success"),
      data: data_prop,
    },
    example: { code: 0, message: "success", data: example_data },
  };
}

function ok(schema_or_ref, example_data) {
  const body = { schema: schema_or_ref };
  if (example_data !== undefined) {
    body.example = { code: 0, message: "success", data: example_data };
  }
  return {
    200: {
      description: "成功。失败时 HTTP 多为 400，body 中 code!=0，message 为原因，data 常为 null",
      content: { "application/json": body },
    },
  };
}

function jsonBody(schema, example, required = true, desc = "JSON 请求体") {
  const content = { schema };
  if (example !== undefined) content.example = example;
  return {
    required,
    description: desc,
    content: { "application/json": content },
  };
}

const TS = { type: "integer", description: "毫秒时间戳（Unix epoch millis）", example: 1726560000000, format: "int64" };
const DEVICE_STATUS = prop(
  "integer",
  "设备状态：0=不可用（巡检离线，不可手改） 1=已启用（可直播可回放） 2=已停用（灰色可见不可播）",
  1,
  false,
  undefined,
  [0, 1, 2]
);

const DEVICE_BASE = {
  id: prop("integer", "设备主键 ID（数据库自增）", 1, false, "int64"),
  deviceId: prop("string", "设备业务编码，全局唯一，后续接口都用这个", "UV_10135"),
  name: prop("string", "设备名称", "东门球机"),
  platformId: prop("string", "上级/平台 ID，可空", undefined, true),
  folderId: prop("integer", "所属设备目录 ID，可空表示未归类", 2, true, "int64"),
  status: DEVICE_STATUS,
  manufacturer: prop("string", "厂商名称，可空", "宇视", true),
  model: prop("string", "设备型号，可空", "IPC-B", true),
  address: prop("string", "安装地址/位置描述，可空", "小区东门", true),
  ptzType: prop("integer", "云台类型：常见 0=无云台，1=球机等，可空", 1, true),
  gatewayId: prop("string", "网关/接入网关 ID，可空", undefined, true),
  longitude: prop("number", "经度，可空", 116.4, true),
  latitude: prop("number", "纬度，可空", 39.9, true),
  host: prop("string", "宇视摄像机 IP。有值表示走宇视拉流和该摄像机自己的云台账号，不返回密码", "39.185.236.176", true),
  port: prop("integer", "宇视 LAPI 端口，IPC 常见为映射端口", 10135, true),
  username: prop("string", "宇视登录用户名。密码不下发，只返回 passwordSet", "guest", true),
  passwordSet: prop("boolean", "是否已保存密码。编辑时密码留空表示沿用原密码", true),
  accessChannel: prop("string", "宇视通道号，IPC 一般为 0", "0", true),
  accessStatus: prop("string", "接入状态：unknown / online / offline / auth_failed", "online", true),
  accessError: prop("string", "最近一次接入失败原因，成功时为空", undefined, true),
  createdAt: { ...TS, description: "创建时间（毫秒时间戳）" },
  updatedAt: { ...TS, description: "最后更新时间（毫秒时间戳）", example: 1726646400000 },
};

const STREAM = {
  id: prop("integer", "码流主键 ID", 11, false, "int64"),
  deviceId: prop("string", "所属设备编码", "UV_10135"),
  streamType: prop("string", "码流类型：main=主码流，sub=辅码流，third=第三流。由摄像机实际启用的码流决定，不是固定两路", "sub"),
  streamIndex: prop("integer", "宇视码流序号：0=主码流，1=辅码流，2=第三流", 1, true),
  channelId: prop("string", "国标通道 ID。宇视拉流为空，避免和通道 0 冲突", undefined, true),
  streamUrl: prop("string", "HTTP-FLV 播放地址。地址先写入码流表，不代表 ZLM 已经在拉流", "http://8.130.74.232:8080/live/uv_UV_10135_sub.live.flv"),
  streamName: prop("string", "码流显示名称：主码流 / 辅码流 / 第三流", "辅码流", true),
  status: prop("string", "ON=正在拉流，OFF=未拉流", "OFF"),
  zlmApp: prop("string", "ZLM 应用名，宇视固定 live", "live", true),
  zlmStream: prop("string", "ZLM 流名，形如 uv_{设备编码}_{码流类型}", "uv_UV_10135_sub", true),
  sortNo: prop("integer", "排序号，越小越靠前，一般为 streamIndex+1", 2),
  liveEnabled: prop("boolean", "是否为该设备的业务直播流（同设备仅一条为 true）。优先辅码流，没有再用主码流", true),
  playCount: prop("integer", "当前播放人数（由 ZLM Hook 维护）", 0),
  createdAt: { ...TS, description: "创建时间（毫秒时间戳）" },
  updatedAt: { ...TS, description: "最后更新时间（毫秒时间戳）", example: 1726646400000 },
};

const FOLDER_BASE = {
  id: prop("integer", "目录主键 ID", 1, false, "int64"),
  parentId: prop("integer", "父目录 ID；根节点为 null", undefined, true, "int64"),
  name: prop("string", "目录名称", "园区"),
  sortNo: prop("integer", "同级排序号，越小越靠前", 1),
  path: prop("string", "目录路径，如 /1/ 或 /1/2/", "/1/"),
  createdAt: { ...TS, description: "创建时间（毫秒时间戳）" },
  updatedAt: { ...TS, description: "最后更新时间（毫秒时间戳）" },
};

const DEV = "UV_10135";
const PLAY = "http://8.130.74.232:8080/live/uv_UV_10135_sub.live.flv";

const ex_stream = {
  id: 11,
  deviceId: DEV,
  streamType: "sub",
  streamIndex: 1,
  channelId: null,
  streamUrl: PLAY,
  streamName: "辅码流",
  status: "OFF",
  zlmApp: "live",
  zlmStream: "uv_UV_10135_sub",
  sortNo: 2,
  liveEnabled: true,
  playCount: 0,
  createdAt: 1726560000000,
  updatedAt: 1726646400000,
};

const ex_device = {
  id: 1,
  deviceId: DEV,
  name: "东门球机",
  platformId: null,
  folderId: 2,
  status: 1,
  manufacturer: "宇视",
  model: "IPC-B",
  address: "小区东门",
  ptzType: 1,
  gatewayId: null,
  longitude: 116.4,
  latitude: 39.9,
  host: "39.185.236.176",
  port: 10135,
  username: "guest",
  passwordSet: true,
  accessChannel: "0",
  accessStatus: "online",
  accessError: null,
  createdAt: 1726560000000,
  updatedAt: 1726646400000,
  streamCount: 2,
  playable: true,
  livePlayable: true,
};

const ex_folder = {
  id: 1,
  parentId: null,
  name: "园区",
  sortNo: 1,
  path: "/1/",
  createdAt: 1726560000000,
  updatedAt: 1726646400000,
  deviceCount: 2,
  totalDeviceCount: 5,
  children: [
    {
      id: 2,
      parentId: 1,
      name: "东门",
      sortNo: 1,
      path: "/1/2/",
      createdAt: 1726560000000,
      updatedAt: 1726646400000,
      deviceCount: 2,
      totalDeviceCount: 2,
      children: [],
    },
  ],
};

const ex_live = {
  deviceId: DEV,
  streamType: "sub",
  streamUrl: PLAY,
  playUrl: PLAY,
  liveEnabled: true,
  ref: 0,
  countBy: "zlm-hook",
};

const ex_recording = {
  deviceId: DEV,
  fileName: "20260910_143000.mp4",
  recordTime: 1725952200000,
  endTime: 1725952500000,
  durationSeconds: 300,
  size: 12345678,
  path: "/data/testdata-records/UV_10135/20260910_143000.mp4",
  videoUrl: "http://8.130.74.232:8090/api/open/recordings/UV_10135/20260910_143000.mp4",
};

const ex_clip_ok = {
  deviceId: DEV,
  at: 1730000030000,
  seconds: 30,
  startTime: 1730000000000,
  endTime: 1730000060000,
  durationSeconds: 60,
  videoUrl: "http://8.130.74.232:8090/api/biz/devices/UV_10135/clip/file?at=1730000030000&seconds=30",
  clipUrl: "http://8.130.74.232:8090/api/biz/devices/UV_10135/clip/file?at=1730000030000&seconds=30",
  windowStart: 1730000000000,
  windowEnd: 1730000060000,
  sourceFiles: ["20260910_143000.mp4"],
  clipFileName: "UV_10135_1730000030000_30.mp4",
  size: 5242880,
  ok: true,
};

const ex_clip_fail = {
  deviceId: "CAM_WEST_02",
  at: 1730000100000,
  seconds: 15,
  ok: false,
  error: "该时间点无可用录像",
};

const ex_ptz_move = {
  success: true,
  mock: false,
  live: true,
  action: "move",
  deviceId: DEV,
  ptzCmd: 1026,
  speed: 4,
  response: "{\"Response\":{\"ResponseCode\":0}}",
};

const PTZ_DEV = DEV;

const FolderNode = obj(
  {
    ...FOLDER_BASE,
    deviceCount: prop("integer", "本目录直接挂载的设备数量（不含子目录）", 2),
    totalDeviceCount: prop("integer", "本目录及所有子孙目录下的设备总数", 5),
    children: {
      type: "array",
      description: "子目录列表；叶子节点为空数组 []",
      items: { $ref: "#/components/schemas/FolderNode" },
    },
  },
  "目录树节点"
);

const BizDeviceItem = obj(
  {
    ...DEVICE_BASE,
    streamCount: prop("integer", "该设备已注册码流数量", 2),
    playable: prop("boolean", "可否回放/截取：status=2（已停用）时为 false，其余为 true", true),
    livePlayable: prop("boolean", "可否直播：仅 status=1（已启用）时为 true", true),
  },
  "业务端设备列表项（不含码流明细）"
);

const StreamView = obj(STREAM, "码流信息");

const BizLiveBrief = obj(
  {
    id: prop("integer", "业务直播流的码流主键 ID", 11, false, "int64"),
    streamType: prop("string", "业务直播使用的码流类型，默认优先 sub", "sub"),
    streamUrl: prop("string", "业务直播播放地址", "http://8.130.74.232:8080/live/uv_UV_10135_sub.live.flv"),
    streamName: prop("string", "码流名称", "东门-子码流"),
    liveEnabled: prop("boolean", "固定为 true，表示当前即为业务直播流", true),
  },
  "业务直播流摘要；设备未配置业务直播流时整个字段为 null"
);

const BizDeviceDetail = obj(
  {
    ...DEVICE_BASE,
    streamCount: prop("integer", "码流数量", 2),
    playable: prop("boolean", "可否回放：status=2 时为 false", true),
    livePlayable: prop("boolean", "可否直播：仅 status=1 时为 true", true),
    streams: arr(StreamView, "设备下全部码流"),
    liveStream: { ...BizLiveBrief, nullable: true, description: "当前业务直播流配置；未设置时为 null" },
  },
  "业务端设备详情"
);

const LiveStart = obj(
  {
    deviceId: prop("string", "设备编码", "UV_10135"),
    streamType: prop("string", "实际开播使用的码流类型", "sub"),
    streamUrl: prop("string", "取流/播放地址", "http://8.130.74.232:8080/live/uv_UV_10135_sub.live.flv"),
    playUrl: prop("string", "前端播放地址，与 streamUrl 相同", "http://8.130.74.232:8080/live/uv_UV_10135_sub.live.flv"),
    liveEnabled: prop("boolean", "该码流是否标记为业务直播流", true),
    ref: prop("integer", "当前播放引用人数（ZLM Hook 统计；本接口不加人数）", 1),
    countBy: prop("string", "人数统计来源，普通流为 zlm-hook；国标点播为 gb28181", "zlm-hook"),
    streamName: prop("string", "码流名称（国标点播时可能有）", undefined, true),
    channelId: prop("string", "国标通道 ID（国标点播时可能有）", undefined, true),
    transport: prop("string", "国标点播时为 gb28181", undefined, true),
    dataSource: prop("string", "国标点播时为 mock 或 live", undefined, true),
    gb28181: obj({}, "国标 INVITE 原始结果（仅国标点播时有）"),
  },
  "业务端开播结果"
);

const RecordingWithUrl = obj(
  {
    deviceId: prop("string", "设备编码", "UV_10135"),
    fileName: prop("string", "录像文件名，一般为 yyyyMMdd_HHmmss.mp4", "20260910_143000.mp4"),
    recordTime: { ...TS, description: "录制开始时刻（毫秒，从文件名解析）", example: 1725952200000 },
    endTime: {
      ...TS,
      description: "录制结束时刻（毫秒，ffprobe 读取 MP4 时长；失败时不返回）",
      example: 1725952500000,
      nullable: true,
    },
    durationSeconds: prop("number", "录像时长（秒，ffprobe 读取；失败时不返回）", 300, true),
    size: prop("integer", "文件大小，单位：字节", 12345678, false, "int64"),
    path: prop("string", "服务器本地绝对路径（内部用，对外可忽略）", "/data/.../20260910_143000.mp4"),
    videoUrl: prop(
      "string",
      "可直接播放的绝对地址，指向 /api/open/recordings/{deviceId}/{fileName}，无需 Token",
      "http://8.130.74.232:8090/api/open/recordings/UV_10135/20260910_143000.mp4"
    ),
  },
  "带播放链接的录像项"
);

const RecordClipItemRequest = obj(
  {
    deviceId: prop("string", "【必填】设备业务编码", "UV_10135"),
    at: prop(
      "integer",
      "【必填】事件时间点。10 位=秒，11~13 位=毫秒。也可用字符串。别名 timestamp / time / ts",
      1730000030000,
      false,
      "int64"
    ),
    seconds: prop("integer", "时间戳前后各取 N 秒；不传默认 30，须 >0，最大 150", 30),
  },
  ["deviceId", "at"],
  "批量截取录像：单条请求项"
);

const ClipBatchItem = obj(
  {
    ok: prop("boolean", "本条是否成功", true),
    error: prop("string", "失败原因；ok=false 时有值，如「该时间点无可用录像」", undefined, true),
    deviceId: prop("string", "设备编码；失败时也可能带回请求值", "UV_10135", true),
    at: { ...TS, description: "事件时间点（已规范为毫秒）；能解析则带回", example: 1730000030000, nullable: true },
    seconds: prop("integer", "前后各取的秒数（请求值或默认 30）", 30),
    windowStart: { ...TS, description: "成功时：请求窗口起点 = at − seconds×1000", nullable: true },
    windowEnd: { ...TS, description: "成功时：请求窗口终点 = at + seconds×1000", nullable: true },
    startTime: { ...TS, description: "成功时：片段实际开始（受录像覆盖裁切）", nullable: true },
    endTime: { ...TS, description: "成功时：片段实际结束", nullable: true },
    durationSeconds: prop("number", "成功时：实际时长秒（四舍五入）", 60, true),
    videoUrl: prop(
      "string",
      "成功时：可播的 MP4 地址，给播放器直接使用。开启鉴权时 <video> 请在 URL 后加 token",
      "http://8.130.74.232:8090/api/biz/devices/UV_10135/clip/file?at=1730000030000&seconds=30",
      true
    ),
    clipUrl: prop("string", "成功时：与 videoUrl 相同（兼容字段）", undefined, true),
    sourceFiles: arr(prop("string", "源录像文件名"), "成功时：参与截取的原始 MP4 文件名列表"),
    clipFileName: prop("string", "成功时：缓存片段文件名", "UV_10135_1730000030000_30.mp4", true),
    size: prop("integer", "成功时：片段文件大小（字节）", 5242880, true, "int64"),
  },
  "批量截取结果项（含成功/失败）"
);

const PtzResult = obj(
  {
    success: prop("boolean", "指令是否已受理", true),
    mock: prop("boolean", "是否模拟模式；live 时为 false 或不出现", true),
    live: prop("boolean", "是否真实宇视；mock 时不出现", undefined, true),
    action: prop("string", "动作标识：move / zoom / focus / wide-angle / goto-preset / set-preset / snapshot", "move"),
    deviceId: prop("string", "设备编码", PTZ_DEV),
    ptzCmd: prop("integer", "方向/变倍/对焦下发给宇视的 PTZCmd 数值", 1026, true),
    speed: prop("integer", "实际速度，live 会夹到 1~8", 4, true),
    presetIndex: prop("integer", "预置位编号（预置位接口）", 1, true),
    overwrite: prop("boolean", "保存预置位时是否覆盖", false, true),
    index: prop("integer", "保存预置位后的编号（与 presetIndex 相同）", 1, true),
    name: prop("string", "保存后的预置位名称", "东门全景", true),
    zoom: prop("number", "广角 mock 固定 1.0；保存预置位时为中台记录倍率", 1.0, true),
    channelType: prop("string", "抓拍通道类型 visible / thermal", "visible", true),
    response: prop("string", "宇视 LAPI 原始 JSON 字符串", "{\"Response\":{\"ResponseCode\":0}}", true),
  },
  "云台控制结果。live=true 表示已发到该设备自己的摄像机"
);

const schemas = {
  FolderNode,
  BizDeviceItem,
  StreamView,
  BizLiveStreamBrief: BizLiveBrief,
  BizDeviceDetail,
  LiveStartResult: LiveStart,
  RecordingWithUrl,
  RecordClipItemRequest,
  ClipBatchItem,
  PtzResult,
  RespFolderTree: resp_schema(arr(ref("FolderNode"), "目录树根节点数组"), [ex_folder]),
  RespBizDeviceList: resp_schema(arr(ref("BizDeviceItem"), "业务端设备列表"), [ex_device]),
  RespBizDeviceDetail: resp_schema(ref("BizDeviceDetail"), {
    ...ex_device,
    streams: [ex_stream],
    liveStream: {
      id: 11,
      streamType: "sub",
      streamUrl: ex_stream.streamUrl,
      streamName: "东门-子码流",
      liveEnabled: true,
    },
  }),
  RespLiveStart: resp_schema(ref("LiveStartResult"), ex_live),
  RespRecordingDays: resp_schema(
    arr(prop("string", "有录像的日期 yyyy-MM-dd", "2026-09-17"), "某月内有录像的日期列表（升序）"),
    ["2026-09-10", "2026-09-15", "2026-09-17"]
  ),
  RespRecordingUrlList: resp_schema(arr(ref("RecordingWithUrl"), "录像列表（含 videoUrl）"), [ex_recording]),
  RespClipBatch: resp_schema(arr(ref("ClipBatchItem"), "批量截取结果，顺序与请求一致"), [ex_clip_ok, ex_clip_fail]),
  RespPtz: resp_schema(ref("PtzResult"), ex_ptz_move),
  PtzMoveRequest: obj(
    {
      deviceId: prop("string", "【必填】云台设备编码", PTZ_DEV),
      direction: prop(
        "string",
        "【必填】方向：up / down / left / right / left_up / left_down / right_up / right_down",
        "up",
        false,
        undefined,
        ["up", "down", "left", "right", "left_up", "left_down", "right_up", "right_down"]
      ),
      speed: prop("integer", "速度，默认 4；live 限制 1~8", 4),
    },
    ["deviceId", "direction"],
    "云台方向移动请求"
  ),
  PtzActionRequest: obj(
    {
      deviceId: prop("string", "【必填】云台设备编码", PTZ_DEV),
      action: prop("string", "【必填】动作。变倍推荐 zoom_in/zoom_out；对焦推荐 focus_near/focus_far", "zoom_in"),
      speed: prop("integer", "速度，默认 4", 4),
    },
    ["deviceId", "action"],
    "云台变倍/对焦请求"
  ),
  PtzDeviceIdRequest: obj(
    { deviceId: prop("string", "【必填】云台设备编码", PTZ_DEV) },
    ["deviceId"],
    "仅设备编码"
  ),
  PtzPresetGotoRequest: obj(
    {
      deviceId: prop("string", "【必填】云台设备编码", PTZ_DEV),
      index: prop("integer", "【必填】预置位编号，建议 1~1024", 1),
    },
    ["deviceId", "index"],
    "调用预置位请求"
  ),
  PtzPresetSaveRequest: obj(
    {
      deviceId: prop("string", "【必填】云台设备编码", PTZ_DEV),
      index: prop("integer", "【必填】预置位编号", 1),
      name: prop("string", "【必填】预置位名称，不能为空或纯空格", "东门全景"),
      overwrite: prop("boolean", "是否覆盖已有同号预置位，默认 false", false),
    },
    ["deviceId", "index", "name"],
    "保存预置位请求"
  ),
  PtzSnapshotRequest: obj(
    {
      deviceId: prop("string", "【必填】云台设备编码", PTZ_DEV),
      channelType: prop("string", "通道类型：visible=可见光（默认），thermal=热成像", "visible"),
    },
    ["deviceId"],
    "抓拍请求"
  ),
  BizDeviceListRequest: obj(
    {
      folderId: prop("integer", "按目录过滤：目录主键 ID；不传则返回全部设备", 2, true, "int64"),
      includeChildren: prop("boolean", "当传入 folderId 时：true=包含子孙（默认 true）；false=仅本目录", true),
    },
    "业务端设备列表请求；可传空对象 {}"
  ),
  BizDeviceIdRequest: obj(
    { deviceId: prop("string", "【必填】设备业务编码", "UV_10135") },
    ["deviceId"],
    "按设备编码查询/操作"
  ),
  BizRecordingDaysRequest: obj(
    {
      deviceId: prop("string", "【必填】设备业务编码", "UV_10135"),
      year: prop("integer", "【必填】年份 1970~2100", 2026),
      month: prop("integer", "【必填】月份 1~12", 9),
    },
    ["deviceId", "year", "month"],
    "查询某月有录像的日期"
  ),
  BizRecordingsRequest: obj(
    {
      deviceId: prop("string", "【必填】设备业务编码", "UV_10135"),
      from: prop("integer", "开始时间（含），毫秒时间戳；不传则不限起始", 1726502400000, true, "int64"),
      to: prop("integer", "结束时间（含），毫秒时间戳；不传则不限结束", 1726588799999, true, "int64"),
    },
    ["deviceId"],
    "录像列表请求"
  ),
};

const doc = {
  openapi: "3.0.3",
  info: {
    title: "视频中台 - 云台监控 / 业务端",
    description:
      "仅含云台控制与业务端（含批量片段截取）。除目录树外，带参接口一律 POST + JSON Body，路径不含变量。\n" +
      "统一响应 { code, message, data }；code=0 成功。时间字段为毫秒时间戳。\n" +
      "设备 status：0=不可用 1=已启用 2=已停用。直播仅 status=1；回放/截取 status=2 不可用。\n" +
      "当前 uniview.data-source=live。云台和直播使用设备表上的 IP、端口、账号，示例设备 UV_10135。密码不出现在响应里。有人看才拉流，无人观看后停止。\n" +
      "鉴权：auth.enabled=false 时免登录；true 时 Header Authorization: Bearer {token}。" +
      "批量截取返回的 videoUrl 给 video 标签播时，可在 URL 上加 token。",
    version: "1.0.0",
  },
  servers: [
    { url: "http://8.130.74.232:8090", description: "线上环境" },
    { url: "http://127.0.0.1:8090", description: "本地环境" },
  ],
  tags: [
    { name: "云台监控", description: "真实宇视设备。云台控制 POST + JSON Body。看直播用 /api/preview/start，第一人拉流，无人观看后停止。" },
    { name: "业务端", description: "目录树、设备、直播、录像、批量截取片段。" },
  ],
  paths: {
    "/api/uniview/ptz/devices": {
      get: {
        tags: ["云台监控"],
        summary: "云台-设备列表",
        description: "只返回已配置宇视 IP 的设备。presets 为该摄像机当前预置位，连不上时为空数组，不影响列表。不含密码。",
        operationId: "ptzDevices",
        responses: ok(
          resp_schema(
            arr(
              obj({
                deviceId: prop("string", "设备编码", DEV),
                name: prop("string", "设备名称", "东门球机"),
                manufacturer: prop("string", "厂商", "宇视", true),
                model: prop("string", "型号", "IPC-S6424-IR@P-X25-VF", true),
                ptzType: prop("integer", "云台类型", 1, true),
                host: prop("string", "摄像机 IP", "39.185.236.176"),
                port: prop("integer", "LAPI 端口", 10135),
                accessChannel: prop("string", "通道号，IPC 一般为 0", "0"),
                presets: arr(
                  obj({
                    index: prop("integer", "预置位编号", 1),
                    name: prop("string", "预置位名称", "东门全景"),
                  }),
                  "摄像机上的预置位"
                ),
              }),
              "云台设备"
            ),
            "云台设备列表"
          ),
          [
            {
              deviceId: DEV,
              name: "东门球机",
              manufacturer: "宇视",
              model: "IPC-S6424-IR@P-X25-VF",
              ptzType: 1,
              host: "39.185.236.176",
              port: 10135,
              accessChannel: "0",
              presets: [{ index: 1, name: "东门全景" }],
            },
          ]
        ),
      },
    },
    "/api/uniview/devices/{deviceId}": {
      get: {
        tags: ["云台监控"],
        summary: "云台-设备详情（含码流，不拉流）",
        description:
          "返回该设备在码流表中的主/辅/第三流及播放地址。本接口不向摄像机拉流。看画面请再调 /api/preview/start。未配置 IP 时失败：设备未配置宇视地址。",
        operationId: "univiewDevice",
        parameters: [path_p("deviceId", "string", "设备编码", DEV)],
        responses: ok(
          resp_schema(
            obj({
              deviceId: prop("string", "设备编码", DEV),
              name: prop("string", "设备名称", "东门球机"),
              host: prop("string", "摄像机 IP", "39.185.236.176"),
              port: prop("integer", "LAPI 端口", 10135),
              accessChannel: prop("string", "通道号", "0"),
              accessStatus: prop("string", "unknown / online / offline / auth_failed", "online"),
              streamCount: prop("integer", "启用的码流路数", 2),
              streams: arr(
                obj({
                  channelId: prop("string", "通道号", "0"),
                  channelType: prop("string", "可见光固定 visible", "visible"),
                  streamType: prop("string", "main / sub / third", "sub"),
                  streamName: prop("string", "主码流 / 辅码流 / 第三流", "辅码流"),
                  streamUrl: prop("string", "HTTP-FLV，此时可能尚未拉流", PLAY),
                  status: prop("string", "ON 正在拉流，OFF 未拉流", "OFF"),
                }),
                "码流"
              ),
            }),
            "宇视设备详情"
          ),
          {
            deviceId: DEV,
            name: "东门球机",
            host: "39.185.236.176",
            port: 10135,
            accessChannel: "0",
            accessStatus: "online",
            streamCount: 2,
            streams: [
              {
                channelId: "0",
                channelType: "visible",
                streamType: "sub",
                streamName: "辅码流",
                streamUrl: PLAY,
                status: "OFF",
              },
            ],
          }
        ),
      },
    },
    "/api/preview/start": {
      post: {
        tags: ["云台监控"],
        summary: "开始预览（宇视按需拉流）",
        description:
          "云台页选中设备后调用。streamType 不传默认 sub。码流带 zlmApp/zlmStream 时：ZLM 上已有该流则直接返回同一 playUrl，不再拉一次；没有则向该摄像机取直播地址并拉流。无人观看后停止拉流。播放器使用 playUrl。",
        operationId: "previewStart",
        requestBody: jsonBody(
          obj(
            {
              deviceId: prop("string", "【必填】设备编码", DEV),
              streamType: prop("string", "码流类型，默认 sub。可传 main / sub / third", "sub"),
            },
            ["deviceId"],
            "开始预览"
          ),
          { deviceId: DEV, streamType: "sub" }
        ),
        responses: ok(ref("RespLiveStart"), ex_live),
      },
    },
    "/api/uniview/ptz/move": {
      post: {
        tags: ["云台监控"],
        summary: "云台-方向移动",
        description:
          "连续转动，按住时重复调用，松开传 direction=stop。JSON Body。\n" +
          "direction：up/down/left/right/left_up/left_down/right_up/right_down/stop。其它值返回 400。\n" +
          "使用该设备自己的 IP、端口和账号，不再使用配置文件里的单一摄像机。",
        operationId: "ptzMove",
        requestBody: jsonBody(ref("PtzMoveRequest"), { deviceId: PTZ_DEV, direction: "up", speed: 4 }),
        responses: ok(ref("RespPtz"), ex_ptz_move),
      },
    },
    "/api/uniview/ptz/zoom": {
      post: {
        tags: ["云台监控"],
        summary: "云台-变倍",
        description:
          "点按一次：下发变倍后短暂保持再停止。action：in / zoom_in / tele 拉近；out / zoom_out / wide 拉远。其它值返回 400。",
        operationId: "ptzZoom",
        requestBody: jsonBody(ref("PtzActionRequest"), { deviceId: PTZ_DEV, action: "zoom_in", speed: 4 }),
        responses: ok(ref("RespPtz"), {
          success: true,
          mock: false,
          live: true,
          action: "zoom",
          deviceId: PTZ_DEV,
          speed: 4,
        }),
      },
    },
    "/api/uniview/ptz/focus": {
      post: {
        tags: ["云台监控"],
        summary: "云台-对焦",
        description:
          "点按一次：下发对焦后短暂保持再停止。action：near / focus_near 近焦；far / focus_far 远焦。其它值返回 400。",
        operationId: "ptzFocus",
        requestBody: jsonBody(ref("PtzActionRequest"), { deviceId: PTZ_DEV, action: "focus_near", speed: 4 }),
        responses: ok(ref("RespPtz"), {
          success: true,
          mock: false,
          live: true,
          action: "focus",
          deviceId: PTZ_DEV,
          speed: 4,
        }),
      },
    },
    "/api/uniview/ptz/wide-angle": {
      post: {
        tags: ["云台监控"],
        summary: "云台-一键广角",
        description: "点按一次拉远（广角）。JSON Body 只传 deviceId。速度固定 4。",
        operationId: "ptzWideAngle",
        requestBody: jsonBody(ref("PtzDeviceIdRequest"), { deviceId: PTZ_DEV }),
        responses: ok(ref("RespPtz"), {
          success: true,
          mock: false,
          live: true,
          action: "wide-angle",
          deviceId: PTZ_DEV,
          speed: 4,
        }),
      },
    },
    "/api/uniview/ptz/preset/goto": {
      post: {
        tags: ["云台监控"],
        summary: "云台-调用预置位",
        description: "转到已保存预置位。编号与保存时的 index 一致。",
        operationId: "ptzGotoPreset",
        requestBody: jsonBody(ref("PtzPresetGotoRequest"), { deviceId: PTZ_DEV, index: 1 }),
        responses: ok(ref("RespPtz"), {
          success: true,
          mock: false,
          live: true,
          action: "goto-preset",
          deviceId: PTZ_DEV,
          presetIndex: 1,
        }),
      },
    },
    "/api/uniview/ptz/preset/save": {
      post: {
        tags: ["云台监控"],
        summary: "云台-保存预置位",
        description:
          "把当前姿态存为预置位。name 不能为空。已存在且 overwrite=false 时失败：预置位 n 已存在，请勾选覆盖。",
        operationId: "ptzSetPreset",
        requestBody: jsonBody(ref("PtzPresetSaveRequest"), {
          deviceId: PTZ_DEV,
          index: 1,
          name: "东门全景",
          overwrite: false,
        }),
        responses: ok(ref("RespPtz"), {
          success: true,
          mock: false,
          live: true,
          action: "set-preset",
          deviceId: PTZ_DEV,
          presetIndex: 1,
          overwrite: false,
          index: 1,
          name: "东门全景",
          zoom: 1.0,
        }),
      },
    },
    "/api/uniview/ptz/snapshot": {
      post: {
        tags: ["云台监控"],
        summary: "云台-抓拍",
        description: "向该设备摄像机抓拍。结果在 response 字符串（LAPI JSON）。channelType 默认 visible，当前 live 仍按设备通道抓拍。",
        operationId: "ptzSnapshot",
        requestBody: jsonBody(ref("PtzSnapshotRequest"), { deviceId: PTZ_DEV, channelType: "visible" }),
        responses: ok(ref("RespPtz"), {
          success: true,
          mock: false,
          live: true,
          action: "snapshot",
          deviceId: PTZ_DEV,
          channelType: "visible",
        }),
      },
    },
    "/api/biz/folders/tree": {
      get: {
        tags: ["业务端"],
        summary: "业务端-目录树",
        description: "返回完整设备目录树。deviceCount=本级设备数，totalDeviceCount=含子孙。无请求体。",
        operationId: "bizFolderTree",
        responses: ok(ref("RespFolderTree"), [ex_folder]),
      },
    },
    "/api/biz/devices/list": {
      post: {
        tags: ["业务端"],
        summary: "业务端-设备列表",
        description: "含已停用设备；用 playable、livePlayable 判断可否回放/直播。不返回码流明细。Body 可传 {}。",
        operationId: "bizDevices",
        requestBody: jsonBody(ref("BizDeviceListRequest"), { folderId: 2, includeChildren: true }, false),
        responses: ok(ref("RespBizDeviceList"), [ex_device]),
      },
    },
    "/api/biz/devices/detail": {
      post: {
        tags: ["业务端"],
        summary: "业务端-设备详情",
        description: "含 streams 与 liveStream（业务直播流摘要，未配置为 null）。不存在时 message=设备不存在: {deviceId}。",
        operationId: "bizDeviceDetail",
        requestBody: jsonBody(ref("BizDeviceIdRequest"), { deviceId: "UV_10135" }),
        responses: ok(ref("RespBizDeviceDetail"), {
          ...ex_device,
          streams: [ex_stream],
          liveStream: {
            id: 11,
            streamType: "sub",
            streamUrl: ex_stream.streamUrl,
            streamName: "东门-子码流",
            liveEnabled: true,
          },
        }),
      },
    },
    "/api/biz/devices/live": {
      post: {
        tags: ["业务端"],
        summary: "业务端-开始直播",
        description:
          "仅 status=1 可直播，否则「仅「已启用」设备可直播」。设备已配置宇视 IP 时不走国标：第一人观看向摄像机拉流，已有流则复用，无人观看后停止拉流。前端播 playUrl。",
        operationId: "bizStartLive",
        requestBody: jsonBody(ref("BizDeviceIdRequest"), { deviceId: "UV_10135" }),
        responses: ok(ref("RespLiveStart"), ex_live),
      },
    },
    "/api/biz/devices/recording-days": {
      post: {
        tags: ["业务端"],
        summary: "业务端-有录像的日期",
        description: "返回指定年/月内有录像的日期列表（yyyy-MM-dd，升序），供回放日历。status=2 不可调用。",
        operationId: "bizRecordingDays",
        requestBody: jsonBody(ref("BizRecordingDaysRequest"), { deviceId: "UV_10135", year: 2026, month: 9 }),
        responses: ok(ref("RespRecordingDays"), ["2026-09-10", "2026-09-15", "2026-09-17"]),
      },
    },
    "/api/biz/devices/recordings": {
      post: {
        tags: ["业务端"],
        summary: "业务端-录像列表",
        description:
          "status=2 不可回放。from/to 为毫秒时间戳（可选），与文件时段有交集即返回，新的在前。videoUrl 指向开放接口，无需登录。",
        operationId: "bizRecordings",
        requestBody: jsonBody(ref("BizRecordingsRequest"), {
          deviceId: "UV_10135",
          from: 1726502400000,
          to: 1726588799999,
        }),
        responses: ok(ref("RespRecordingUrlList"), [ex_recording]),
      },
    },
    "/api/biz/clips": {
      post: {
        tags: ["业务端"],
        summary: "业务端-批量截取录像片段",
        description:
          "Body 为 JSON 数组（不是 {items:[]}），每项含 deviceId、at、seconds。以 at 为中心前后各 seconds 秒（默认 30，最大 150）。只切一条时数组放一项即可。单条失败不影响其它（ok=false + error）。整单最多 50 条。成功条的 videoUrl 给播放器直接播放。",
        operationId: "bizClipsBatch",
        requestBody: jsonBody(arr(ref("RecordClipItemRequest"), "批量截取请求"), [
          { deviceId: "UV_10135", at: 1730000030000, seconds: 30 },
          { deviceId: "CAM_WEST_02", at: 1730000100000, seconds: 15 },
        ]),
        responses: ok(ref("RespClipBatch"), [ex_clip_ok, ex_clip_fail]),
      },
    },
  },
  components: {
    schemas,
    securitySchemes: {
      bearerAuth: {
        type: "http",
        scheme: "bearer",
        bearerFormat: "JWT",
        description: "auth.enabled=true 时需要。登录后填 JWT，不要带 Bearer 前缀（YApi 会加）。",
      },
    },
  },
  security: [{ bearerAuth: [] }],
};

const out = path.join(__dirname, "yapi-ptz-biz-openapi.json");
fs.writeFileSync(out, JSON.stringify(doc, null, 2), "utf8");
console.log("Wrote", out);
console.log("paths", Object.keys(doc.paths).length);
