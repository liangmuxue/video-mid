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
  deviceId: prop("string", "设备业务编码，全局唯一", "UV_10135"),
  name: prop("string", "设备名称，可空", "演示球机", true),
  address: prop("string", "安装地址，可空", "宇视在线调试", true),
  folderId: prop("integer", "所属目录 ID，未分组为 null", 2, true, "int64"),
  status: DEVICE_STATUS,
};

const CATALOG_STREAM = {
  channelType: prop("string", "可见光为 visible", "visible"),
  streamType: prop("string", "main 主码流 / sub 辅码流 / third 第三流", "sub"),
  streamUrl: prop("string", "HTTP-FLV。此时 ZLM 上可能还没有流", "http://8.130.74.232:8080/live/uv_UV_10135_sub.live.flv"),
};

const FOLDER_BASE = {
  id: prop("integer", "目录主键 ID", 1, false, "int64"),
  parentId: prop("integer", "父目录 ID；根节点为 null", undefined, true, "int64"),
  name: prop("string", "目录名称", "园区"),
  sortNo: prop("integer", "同级排序号，越小越靠前", 1),
};

const DEV = "UV_10135";
const PLAY = "http://8.130.74.232:8080/live/uv_UV_10135_sub.live.flv";

const ex_catalog_stream = {
  channelType: "visible",
  streamType: "sub",
  streamUrl: PLAY,
};

const ex_device = {
  deviceId: DEV,
  name: "演示球机",
  address: "宇视在线调试",
  folderId: 2,
  status: 1,
  playable: true,
  livePlayable: true,
};

