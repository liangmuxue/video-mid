package com.jizhi.videomid.gb28181.live;

import com.jizhi.videomid.device.AccessVendor;
import com.jizhi.videomid.device.Device;
import com.jizhi.videomid.device.DeviceRepository;
import com.jizhi.videomid.device.DeviceStatus;
import com.jizhi.videomid.device.DeviceStream;
import com.jizhi.videomid.device.DeviceStreamRepository;
import com.jizhi.videomid.device.DeviceFeature;
import com.jizhi.videomid.gb28181.Gb28181Ids;
import com.jizhi.videomid.gb28181.sip.live.Gb28181InviteSession;
import com.jizhi.videomid.gb28181.sip.live.Gb28181InviteSessionStore;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 国标注册后的设备入库。目录里的每个视频通道一条设备；同一通道再次注册只更新。
 * 已停用保持不动。其他平台占用了这个通道或设备编号时跳过。
 */
@Component
@ConditionalOnProperty(name = "gb28181.data-source", havingValue = "live")
public class Gb28181DeviceProvisioner {

    private static final Logger log = LoggerFactory.getLogger(Gb28181DeviceProvisioner.class);
    private static final long FALLBACK_DELAY_SECONDS = 20;

    private final DeviceRepository deviceRepository;
    private final DeviceStreamRepository streamRepository;
    private final Gb28181DeviceRegistry registry;
    private final Gb28181InviteSessionStore inviteSessionStore;
    private final TransactionTemplate transactionTemplate;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> fallbackPending = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "gb28181-device-provision");
        thread.setDaemon(true);
        return thread;
    });

    public Gb28181DeviceProvisioner(DeviceRepository deviceRepository,
                                    DeviceStreamRepository streamRepository,
                                    Gb28181DeviceRegistry registry,
                                    Gb28181InviteSessionStore inviteSessionStore,
                                    PlatformTransactionManager transactionManager) {
        this.deviceRepository = deviceRepository;
        this.streamRepository = streamRepository;
        this.registry = registry;
        this.inviteSessionStore = inviteSessionStore;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void provisionCatalog(String registerId, List<Map<String, String>> items) {
        String root = registerId == null ? "" : registerId.trim();
        List<Gb28181Ids.VideoChannel> videos = Gb28181Ids.videoChannels(items, root);
        String audio = videos.size() == 1 ? Gb28181Ids.firstAudioChannel(items) : null;
        for (Gb28181Ids.VideoChannel channel : videos) {
            upsert(channel.channelId(), root.isEmpty() ? channel.channelId() : root,
                    channel.name(), channel.manufacturer(), channel.model(), audio);
        }
    }

    /** 目录迟迟不来时，注册号本身是摄像机就建一条。 */
    public void scheduleRegisterFallback(String registerId) {
        if (!Gb28181Ids.isVideoChannel(registerId)) {
            return;
        }
        String id = registerId.trim();
        if (fallbackPending.putIfAbsent(id, Boolean.TRUE) != null) {
            return;
        }
        scheduler.schedule(() -> {
            fallbackPending.remove(id);
            try {
                upsert(id, id, null, null, null, null);
            } catch (Exception e) {
                log.warn("[GB28181] 注册兜底入库失败 deviceId={} err={}", id, e.getMessage());
            }
        }, FALLBACK_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    /** 注册仍在时把「不可用」拉回已启用。已停用不改。 */
    public void markOnline(String registerId) {
        if (registerId == null || registerId.isBlank()) {
            return;
        }
        String id = registerId.trim();
        for (Device device : deviceRepository.findGbByRegister(id)) {
            int status = DeviceStatus.normalize(device.getStatus());
            if (!DeviceStatus.isUnavailable(status)) {
                continue;
            }
            deviceRepository.updateStatus(device.getId(), DeviceStatus.ENABLED);
            log.info("[GB28181] 设备恢复已启用 deviceId={}", device.getDeviceId());
        }
    }

    public void markOffline(String registerId) {
        if (registerId == null || registerId.isBlank()) {
            return;
        }
        String id = registerId.trim();
        for (Device device : deviceRepository.findGbByRegister(id)) {
            int status = DeviceStatus.normalize(device.getStatus());
            if (!DeviceStatus.isEnabled(status)) {
                continue;
            }
            if (isLiveInviteActive(device)) {
                log.info("[GB28181] 注册过期但仍在点播，暂不下线 deviceId={}", device.getDeviceId());
                continue;
            }
            deviceRepository.updateStatus(device.getId(), DeviceStatus.UNAVAILABLE);
            log.info("[GB28181] 设备下线 deviceId={}", device.getDeviceId());
        }
    }

    private boolean isLiveInviteActive(Device device) {
        String channelId = streamRepository.findByDeviceIdAndType(device.getDeviceId(), "main")
                .map(DeviceStream::getChannelId)
                .filter(c -> c != null && !c.isBlank())
                .orElse(device.getDeviceId());
        return inviteSessionStore.findByChannelId(channelId)
                .map(s -> s.getStatus() == Gb28181InviteSession.Status.PENDING
                        || s.getStatus() == Gb28181InviteSession.Status.PLAYING)
                .orElse(false);
    }

    private void upsert(String channelId, String registerId, String name, String manufacturer, String model, String audioId) {
        String channel = channelId.trim();
        String register = registerId == null || registerId.isBlank() ? channel : registerId.trim();
        synchronized (locks.computeIfAbsent(channel, key -> new Object())) {
            try {
                transactionTemplate.executeWithoutResult(status ->
                        write(channel, register, name, manufacturer, model, audioId));
            } catch (DataIntegrityViolationException e) {
                transactionTemplate.executeWithoutResult(status ->
                        write(channel, register, name, manufacturer, model, audioId));
            }
            ensureRegistry(channel, register, name);
        }
    }

    private void write(String channelId, String registerId, String name, String manufacturer, String model, String audioId) {
        Optional<DeviceStream> owned = streamRepository.findByChannelId(channelId);
        if (owned.isPresent()) {
            Device owner = deviceRepository.findByDeviceId(owned.get().getDeviceId()).orElse(null);
            if (owner == null || !AccessVendor.GB28181.name().equals(owner.getVendor())) {
                log.warn("[GB28181] 通道已被其他平台占用，跳过 channelId={} deviceId={}",
                        channelId, owned.get().getDeviceId());
                return;
            }
            refresh(owner, registerId, name, manufacturer, model, audioId, channelId);
            ensureMainStream(owner.getDeviceId(), channelId, displayName(name, channelId));
            return;
        }
        Device preset = presetForRegister(registerId, channelId);
        if (preset != null) {
            refresh(preset, registerId, name, manufacturer, model, audioId, channelId);
            ensureMainStream(preset.getDeviceId(), channelId, displayName(name, channelId));
            return;
        }
        Optional<Device> existing = deviceRepository.findByDeviceId(channelId);
        if (existing.isPresent()) {
            if (!AccessVendor.GB28181.name().equals(existing.get().getVendor())) {
                log.warn("[GB28181] 设备编号已被其他平台占用，跳过 deviceId={}", channelId);
                return;
            }
            refresh(existing.get(), registerId, name, manufacturer, model, audioId, channelId);
            ensureMainStream(channelId, channelId, displayName(name, channelId));
            return;
        }
        Device created = new Device();
        created.setDeviceId(channelId);
        created.setName(displayName(name, channelId));
        created.setVendor(AccessVendor.GB28181.name());
        created.setGbRegisterId(registerId);
        created.setDeviceFeature(DeviceFeature.VIDEO);
        created.setStatus(DeviceStatus.ENABLED);
        created.setPtzType(0);
        created.setManufacturer(clip(manufacturer, 128));
        created.setModel(clip(model, 128));
        if (usableAudio(audioId, channelId, registerId, null)) {
            created.setGbAudioId(audioId.trim());
        }
        long id = deviceRepository.insert(created);
        created.setId(id);
        ensureMainStream(channelId, channelId, created.getName());
        log.info("[GB28181] 自动添加设备 deviceId={} registerId={} name={}", channelId, registerId, created.getName());
    }

    /** 预先添加的设备：注册号对上，且视频通道还没填或就是这次上报的通道。 */
    private Device presetForRegister(String registerId, String channelId) {
        for (Device device : deviceRepository.findGbByRegister(registerId)) {
            Optional<DeviceStream> main = streamRepository.findByDeviceIdAndType(device.getDeviceId(), "main");
            if (main.isEmpty()) {
                return device;
            }
            String existing = main.get().getChannelId();
            if (existing == null || existing.isBlank() || existing.equals(channelId)) {
                return device;
            }
        }
        return null;
    }

    private boolean usableAudio(String audioId, String channelId, String registerId, Long excludeId) {
        if (!Gb28181Ids.isAudioChannel(audioId)) {
            return false;
        }
        String audio = audioId.trim();
        if (audio.equals(channelId) || audio.equals(registerId)) {
            return false;
        }
        return deviceRepository.findOtherByGbAudio(audio, excludeId).isEmpty();
    }

    private void refresh(Device device, String registerId, String name, String manufacturer, String model, String audioId, String channelId) {
        boolean changed = false;
        String nextName = clip(name, 256);
        String currentName = device.getName();
        if (nextName != null && (currentName == null || currentName.isBlank() || currentName.equals(device.getDeviceId()))) {
            device.setName(nextName);
            changed = true;
        }
        String nextManufacturer = clip(manufacturer, 128);
        if (nextManufacturer != null && (device.getManufacturer() == null || device.getManufacturer().isBlank())) {
            device.setManufacturer(nextManufacturer);
            changed = true;
        }
        String nextModel = clip(model, 128);
        if (nextModel != null && (device.getModel() == null || device.getModel().isBlank())) {
            device.setModel(nextModel);
            changed = true;
        }
        if ((device.getGbAudioId() == null || device.getGbAudioId().isBlank())
                && usableAudio(audioId, channelId, registerId, device.getId())) {
            device.setGbAudioId(audioId.trim());
            changed = true;
        }
        if (registerId != null && !registerId.equals(device.getGbRegisterId())) {
            device.setGbRegisterId(registerId);
            changed = true;
        }
        int status = DeviceStatus.normalize(device.getStatus());
        if (!DeviceStatus.isDisabled(status) && !DeviceStatus.isEnabled(status)) {
            device.setStatus(DeviceStatus.ENABLED);
            changed = true;
            log.info("[GB28181] 设备恢复已启用 deviceId={}", device.getDeviceId());
        }
        if (changed) {
            deviceRepository.update(device);
        }
    }

    private void ensureMainStream(String deviceId, String channelId, String streamName) {
        Optional<DeviceStream> byChannel = streamRepository.findByChannelId(channelId);
        if (byChannel.isPresent()) {
            enableLiveIfMissing(deviceId, byChannel.get());
            return;
        }
        Optional<DeviceStream> main = streamRepository.findByDeviceIdAndType(deviceId, "main");
        if (main.isPresent()) {
            DeviceStream stream = main.get();
            if (stream.getChannelId() == null || stream.getChannelId().isBlank()) {
                stream.setChannelId(channelId);
                if (stream.getStreamName() == null || stream.getStreamName().isBlank()) {
                    stream.setStreamName(streamName);
                }
                streamRepository.update(stream);
            }
            enableLiveIfMissing(deviceId, stream);
            return;
        }
        DeviceStream stream = new DeviceStream();
        stream.setDeviceId(deviceId);
        stream.setStreamType("main");
        stream.setChannelId(channelId);
        stream.setStreamName(streamName);
        stream.setStatus("OFF");
        stream.setSortNo(1);
        stream.setLiveEnabled(true);
        streamRepository.insert(stream);
    }

    private void enableLiveIfMissing(String deviceId, DeviceStream stream) {
        if (Boolean.TRUE.equals(stream.getLiveEnabled())) {
            return;
        }
        if (streamRepository.findLiveByDeviceId(deviceId).isEmpty()) {
            streamRepository.setLive(stream.getId(), true);
        }
    }

    @SuppressWarnings("unchecked")
    private void ensureRegistry(String channelId, String registerId, String name) {
        if (registry.findChannel(channelId).isPresent()) {
            return;
        }
        Map<String, Object> channel = new LinkedHashMap<>();
        channel.put("channelId", channelId);
        channel.put("streamType", "main");
        channel.put("streamName", displayName(name, channelId));
        channel.put("gbStatus", "ON");

        Optional<Map<String, Object>> parent = registry.findByGbDeviceId(registerId);
        if (parent.isPresent()) {
            Map<String, Object> row = new LinkedHashMap<>(parent.get());
            List<Map<String, Object>> channels = new ArrayList<>();
            Object raw = row.get("channels");
            if (raw instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        channels.add(new LinkedHashMap<>((Map<String, Object>) map));
                    }
                }
            }
            channels.add(channel);
            row.put("channels", channels);
            row.put("gbDeviceId", registerId);
            registry.upsertDevice(row);
            return;
        }

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("deviceId", registerId);
        row.put("gbDeviceId", registerId);
        row.put("name", displayName(name, registerId));
        row.put("status", "ON");
        row.put("registered", true);
        row.put("mock", false);
        row.put("source", "gb-provision");
        row.put("channels", List.of(channel));
        registry.upsertDevice(row);
    }

    private static String displayName(String name, String fallback) {
        String clipped = clip(name, 256);
        return clipped == null ? fallback : clipped;
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
