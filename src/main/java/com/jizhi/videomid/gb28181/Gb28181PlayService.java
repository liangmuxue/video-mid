package com.jizhi.videomid.gb28181;

import com.jizhi.videomid.config.Gb28181Properties;
import com.jizhi.videomid.device.DeviceStream;
import com.jizhi.videomid.device.DeviceStreamRepository;
import com.jizhi.videomid.device.LiveStreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** 业务端国标点播（mock：INVITE → 演示 FLV；live：SIP+RTP） */
@Service
public class Gb28181PlayService {

    private static final Logger log = LoggerFactory.getLogger(Gb28181PlayService.class);

    private final Gb28181Properties props;
    private final Gb28181CatalogPort catalogPort;
    private final DeviceStreamRepository streamRepository;

    public Gb28181PlayService(Gb28181Properties props,
                              Gb28181CatalogPort catalogPort,
                              DeviceStreamRepository streamRepository) {
        this.props = props;
        this.catalogPort = catalogPort;
        this.streamRepository = streamRepository;
    }

    public boolean preferForBizLive() {
        return props.isEnabled() && props.isPreferForBizLive();
    }

    @Transactional
    public Optional<Map<String, Object>> startLive(String deviceId) {
        if (!props.isEnabled()) {
            return Optional.empty();
        }
        DeviceStream stream = LiveStreamSupport.resolveByPriority(streamRepository, deviceId)
                .orElse(null);
        if (stream == null) {
            return Optional.empty();
        }
        String channelId = stream.getChannelId();
        if (channelId == null || channelId.isBlank()) {
            return Optional.empty();
        }
        Map<String, Object> invite = catalogPort.invite(channelId.trim());
        Object playUrl = invite.get("playUrl");
        if (playUrl == null || String.valueOf(playUrl).isBlank()) {
            return Optional.empty();
        }
        persistInviteStream(stream, invite, String.valueOf(playUrl));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deviceId", deviceId);
        data.put("streamType", stream.getStreamType());
        data.put("streamName", stream.getStreamName());
        data.put("channelId", channelId);
        data.put("streamUrl", playUrl);
        data.put("playUrl", playUrl);
        data.put("liveEnabled", true);
        data.put("transport", "gb28181");
        data.put("dataSource", props.isMock() ? "mock" : "live");
        data.put("countBy", "gb28181");

        Map<String, Object> gb = new HashMap<>(invite);
        data.put("gb28181", gb);
        return Optional.of(data);
    }

    private void persistInviteStream(DeviceStream stream, Map<String, Object> invite, String playUrl) {
        try {
            stream.setStreamUrl(playUrl);
            Object app = invite.get("zlmApp");
            if (app != null && !String.valueOf(app).isBlank()) {
                stream.setZlmApp(String.valueOf(app));
            }
            Object streamId = invite.get("streamId");
            if (streamId != null && !String.valueOf(streamId).isBlank()) {
                stream.setZlmStream(String.valueOf(streamId));
            }
            stream.setStatus("ON");
            streamRepository.update(stream);
        } catch (Exception e) {
            log.warn("[GB28181] 写入点播播放地址失败 deviceId={} err={}", stream.getDeviceId(), e.getMessage());
        }
    }
}
