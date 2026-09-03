package com.jizhi.videomid.device.service;

import com.jizhi.videomid.device.entity.DeviceEntity;
import com.jizhi.videomid.device.entity.SipPlatformEntity;
import com.jizhi.videomid.device.repository.DeviceRepository;
import com.jizhi.videomid.device.repository.SipPlatformRepository;
import com.jizhi.videomid.session.RedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 设备业务模块：全部查询走 MySQL，在线心跳临时标记可写 Redis device:online。
 */
@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final SipPlatformRepository sipPlatformRepository;
    private final StringRedisTemplate redis;

    public DeviceService(DeviceRepository deviceRepository,
                         SipPlatformRepository sipPlatformRepository,
                         StringRedisTemplate redis) {
        this.deviceRepository = deviceRepository;
        this.sipPlatformRepository = sipPlatformRepository;
        this.redis = redis;
    }

    public List<DeviceEntity> listAll() {
        return deviceRepository.findAllByOrderByDeviceIdAsc();
    }

    public Optional<DeviceEntity> findByDeviceId(String deviceId) {
        return deviceRepository.findByDeviceId(deviceId);
    }

    /**
     * 解析点播用的实际通道 ID：预览默认子码流。
     */
    public String resolveChannelId(String deviceId, String streamType) {
        DeviceEntity device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在: " + deviceId));
        String type = streamType == null || streamType.isBlank() ? "sub" : streamType.toLowerCase();
        if ("main".equals(type)) {
            if (device.getMainChannelId() != null && !device.getMainChannelId().isBlank()) {
                return device.getMainChannelId();
            }
            return device.getDeviceId();
        }
        if (device.getSubChannelId() != null && !device.getSubChannelId().isBlank()) {
            return device.getSubChannelId();
        }
        return device.getDeviceId();
    }

    @Transactional
    public void markPlatformOnline(String platformId, String ip, int port, String transport, String domain) {
        SipPlatformEntity platform = sipPlatformRepository.findByPlatformId(platformId)
                .orElseGet(SipPlatformEntity::new);
        platform.setPlatformId(platformId);
        platform.setIp(ip);
        platform.setPort(port);
        platform.setTransport(transport);
        platform.setDomain(domain);
        platform.setStatus("ONLINE");
        platform.setLastRegisterAt(LocalDateTime.now());
        platform.setLastKeepaliveAt(LocalDateTime.now());
        sipPlatformRepository.save(platform);
    }

    @Transactional
    public void markPlatformOffline(String platformId) {
        sipPlatformRepository.findByPlatformId(platformId).ifPresent(p -> {
            p.setStatus("OFFLINE");
            sipPlatformRepository.save(p);
        });
    }

    @Transactional
    public void keepalive(String platformId) {
        sipPlatformRepository.findByPlatformId(platformId).ifPresent(p -> {
            p.setLastKeepaliveAt(LocalDateTime.now());
            p.setStatus("ONLINE");
            sipPlatformRepository.save(p);
        });
        // 运行时在线标记（临时），非业务主数据
        redis.opsForValue().set(RedisKeys.deviceOnline(platformId), "1", Duration.ofSeconds(120));
    }

    public Optional<SipPlatformEntity> findPlatform(String platformId) {
        return sipPlatformRepository.findByPlatformId(platformId);
    }

    public Optional<SipPlatformEntity> findAnyOnlinePlatform() {
        return sipPlatformRepository.findByStatus("ONLINE").stream().findFirst();
    }

    public boolean isOnline(String deviceId) {
        String v = redis.opsForValue().get(RedisKeys.deviceOnline(deviceId));
        if ("1".equals(v)) {
            return true;
        }
        return deviceRepository.findByDeviceId(deviceId)
                .map(d -> "ON".equalsIgnoreCase(d.getStatus()))
                .orElse(false);
    }
}
