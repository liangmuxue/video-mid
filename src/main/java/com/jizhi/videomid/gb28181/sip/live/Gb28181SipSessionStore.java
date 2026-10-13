package com.jizhi.videomid.gb28181.sip.live;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

@Component
@ConditionalOnProperty(name = "gb28181.data-source", havingValue = "live")
public class Gb28181SipSessionStore {

    private final ConcurrentHashMap<String, Gb28181SipSession> byDeviceId = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> expiredIds = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public void upsert(Gb28181SipSession session) {
        upsert(session, null);
    }

    /** 写入会话，并在同一把锁里执行上线回调，避免和注销改状态交错。 */
    public void upsert(Gb28181SipSession session, Consumer<String> whileRegistered) {
        if (session == null || session.getDeviceId() == null) {
            return;
        }
        String deviceId = session.getDeviceId().trim();
        synchronized (lock(deviceId)) {
            byDeviceId.put(deviceId, session);
            if (whileRegistered != null) {
                whileRegistered.accept(deviceId);
            }
        }
    }

    public void remove(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return;
        }
        String id = deviceId.trim();
        synchronized (lock(id)) {
            byDeviceId.remove(id);
        }
    }

    /**
     * 注销或过期后改设备状态。若这期间又注册上了，则不动设备表。
     */
    public void ifStillAbsent(String deviceId, Consumer<String> action) {
        if (deviceId == null || deviceId.isBlank() || action == null) {
            return;
        }
        String id = deviceId.trim();
        synchronized (lock(id)) {
            Gb28181SipSession current = byDeviceId.get(id);
            if (current != null && !current.isExpired()) {
                return;
            }
            action.accept(id);
        }
    }

    private Object lock(String deviceId) {
        return locks.computeIfAbsent(deviceId, key -> new Object());
    }

    /**
     * 下级 Keepalive / MESSAGE 时续期注册，避免只发心跳不重 REGISTER 时被误判过期下线。
     */
    public void renewRegistration(String deviceId, long expiresSeconds, Consumer<Gb28181SipSession> whileActive) {
        if (deviceId == null || deviceId.isBlank() || expiresSeconds <= 0) {
            return;
        }
        String id = deviceId.trim();
        synchronized (lock(id)) {
            Gb28181SipSession session = byDeviceId.get(id);
            if (session == null) {
                return;
            }
            session.refreshExpiry(expiresSeconds);
            if (whileActive != null) {
                whileActive.accept(session);
            }
        }
    }

    public Optional<Gb28181SipSession> findByDeviceId(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return Optional.empty();
        }
        String id = deviceId.trim();
        synchronized (lock(id)) {
            Gb28181SipSession s = byDeviceId.get(id);
            if (s == null || s.isExpired()) {
                if (s != null && byDeviceId.remove(id, s)) {
                    expiredIds.add(id);
                }
                return Optional.empty();
            }
            return Optional.of(s);
        }
    }

    public int activeCount() {
        purgeExpired();
        return byDeviceId.size();
    }

    public List<Map<String, Object>> listActive() {
        purgeExpired();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Gb28181SipSession s : byDeviceId.values()) {
            if (s.isExpired()) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("deviceId", s.getDeviceId());
            m.put("contactHost", s.getContactHost());
            m.put("contactPort", s.getContactPort());
            m.put("signalHost", s.signalHost());
            m.put("signalPort", s.signalPort());
            m.put("transport", s.getTransport());
            m.put("expiresSeconds", s.getExpiresSeconds());
            m.put("registeredAt", s.getRegisteredAt().toString());
            m.put("expiresAt", s.getExpiresAt().toString());
            m.put("rawContact", s.getRawContact());
            out.add(m);
        }
        return out;
    }

    /** 取出并清掉已过期的注册编号，供设备表改为不可用。 */
    public List<String> pollExpired() {
        purgeExpired();
        List<String> out = new ArrayList<>();
        String id;
        while ((id = expiredIds.poll()) != null) {
            out.add(id);
        }
        return out;
    }

    private void purgeExpired() {
        for (String id : new ArrayList<>(byDeviceId.keySet())) {
            synchronized (lock(id)) {
                Gb28181SipSession current = byDeviceId.get(id);
                if (current != null && current.isExpired() && byDeviceId.remove(id, current)) {
                    expiredIds.add(id);
                }
            }
        }
    }
}
