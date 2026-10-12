package com.jizhi.videomid.device;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每 2 分钟检查推流设备是否在线：未推流则标为「不可用」，恢复推流则回到「已启用」。
 * 直接拉流没人看时本来就不在线，不参与这个判断；若已被误标为不可用，则改回已启用。
 * 国标设备跟着 SIP 注册上下线，不在这里改状态。
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
