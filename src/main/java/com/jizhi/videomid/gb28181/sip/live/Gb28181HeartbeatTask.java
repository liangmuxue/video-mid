package com.jizhi.videomid.gb28181.sip.live;

import com.jizhi.videomid.config.Gb28181Properties;
import com.jizhi.videomid.gb28181.live.Gb28181DeviceProvisioner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** 上级平台定时向下级发送 Keepalive */
@Component
@ConditionalOnProperty(name = "gb28181.data-source", havingValue = "live")
public class Gb28181HeartbeatTask {

    private static final Logger log = LoggerFactory.getLogger(Gb28181HeartbeatTask.class);

    private final Gb28181Properties props;
    private final Gb28181SipSessionStore sessionStore;
    private final Gb28181SipOutboundClient outboundClient;
    private final Gb28181DeviceProvisioner deviceProvisioner;

    public Gb28181HeartbeatTask(Gb28181Properties props,
                                Gb28181SipSessionStore sessionStore,
                                Gb28181SipOutboundClient outboundClient,
                                Gb28181DeviceProvisioner deviceProvisioner) {
        this.props = props;
        this.sessionStore = sessionStore;
        this.outboundClient = outboundClient;
        this.deviceProvisioner = deviceProvisioner;
    }

    @Scheduled(fixedDelayString = "#{${gb28181.upper.heartbeat-interval:30} * 1000}")
    public void sendKeepalive() {
        for (String deviceId : sessionStore.pollExpired()) {
            sessionStore.ifStillAbsent(deviceId, id -> {
                try {
                    deviceProvisioner.markOffline(id);
                } catch (Exception e) {
                    log.warn("[GB28181-SIP] 注册过期下线失败 deviceId={} err={}", id, e.getMessage());
                }
            });
        }
        if (!props.getLive().isEnableSipStack() || !outboundClient.isReady()) {
            return;
        }
        List<Map<String, Object>> sessions = sessionStore.listActive();
        for (Map<String, Object> s : sessions) {
            String deviceId = String.valueOf(s.get("deviceId"));
            try {
                deviceProvisioner.markOnline(deviceId);
            } catch (Exception e) {
                log.warn("[GB28181-SIP] 注册在线但恢复已启用失败 deviceId={} err={}", deviceId, e.getMessage());
            }
            try {
                outboundClient.sendKeepalive(deviceId);
            } catch (Exception e) {
                log.debug("[GB28181-SIP] Keepalive 失败 deviceId={} err={}", deviceId, e.getMessage());
            }
        }
    }
}
