package com.jizhi.videomid.api;

import com.jizhi.videomid.session.PreviewService;
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
 * </pre>
 */
@RestController
@RequestMapping("/index/hook")
public class ZlmHookController {

    private static final Logger log = LoggerFactory.getLogger(ZlmHookController.class);

    private final PreviewService previewService;

    public ZlmHookController(PreviewService previewService) {
        this.previewService = previewService;
    }

    /** 有播放器开始拉流 → Redis +1 */
    @PostMapping("/on_play")
    public Map<String, Object> onPlay(@RequestBody Map<String, Object> body) {
        String app = str(body.get("app"));
        String stream = str(body.get("stream"));
        log.debug("ZLM on_play app={} stream={} schema={}", app, stream, body.get("schema"));
        previewService.onPlayerStart(app, stream);
        return ok();
    }

    /**
     * 播放器或推流器断开流量上报。
     * player=true 表示播放端断开 → Redis -1
     */
    @PostMapping("/on_flow_report")
    public Map<String, Object> onFlowReport(@RequestBody Map<String, Object> body) {
        boolean player = bool(body.get("player"));
        String app = str(body.get("app"));
        String stream = str(body.get("stream"));
        log.debug("ZLM on_flow_report player={} app={} stream={}", player, app, stream);
        if (player) {
            previewService.onPlayerStop(app, stream);
        }
        return ok();
    }

    /** 流已无人观看 → Redis 置 0（兜底纠偏） */
    @PostMapping("/on_stream_none_reader")
    public Map<String, Object> onStreamNoneReader(@RequestBody Map<String, Object> body) {
        String app = str(body.get("app"));
        String stream = str(body.get("stream"));
        log.debug("ZLM on_stream_none_reader app={} stream={}", app, stream);
        previewService.onNoneReader(app, stream);
        // close=false：不要让 ZLM 因此关流（只用来清人数）
        Map<String, Object> resp = ok();
        resp.put("close", false);
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
