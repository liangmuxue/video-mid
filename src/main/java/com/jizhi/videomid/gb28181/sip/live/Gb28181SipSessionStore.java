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

    public Optional<Gb28181SipSession> findByDeviceId(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return Optional.empty();
        }
        Gb28181SipSession s = byDeviceId.get(deviceId.trim());
        if (s == null || s.isExpired()) {
            if (s != null && byDeviceId.remove(deviceId.trim(), s)) {
                expiredIds.add(deviceId.trim());
            }
            return Optional.empty();
        }
        return Optional.of(s);
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
        for (Map.Entry<String, Gb28181SipSession> entry : byDeviceId.entrySet()) {
            if (entry.getValue().isExpired() && byDeviceId.remove(entry.getKey(), entry.getValue())) {
                expiredIds.add(entry.getKey());
            }
        }
    }
}
