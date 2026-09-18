package com.jizhi.videomid.sip.live;

import com.jizhi.videomid.config.Gb28181Properties;
import com.jizhi.videomid.gb28181.live.Gb28181DbCatalogSeeder;
import com.jizhi.videomid.gb28181.live.Gb28181DeviceRegistry;
import com.jizhi.videomid.sip.Gb28181SipPort;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "gb28181.data-source", havingValue = "live")
public class LiveGb28181SipService implements Gb28181SipPort {

    private static final Logger log = LoggerFactory.getLogger(LiveGb28181SipService.class);

    private final Gb28181Properties props;
    private final Gb28181DeviceRegistry registry;
    private final Gb28181DbCatalogSeeder seeder;
    private final Gb28181SipStackManager stackManager;
    private final Gb28181SipListenerImpl sipListener;
    private final Gb28181SipSessionStore sessionStore;

    private volatile boolean started;
    private volatile String startError = "";

    public LiveGb28181SipService(Gb28181Properties props,
                                 Gb28181DeviceRegistry registry,
                                 Gb28181DbCatalogSeeder seeder,
                                 Gb28181SipStackManager stackManager,
                                 Gb28181SipListenerImpl sipListener,
                                 Gb28181SipSessionStore sessionStore) {
        this.props = props;
        this.registry = registry;
        this.seeder = seeder;
        this.stackManager = stackManager;
        this.sipListener = sipListener;
        this.sessionStore = sessionStore;
    }

    @Override
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("running", started);
        m.put("mode", "live");
        m.put("sipStackRunning", stackManager.isRunning());
        m.put("sipListen", props.getUpper().getSipIp() + ":" + props.getUpper().getSipPort());
        m.put("bindIp", props.getLive().getBindIp());
        m.put("mediaIp", stackManager.mediaIp());
        m.put("sipId", props.getUpper().getSipId());
        m.put("sipSessions", sessionStore.activeCount());
        m.put("registeredCatalogDevices", registry.deviceCount());
        m.put("seedFromDb", props.getLive().isSeedFromDb());
        m.put("enableSipStack", props.getLive().isEnableSipStack());
        m.put("sendInviteOnPlay", props.getLive().isSendInviteOnPlay());
        m.put("autoCatalogQuery", props.getLive().isAutoCatalogQuery());
        m.put("enableDigestAuth", props.getLive().isEnableDigestAuth());
        m.put("fallbackToDbStream", props.getLive().isFallbackToDbStream());
        m.put("zlmApp", props.getLive().getZlmApp());
        m.put("note", started
                ? (stackManager.isRunning()
                ? "JAIN-SIP 上级运行中：REGISTER / Catalog / INVITE"
                : "SIP 栈未启用或启动失败，Catalog 仍可用 DB 种子")
                : "未启动");
        if (!startError.isBlank()) {
            m.put("startError", startError);
        }
        return m;
    }

    @Override
    public void ensureStarted() {
        if (started) {
            return;
        }
        if (props.getLive().isSeedFromDb()) {
            seeder.seedFromDb();
        }
        try {
            stackManager.start(sipListener);
        } catch (Exception e) {
            startError = e.getMessage();
            log.error("[GB28181-live] JAIN-SIP 启动失败: {}", e.getMessage(), e);
        }
        started = true;
        log.info("[GB28181-live] 上级启动 catalogDevices={} sipStack={} sipSessions={}",
                registry.deviceCount(),
                stackManager.isRunning(),
                sessionStore.activeCount());
    }

    @PreDestroy
    public void shutdown() {
        stackManager.stop();
    }

    public void reloadCatalogFromDb() {
        seeder.seedFromDb();
    }
}
