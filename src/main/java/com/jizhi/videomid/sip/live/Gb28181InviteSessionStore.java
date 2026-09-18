package com.jizhi.videomid.sip.live;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "gb28181.data-source", havingValue = "live")
public class Gb28181InviteSessionStore {

    private final ConcurrentHashMap<String, Gb28181InviteSession> byChannel = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Gb28181InviteSession> byCallId = new ConcurrentHashMap<>();

    public Gb28181InviteSession register(Gb28181InviteSession session) {
        byChannel.put(session.getChannelId(), session);
        byCallId.put(session.getCallId(), session);
        return session;
    }

    public Optional<Gb28181InviteSession> findByChannelId(String channelId) {
        if (channelId == null || channelId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byChannel.get(channelId.trim()));
    }

    public Optional<Gb28181InviteSession> findByCallId(String callId) {
        if (callId == null || callId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byCallId.get(callId.trim()));
    }

    public void remove(String channelId) {
        if (channelId == null) {
            return;
        }
        Gb28181InviteSession s = byChannel.remove(channelId.trim());
        if (s != null) {
            byCallId.remove(s.getCallId());
        }
    }

    public List<Map<String, Object>> listAll() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Gb28181InviteSession s : byChannel.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("channelId", s.getChannelId());
            m.put("gbDeviceId", s.getGbDeviceId());
            m.put("callId", s.getCallId());
            m.put("rtpPort", s.getRtpPort());
            m.put("streamId", s.getStreamId());
            m.put("ssrc", s.getSsrc());
            m.put("status", s.getStatus().name());
            m.put("failReason", s.getFailReason());
            m.put("createdAt", s.getCreatedAt().toString());
            out.add(m);
        }
        return out;
    }
}
