package com.jizhi.videomid.session;

import com.jizhi.videomid.config.ZlmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 关掉播放器后 ZLM 有时不立刻回调 on_flow_report（流量未达阈值等），
 * 定时用 getMediaList 的观看人数把 Redis 对齐。
 */
@Component
public class PlayCountReconciler {

    private static final Logger log = LoggerFactory.getLogger(PlayCountReconciler.class);

    private final PreviewService previewService;
    private final ZlmProperties zlmProperties;

    public PlayCountReconciler(PreviewService previewService, ZlmProperties zlmProperties) {
        this.previewService = previewService;
        this.zlmProperties = zlmProperties;
    }

    @Scheduled(fixedDelay = 5000)
    public void sync() {
        if (zlmProperties.getPlayCountSyncMs() <= 0) {
            return;
        }
        try {
            previewService.reconcileFromZlm();
        } catch (Exception e) {
            log.warn("[本服务→ZLM] 定时同步 getMediaList 失败: {}", e.getMessage());
        }
    }
}
