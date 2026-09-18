package com.jizhi.videomid.gb28181.live;

import com.jizhi.videomid.config.Gb28181Properties;
import com.jizhi.videomid.gb28181.Gb28181CatalogPort;
import com.jizhi.videomid.gb28181.Gb28181CatalogXmlBuilder;
import com.jizhi.videomid.sip.live.Gb28181SipOutboundClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "gb28181.data-source", havingValue = "live")
public class LiveGb28181CatalogAdapter implements Gb28181CatalogPort {

    private final Gb28181Properties props;
    private final Gb28181DeviceRegistry registry;
    private final ZlmRtpBridge rtpBridge;
    private final Gb28181SipOutboundClient sipClient;

    public LiveGb28181CatalogAdapter(Gb28181Properties props,
                                     Gb28181DeviceRegistry registry,
                                     ZlmRtpBridge rtpBridge,
                                     Gb28181SipOutboundClient sipClient) {
        this.props = props;
        this.registry = registry;
        this.rtpBridge = rtpBridge;
        this.sipClient = sipClient;
    }

    @Override
    public List<Map<String, Object>> listCatalog() {
        return registry.listCatalog();
    }

    @Override
    public Map<String, Object> getChannel(String channelId) {
        return registry.findChannel(channelId)
                .orElseThrow(() -> new IllegalArgumentException("国标通道不存在或未注册: " + channelId));
    }

    @Override
    public String buildCatalogXml() {
        return Gb28181CatalogXmlBuilder.build(listCatalog());
    }

    @Override
    public Map<String, Object> invite(String channelId) {
        Map<String, Object> ch = getChannel(channelId);
        String fallback = ch.get("streamUrl") != null ? String.valueOf(ch.get("streamUrl")) : null;
        Map<String, Object> result = rtpBridge.openReceive(channelId, fallback);
        result.put("deviceId", ch.get("deviceId"));
        result.put("deviceName", ch.get("deviceName"));

        if (props.getLive().isSendInviteOnPlay() && sipClient.isReady()) {
            Object gbDeviceId = ch.get("gbDeviceId");
            Object rtpPort = result.get("rtpPort");
            Object ssrc = result.get("ssrc");
            Object streamId = result.get("streamId");
            if (gbDeviceId != null && rtpPort instanceof Number n) {
                Map<String, Object> sipInvite = sipClient.sendPlayInvite(
                        channelId,
                        String.valueOf(gbDeviceId),
                        n.intValue(),
                        String.valueOf(ssrc),
                        streamId != null ? String.valueOf(streamId) : channelId);
                result.put("sipInvite", sipInvite);
                if (Boolean.TRUE.equals(sipInvite.get("sent"))) {
                    result.put("signaling", "SIP INVITE 已发送");
                } else {
                    result.put("signaling", "SIP INVITE 未发送: " + sipInvite.get("reason"));
                }
            }
        }
        return result;
    }

    public Map<String, Object> stopInvite(String channelId) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> bye = sipClient.sendBye(channelId);
        rtpBridge.closeReceive(channelId);
        result.put("channelId", channelId);
        result.put("bye", bye);
        result.put("stopped", Boolean.TRUE.equals(bye.get("stopped")));
        return result;
    }
}
