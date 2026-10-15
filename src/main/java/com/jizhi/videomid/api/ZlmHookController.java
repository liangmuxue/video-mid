package com.jizhi.videomid.api;

import com.jizhi.videomid.session.PreviewService;
import com.jizhi.videomid.uniview.cloud.UniviewCloudPlayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ZLMediaKit Hook 回调（免鉴权，见 WebMvcConfig /index/hook/**）。
 * <p>
 * 在 ZLM config.ini [hook] 中配置：
 * <pre>
 * enable=1
 * on_play=http://{video-mid主机}:8090/index/hook/on_play
 * on_flow_report=http://{video-mid主机}:8090/index/hook/on_flow_report
 * on_stream_none_reader=http://{video-mid主机}:8090/index/hook/on_stream_none_reader
 * on_stream_not_found=http://{video-mid主机}:8090/index/hook/on_stream_not_found
 * </pre>
 * 宇视云实况依赖 on_stream_not_found 拉流。无人观看时只对 app=cloud 返回 close=true，其它流仍为 close=false。
 */
@RestController
@RequestMapping("/index/hook")
public class ZlmHookController {

    private static final Logger log = LoggerFactory.getLogger(ZlmHookController.class);

    private final PreviewService previewService;
    private final UniviewCloudPlayService univiewCloudPlayService;

    public ZlmHookController(PreviewService previewService, UniviewCloudPlayService univiewCloudPlayService) {
        this.previewService = previewService;
        this.univiewCloudPlayService = univiewCloudPlayService;
    }

    /**
     * 播放器请求一条还不存在的流。只对宇视云 app=cloud 向云端拉流，其它应用原样放过。
     */
    @PostMapping("/on_stream_not_found")
    public Map<String, Object> onStreamNotFound(@RequestBody Map<String, Object> body) {
        String app = str(body.get("app"));
        String stream = str(body.get("stream"));
        log.info("[ZLM→本服务] 收到 on_stream_not_found app={} stream={} schema={} ip={}",
                app, stream, body.get("schema"), body.get("ip"));
        try {
            univiewCloudPlayService.ensurePulled(app, stream);
        } catch (RuntimeException e) {
            log.warn("[宇视云] 按播放请求拉流失败 app={} stream={} err={}", app, stream, e.getMessage());
        }
        return ok();
    }

    /** 有播放器开始拉流 → Redis 人数对齐 / +1 */
    @PostMapping("/on_play")
    public Map<String, Object> onPlay(@RequestBody Map<String, Object> body) {
        String app = str(body.get("app"));
        String stream = str(body.get("stream"));
        String id = str(body.get("id"));
        log.info("[ZLM→本服务] 收到 on_play app={} stream={} schema={} id={} ip={} 原始参数={}",
                app, stream, body.get("schema"), id, body.get("ip"), body);
        long ref = previewService.onPlayerStart(app, stream, id);
        log.info("[ZLM→本服务] on_play 处理{} app={} stream={} redis人数={}",
                ref >= 0 ? "成功" : "失败(未匹配码流)", app, stream, ref);
        return ok();
    }

    /**
     * 播放器或推流器断开。按 ZLM 当前观看人数回写 Redis（关播放器会减到真实值）。
     */
    @PostMapping("/on_flow_report")
    public Map<String, Object> onFlowReport(@RequestBody Map<String, Object> body) {
        boolean player = bool(body.get("player"));
        String app = str(body.get("app"));
        String stream = str(body.get("stream"));
        String schema = str(body.get("schema"));
        String id = str(body.get("id"));
        log.info("[ZLM→本服务] 收到 on_flow_report player={} schema={} app={} stream={} id={} 原始参数={}",
                player, schema, app, stream, id, body);
        long ref = previewService.onPlayerStop(app, stream, id);
        log.info("[ZLM→本服务] on_flow_report 处理{} app={} stream={} redis人数={}",
                ref >= 0 ? "成功" : "失败(未匹配码流)", app, stream, ref);
        return ok();
    }

    /** 流已无人观看 → Redis 置 0（兜底纠偏） */
    @PostMapping("/on_stream_none_reader")
    public Map<String, Object> onStreamNoneReader(@RequestBody Map<String, Object> body) {
        String app = str(body.get("app"));
        String stream = str(body.get("stream"));
        log.info("[ZLM→本服务] 收到 on_stream_none_reader app={} stream={} 原始参数={}", app, stream, body);
        long ref = previewService.onNoneReader(app, stream);
        boolean closeCloud = univiewCloudPlayService.releaseWhenNoReader(app, stream);
        log.info("[ZLM→本服务] on_stream_none_reader 处理{} app={} stream={} redis人数={} close={}",
                ref >= 0 ? "成功" : "失败(未匹配码流)", app, stream, ref, closeCloud);
        Map<String, Object> resp = ok();
        resp.put("close", closeCloud);
        return resp;
    }

    private static Map<String, Object> ok() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", 0);
        m.put("msg", "success");
        return m;
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static boolean bool(Object v) {
        if (v instanceof Boolean b) return b;
        if (v == null) return false;
        String s = String.valueOf(v).trim();
        return "1".equals(s) || "true".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s);
    }
}
