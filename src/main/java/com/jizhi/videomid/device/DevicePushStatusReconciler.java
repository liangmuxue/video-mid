package com.jizhi.videomid.device;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每 2 分钟检查设备推流是否正常：未推流则标为「不可用」，恢复推流则回到「已启用」。
 * 「已停用」不受巡检影响。
 */
@Component
public class DevicePushStatusReconciler {

    private static final Logger log = LoggerFactory.getLogger(DevicePushStatusReconciler.class);

    private final DeviceService deviceService;

    public DevicePushStatusReconciler(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @Scheduled(fixedDelay = 120_000, initialDelay = 20_000)
    public void sync() {
        try {
            deviceService.reconcilePushStatus();
        } catch (Exception e) {
            log.warn("设备推流巡检失败: {}", e.getMessage());
        }
    }
}
