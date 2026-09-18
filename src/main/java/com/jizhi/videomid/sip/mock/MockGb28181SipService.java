package com.jizhi.videomid.sip.mock;

import com.jizhi.videomid.config.Gb28181Properties;
import com.jizhi.videomid.gb28181.Gb28181CatalogPort;
import com.jizhi.videomid.sip.Gb28181SipPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "gb28181.data-source", havingValue = "mock", matchIfMissing = true)
public class MockGb28181SipService implements Gb28181SipPort {

    private static final Logger log = LoggerFactory.getLogger(MockGb28181SipService.class);

    private final Gb28181Properties props;
    private final Gb28181CatalogPort catalogPort;
    private volatile boolean started;

    public MockGb28181SipService(Gb28181Properties props, Gb28181CatalogPort catalogPort) {
        this.props = props;
        this.catalogPort = catalogPort;
    }

    @Override
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("running", started);
        m.put("mode", "mock");
        m.put("sipListen", props.getUpper().getSipIp() + ":" + props.getUpper().getSipPort());
        m.put("sipId", props.getUpper().getSipId());
        m.put("registeredCatalogItems", catalogPort.listCatalog().size());
        m.put("note", "mock 不开启真实 UDP 5060；Catalog/INVITE 由 REST 模拟");
        return m;
    }

    @Override
    public void ensureStarted() {
        if (started) {
            return;
        }
        started = true;
        log.info("[GB28181-mock] 模拟上级就绪 sipId={} listen={}:{} catalogDevices={}",
                props.getUpper().getSipId(),
                props.getUpper().getSipIp(),
                props.getUpper().getSipPort(),
                catalogPort.listCatalog().size());
    }
}