const ex_folder = {
  id: 1,
  parentId: null,
  name: "园区",
  sortNo: 1,
  deviceCount: 0,
  totalDeviceCount: 1,
  children: [
    {
      id: 2,
      parentId: 1,
      name: "东门区域",
      sortNo: 1,
      deviceCount: 1,
      totalDeviceCount: 1,
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
  fileName: "C1/B1790092800/E1790093684",
  recordTime: 1790092800000,
  endTime: 1790093684000,
  size: 0,
  source: "nvr",
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
    playable: prop("boolean", "可否回放/截取：status=2（已停用）时为 false，其余为 true", true),
    livePlayable: prop("boolean", "可否直播：仅 status=1（已启用）时为 true", true),
  },
  "业务端设备。不含登录信息和码流明细"
);

const CatalogStream = obj(CATALOG_STREAM, "云台设备详情里的已启用码流");

const BizLiveBrief = obj(
  {
    streamType: prop("string", "业务直播码流：main / sub / third，优先 sub", "sub"),
    streamUrl: prop("string", "HTTP-FLV。此时可能尚未拉流", PLAY),
  },
  "业务直播流；没有时整个字段为 null"
);

const BizDeviceDetail = obj(
  {
    ...DEVICE_BASE,
    playable: prop("boolean", "可否回放：status=2 时为 false", true),
    livePlayable: prop("boolean", "可否直播：仅 status=1 时为 true", true),
    liveStream: { ...BizLiveBrief, nullable: true, description: "当前业务直播流；未设置时为 null" },
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
    ref: prop("integer", "当前观看人数，由 ZLM 回调维护，本接口不加人数", 0),
    countBy: prop("string", "固定 zlm-hook", "zlm-hook"),
  },
  "开始预览 / 业务直播结果"
);

const RecordingWithUrl = obj(
  {
    deviceId: prop("string", "设备编码", "UV_10135"),
    fileName: prop("string", "文件名。录像机为通道与起止时间拼出的名字；本地为 MP4 文件名", "C1/B1790092800/E1790093684"),
    recordTime: { ...TS, description: "开始毫秒", example: 1790092800000 },
    endTime: {
      ...TS,
      description: "结束毫秒。本地文件探测失败时可能没有",
      example: 1790093684000,
      nullable: true,
    },
    size: prop("integer", "字节。录像机上的文件可能为 0", 0, false, "int64"),
    source: prop("string", "已绑定录像机时为 nvr。本地 MP4 没有这个字段", "nvr", true),
    durationSeconds: prop("number", "仅本地 MP4：时长秒。探测失败时不返回", 300, true),
    videoUrl: prop(
      "string",
      "仅本地 MP4：可直接播放，指向 /api/open/recordings/{deviceId}/{fileName}。录像机记录没有此字段，播放走 /api/recordings/playback.flv",
      undefined,
      true
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

const ClipInfo = obj(
  {
    deviceId: prop("string", "设备编码", "UV_10135"),
    at: { ...TS, description: "时间点，已换成毫秒", example: 1730000030000 },
    seconds: prop("integer", "实际使用的前后秒数", 30),
    windowStart: { ...TS, description: "请求窗口起点，at − seconds×1000" },
    windowEnd: { ...TS, description: "请求窗口终点，at + seconds×1000" },
    startTime: { ...TS, description: "片段实际开始，受录像覆盖裁切" },
    endTime: { ...TS, description: "片段实际结束" },
    durationSeconds: prop("number", "实际时长秒", 60),
    videoUrl: prop(
      "string",
      "给播放器的 MP4 地址，即片段文件接口",
      "http://8.130.74.232:8090/api/biz/devices/UV_10135/clip/file?at=1730000030000&seconds=30"
    ),
    clipUrl: prop("string", "与 videoUrl 相同", "http://8.130.74.232:8090/api/biz/devices/UV_10135/clip/file?at=1730000030000&seconds=30"),
    sourceFiles: arr(prop("string", "参与截取的原始文件名"), "源文件名"),
    clipFileName: prop("string", "缓存片段文件名", "UV_10135_1730000030000_30.mp4"),
    size: prop("integer", "片段字节数", 5242880, false, "int64"),
  },
  "按时间点截取的结果。只切本地 MP4"
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
    presetIndex: prop("integer", "预置位编号（调用或保存预置位）", 1, true),
    overwrite: prop("boolean", "保存预置位时是否覆盖", false, true),
    name: prop("string", "保存后的预置位名称", "门口", true),
    channelType: prop("string", "抓拍通道类型，默认 visible", "visible", true),
    response: prop("string", "宇视 LAPI 原始 JSON 字符串", "{\"Response\":{\"ResponseCode\":0}}", true),
  },
  "云台控制结果。live=true 表示已发到该设备自己的摄像机"
);

const schemas = {
  FolderNode,
  BizDeviceItem,
  CatalogStream,
  BizLiveStreamBrief: BizLiveBrief,
  BizDeviceDetail,
  LiveStartResult: LiveStart,
  RecordingWithUrl,
  RecordClipItemRequest,
  ClipInfo,
  ClipBatchItem,
  PtzResult,
  RespFolderTree: resp_schema(arr(ref("FolderNode"), "目录树根节点数组"), [ex_folder]),
  RespBizDeviceList: resp_schema(arr(ref("BizDeviceItem"), "业务端设备列表"), [ex_device]),
  RespBizDeviceDetail: resp_schema(ref("BizDeviceDetail"), {
    ...ex_device,
    liveStream: {
      streamType: "sub",
      streamUrl: PLAY,
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
        "【必填】方向：up / down / left / right / left_up / left_down / right_up / right_down / stop",
        "up",
        false,
        undefined,
        ["up", "down", "left", "right", "left_up", "left_down", "right_up", "right_down", "stop"]
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
      "仅含云台监控与业务端（目录、设备、直播、录像、录像机回放、片段截取）。\n" +
      "除目录树、设备详情、录像机回放、片段文件外，带参接口一律 POST + JSON Body。\n" +
      "统一响应 { code, message, data }；code=0 成功。时间字段为毫秒时间戳。\n" +
      "设备 status：0=不可用 1=已启用 2=已停用。直播仅 status=1；回放/截取 status=2 不可用。\n" +
      "返回只有调用方会用到的字段。没有密码、经纬度、创建时间、服务器磁盘路径。\n" +
      "当前 uniview.data-source=live。云台发给这台摄像机自己的地址；历史录像在它绑定的录像机上。示例设备 UV_10135。\n" +
      "鉴权：auth.enabled=false 时免登录；true 时 Header Authorization: Bearer {token}。" +
      "<video> 带不了 Header，URL 上加 token。",
    version: "1.0.0",
  },
  servers: [
    { url: "http://8.130.74.232:8090", description: "线上环境" },
    { url: "http://127.0.0.1:8090", description: "本地环境" },
  ],
  tags: [
    { name: "云台监控", description: "已配置宇视地址的设备。云台控制 POST + JSON Body。看直播用 /api/preview/start。" },
    { name: "业务端", description: "目录树、设备、直播、录像、录像机回放、片段截取。" },
  ],
  paths: {
    "/api/uniview/ptz/devices": {
      get: {
        tags: ["云台监控"],
        summary: "云台-设备列表",
        description: "只返回已配置宇视地址的设备。presets 为该摄像机当前预置位，连不上时为空数组，不影响列表。",
        operationId: "ptzDevices",
        responses: ok(
          resp_schema(
            arr(
              obj({
                deviceId: prop("string", "设备编码，如 UV_10135", DEV),
                name: prop("string", "名称，可空", "演示球机", true),
                presets: arr(
                  obj({
                    index: prop("integer", "预置位编号", 1),
                    name: prop("string", "预置位名称", "门口"),
                  }),
                  "预置位。项为 index、name"
                ),
              }),
              "云台设备"
            ),
            "云台设备列表"
          ),
          [
            {
              deviceId: DEV,
              name: "演示球机",
              presets: [{ index: 1, name: "门口" }],
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
          "取这台摄像机已启用码流的播放地址。不拉流。要出画面再调 /api/preview/start。未配置宇视地址时失败：设备未配置宇视地址。",
        operationId: "univiewDevice",
        parameters: [path_p("deviceId", "string", "设备编码", DEV)],
        responses: ok(
          resp_schema(
            obj({
              deviceId: prop("string", "设备编码", DEV),
              name: prop("string", "名称", "演示球机"),
              streams: arr(ref("CatalogStream"), "已启用码流"),
            }),
            "云台设备详情"
          ),
          {
            deviceId: DEV,
            name: "演示球机",
            streams: [ex_catalog_stream],
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
          presetIndex: 1,
          name: "门口",
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
        description: "单台设备，并带上业务直播流地址。不拉流。没有业务直播流时 liveStream 为 null。不存在时 message=设备不存在: {deviceId}。",
        operationId: "bizDeviceDetail",
        requestBody: jsonBody(ref("BizDeviceIdRequest"), { deviceId: "UV_10135" }),
        responses: ok(ref("RespBizDeviceDetail"), {
          ...ex_device,
          liveStream: {
            streamType: "sub",
            streamUrl: PLAY,
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
        description: "给回放日历打点。已绑定录像机的设备查录像机；没绑定的查中台本地 MP4。已停用设备不能查。返回 yyyy-MM-dd，升序。",
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
          "已停用设备不能查。已绑定录像机的记录带 source=nvr，没有 videoUrl，播放走 GET /api/recordings/playback.flv。没绑定的是本地 MP4，带 durationSeconds 和 videoUrl。",
        operationId: "bizRecordings",
        requestBody: jsonBody(ref("BizRecordingsRequest"), {
          deviceId: "UV_10135",
          from: 1726502400000,
          to: 1726588799999,
        }),
        responses: ok(ref("RespRecordingUrlList"), [ex_recording]),
      },
    },
    "/api/recordings/playback.flv": {
      get: {
        tags: ["业务端"],
        summary: "业务端-录像机回放",
        description:
          "source=nvr 的记录用这个地址播放。服务向录像机取这段时间的流，转成 FLV 再输出。不是 JSON。begin 用列表里的 recordTime，拖动进度时加上偏移毫秒；end 用 endTime。大于 1e10 按毫秒，否则按秒。",
        operationId: "nvrPlayback",
        parameters: [
          q("deviceId", "string", true, "设备编码", DEV),
          q("begin", "integer", true, "开始时间。毫秒或秒都可以，大于 1e10 按毫秒", 1790092800000, "int64"),
          q("end", "integer", true, "结束时间，规则同 begin", 1790093684000, "int64"),
          q("token", "string", false, "auth.enabled=true 时，video 标签把登录 token 放这里"),
        ],
        responses: {
          200: {
            description: "FLV 码流。没绑定录像机：该设备没有绑定录像设备。开始不早于结束：回放开始时间必须早于结束时间。",
            content: { "video/x-flv": { schema: { type: "string", format: "binary" } } },
          },
        },
      },
    },
    "/api/biz/devices/{deviceId}/clip": {
      get: {
        tags: ["业务端"],
        summary: "业务端-按时间点截取",
        description:
          "以 at 为中心，前后各 seconds 秒，从本地 MP4 切出一段。只生成播放地址，文件在访问片段文件接口时才输出。不切录像机上的历史录像。该时间点没有本地录像：该时间点无可用录像。",
        operationId: "bizClip",
        parameters: [
          path_p("deviceId", "string", "设备编码", DEV),
          q("at", "string", true, "时间点。10 位是秒，11~13 位是毫秒", "1730000030000"),
          q("seconds", "integer", false, "前后各取的秒数，默认 30，须大于 0，最大 150", 30),
        ],
        responses: ok(resp_schema(ref("ClipInfo"), ex_clip_ok), {
          deviceId: DEV,
          at: 1730000030000,
          seconds: 30,
          startTime: 1730000000000,
          endTime: 1730000060000,
          durationSeconds: 60,
          videoUrl: ex_clip_ok.videoUrl,
          clipUrl: ex_clip_ok.clipUrl,
          windowStart: 1730000000000,
          windowEnd: 1730000060000,
          sourceFiles: ["20260910_143000.mp4"],
          clipFileName: "UV_10135_1730000030000_30.mp4",
          size: 5242880,
        }),
      },
    },
    "/api/biz/clips": {
      post: {
        tags: ["业务端"],
        summary: "业务端-批量截取录像片段",
        description:
          "一次切多段本地 MP4，不切录像机历史录像。Body 是 JSON 数组，不是 {items:[]}。最多 50 条。单条失败不影响其它条。成功条带截取结果字段且 ok=true；失败条 ok=false，并有 deviceId、at、seconds、error。",
        operationId: "bizClipsBatch",
        requestBody: jsonBody(arr(ref("RecordClipItemRequest"), "批量截取请求"), [
          { deviceId: "UV_10135", at: 1730000030000, seconds: 30 },
          { deviceId: "CAM_WEST_02", at: 1730000100000, seconds: 15 },
        ]),
        responses: ok(ref("RespClipBatch"), [ex_clip_ok, ex_clip_fail]),
      },
    },
    "/api/biz/devices/{deviceId}/clip/file": {
      get: {
        tags: ["业务端"],
        summary: "业务端-片段文件",
        description: "按时间点截取和批量截取返回的 videoUrl 实际文件。响应是 MP4，不是 JSON。",
        operationId: "bizClipFile",
        parameters: [
          path_p("deviceId", "string", "设备编码", DEV),
          q("at", "string", true, "与截取时相同的时间点", "1730000030000"),
          q("seconds", "integer", false, "与截取时相同，默认 30", 30),
          q("token", "string", false, "开启鉴权时给 video 标签用"),
        ],
        responses: {
          200: {
            description: "MP4 文件流",
            content: { "video/mp4": { schema: { type: "string", format: "binary" } } },
          },
        },
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
