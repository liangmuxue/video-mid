package com.jizhi.videomid.gb28181.sip.live;

import com.jizhi.videomid.config.Gb28181Properties;
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

    public Gb28181HeartbeatTask(Gb28181Properties props,
                                Gb28181SipSessionStore sessionStore,
                                Gb28181SipOutboundClient outboundClient) {
        this.props = props;
        this.sessionStore = sessionStore;
        this.outboundClient = outboundClient;
    }

    @Scheduled(fixedDelayString = "#{${gb28181.upper.heartbeat-interval:30} * 1000}")
    public void sendKeepalive() {
        if (!props.getLive().isEnableSipStack() || !outboundClient.isReady()) {
            return;
        }
        List<Map<String, Object>> sessions = sessionStore.listActive();
        for (Map<String, Object> s : sessions) {
            String deviceId = String.valueOf(s.get("deviceId"));
            try {
                outboundClient.sendKeepalive(deviceId);
            } catch (Exception e) {
                log.debug("[GB28181-SIP] Keepalive 失败 deviceId={} err={}", deviceId, e.getMessage());
            }
        }
    }
}
