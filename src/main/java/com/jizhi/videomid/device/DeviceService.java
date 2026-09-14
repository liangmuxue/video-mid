package com.jizhi.videomid.device;

import com.jizhi.videomid.device.dto.DeviceRequest;
import com.jizhi.videomid.device.dto.StreamRegisterRequest;
import com.jizhi.videomid.media.ZlmClient;
import com.jizhi.videomid.session.PreviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class DeviceService {

    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);

    private final DeviceRepository deviceRepository;
    private final DeviceStreamRepository streamRepository;
    private final PreviewService previewService;
    private final ZlmClient zlmClient;
    private final DeviceFolderService folderService;

    public DeviceService(DeviceRepository deviceRepository,
                         DeviceStreamRepository streamRepository,
                         PreviewService previewService,
                         ZlmClient zlmClient,
                         DeviceFolderService folderService) {
        this.deviceRepository = deviceRepository;
        this.streamRepository = streamRepository;
        this.previewService = previewService;
        this.zlmClient = zlmClient;
        this.folderService = folderService;
    }

    public Map<String, Object> getDevice(Long id) {
        Device d = deviceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在"));
        return buildDeviceDetail(d);
    }

    public Map<String, Object> getDeviceByDeviceId(String deviceId) {
        Device d = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在: " + deviceId));
        return buildDeviceDetail(d);
    }

    private Map<String, Object> buildDeviceDetail(Device d) {
        Map<String, Object> m = toDeviceView(d);
        List<Map<String, Object>> streams = streamRepository.findByDeviceId(d.getDeviceId())
                .stream().map(s -> {
                    Map<String, Object> sv = toStreamView(s);
                    sv.put("playCount", previewService.getRef(s.getDeviceId(), s.getStreamType()));
                    return sv;
                }).toList();
        m.put("streams", streams);
        m.put("streamCount", streams.size());
        return m;
    }

    public List<Map<String, Object>> listDevices() {
        return listDevices(null, false);
    }

    /**
     * @param folderId 目录 id；null 表示全部
     * @param includeChildren 是否包含子目录下设备
     */
    public List<Map<String, Object>> listDevices(Long folderId, boolean includeChildren) {
        Set<Long> folderIds = null;
        if (folderId != null) {
            if (includeChildren) {
                folderIds = folderService.collectSelfAndDescendantIds(folderId);
            } else {
                folderIds = Set.of(folderId);
            }
            if (folderIds.isEmpty()) {
                return List.of();
            }
        }
        List<Device> devices = deviceRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Device d : devices) {
            if (folderIds != null) {
                if (d.getFolderId() == null || !folderIds.contains(d.getFolderId())) {
                    continue;
                }
            }
            Map<String, Object> m = toDeviceView(d);
            int count = streamRepository.findByDeviceId(d.getDeviceId()).size();
            m.put("streamCount", count);
            result.add(m);
        }
        return result;
    }

    /** 对外查询：按 name / deviceId 模糊或精确筛选（均可选） */
    public List<Map<String, Object>> searchDevices(String name, String deviceId) {
        String nameQ = name == null ? null : name.trim().toLowerCase();
        String idQ = deviceId == null ? null : deviceId.trim().toLowerCase();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Device d : deviceRepository.findAll()) {
            if (nameQ != null && !nameQ.isEmpty()) {
                String n = d.getName() == null ? "" : d.getName().toLowerCase();
                if (!n.contains(nameQ)) {
                    continue;
                }
            }
            if (idQ != null && !idQ.isEmpty()) {
                String id = d.getDeviceId() == null ? "" : d.getDeviceId().toLowerCase();
                if (!id.contains(idQ)) {
                    continue;
                }
            }
            Map<String, Object> m = toDeviceView(d);
            m.put("streamCount", streamRepository.findByDeviceId(d.getDeviceId()).size());
            result.add(m);
        }
        return result;
    }

    /** 对外查询：某设备下全部码流 */
    public List<Map<String, Object>> listStreamsByDeviceId(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId 不能为空");
        }
        String id = deviceId.trim();
        deviceRepository.findByDeviceId(id)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在: " + id));
        return streamRepository.findByDeviceId(id).stream().map(s -> {
            Map<String, Object> sv = toStreamView(s);
            sv.put("playCount", previewService.getRef(s.getDeviceId(), s.getStreamType()));
            return sv;
        }).toList();
    }

    @Transactional
    public Map<String, Object> createDevice(DeviceRequest req) {
        if (deviceRepository.findByDeviceId(req.getDeviceId().trim()).isPresent()) {
            throw new IllegalArgumentException("deviceId 已存在");
        }
        Device d = fromDeviceRequest(req);
        long id = deviceRepository.insert(d);
        d.setId(id);
        return toDeviceView(d);
    }

    @Transactional
    public Map<String, Object> updateDevice(Long id, DeviceRequest req) {
        Device existing = deviceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在"));
        Device d = fromDeviceRequest(req);
        d.setId(existing.getId());
        d.setDeviceId(existing.getDeviceId());
        deviceRepository.update(d);
        return toDeviceView(deviceRepository.findById(id).orElse(d));
    }

    @Transactional
    public void deleteDevice(Long id) {
        Device d = deviceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在"));
        streamRepository.deleteByDeviceId(d.getDeviceId());
        deviceRepository.deleteById(id);
    }

    /**
     * 码流注册：写入 stream_url 到 MySQL；设备不存在则自动创建。
     */
    @Transactional
    public Map<String, Object> registerStream(StreamRegisterRequest req) {
        String type = req.getStreamType().trim().toLowerCase();
        if (!"main".equals(type) && !"sub".equals(type)) {
            throw new IllegalArgumentException("streamType 必须是 main 或 sub");
        }
        String deviceId = req.getDeviceId().trim();

        Optional<Device> deviceOpt = deviceRepository.findByDeviceId(deviceId);
        if (deviceOpt.isEmpty()) {
            Device d = new Device();
            d.setDeviceId(deviceId);
            d.setName(req.getDeviceName() == null || req.getDeviceName().isBlank() ? deviceId : req.getDeviceName());
            d.setStatus(DeviceStatus.ENABLED);
            d.setPtzType(0);
            deviceRepository.insert(d);
        } else if (req.getDeviceName() != null && !req.getDeviceName().isBlank()) {
            Device d = deviceOpt.get();
            d.setName(req.getDeviceName());
            // 不覆盖人工「已停用」；仅补写名称
            deviceRepository.update(d);
        }

        Optional<DeviceStream> existing = streamRepository.findByDeviceIdAndType(deviceId, type);
        DeviceStream s;
        if (existing.isPresent()) {
            s = existing.get();
            s.setStreamUrl(req.getStreamUrl().trim());
            s.setStreamName(req.getStreamName());
            s.setChannelId(req.getChannelId());
            s.setStatus(req.getStatus() == null || req.getStatus().isBlank() ? "ON" : req.getStatus());
            if (req.getSortNo() != null) s.setSortNo(req.getSortNo());
            if (req.getLiveEnabled() != null) s.setLiveEnabled(req.getLiveEnabled());
            streamRepository.update(s);
        } else {
            s = new DeviceStream();
            s.setDeviceId(deviceId);
            s.setStreamType(type);
            s.setStreamUrl(req.getStreamUrl().trim());
            s.setStreamName(req.getStreamName());
            s.setChannelId(req.getChannelId());
            s.setStatus(req.getStatus() == null || req.getStatus().isBlank() ? "ON" : req.getStatus());
            s.setSortNo(req.getSortNo() == null ? 0 : req.getSortNo());
            s.setLiveEnabled(Boolean.TRUE.equals(req.getLiveEnabled()));
            long id = streamRepository.insert(s);
            s.setId(id);
        }
        // 显式指定直播，或该设备尚无直播流时按默认规则（优先 sub）
        if (Boolean.TRUE.equals(req.getLiveEnabled())) {
            setLiveStream(s.getId());
            s = streamRepository.findById(s.getId()).orElse(s);
        } else {
            ensureDefaultLiveStream(deviceId);
            s = streamRepository.findById(s.getId()).orElse(s);
        }
        Map<String, Object> view = toStreamView(s);
        view.put("playCount", previewService.getRef(deviceId, type));
        return view;
    }

    /**
     * 将指定码流设为业务端直播流（同设备互斥）。
     */
    @Transactional
    public Map<String, Object> setLiveStream(Long streamId) {
        DeviceStream s = streamRepository.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("码流不存在"));
        streamRepository.clearLiveByDeviceId(s.getDeviceId());
        streamRepository.setLive(streamId, true);
        s.setLiveEnabled(true);
        Map<String, Object> view = toStreamView(s);
        view.put("playCount", previewService.getRef(s.getDeviceId(), s.getStreamType()));
        return view;
    }

    /**
     * 解析设备业务端直播流：已标记优先；否则默认 sub，再退 main，并写回标记。
     */
    public Optional<DeviceStream> resolveLiveStream(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return Optional.empty();
        }
        String id = deviceId.trim();
        Optional<DeviceStream> marked = streamRepository.findLiveByDeviceId(id);
        if (marked.isPresent()) {
            return marked;
        }
        Optional<DeviceStream> sub = streamRepository.findByDeviceIdAndType(id, "sub");
        if (sub.isPresent()) {
            setLiveStream(sub.get().getId());
            return streamRepository.findById(sub.get().getId());
        }
        Optional<DeviceStream> main = streamRepository.findByDeviceIdAndType(id, "main");
        if (main.isPresent()) {
            setLiveStream(main.get().getId());
            return streamRepository.findById(main.get().getId());
        }
        return Optional.empty();
    }

    /** 业务端：按 live 码流开播 */
    public Map<String, Object> startBizLive(String deviceId) {
        DeviceStream stream = resolveLiveStream(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("设备未配置可直播码流"));
        return previewService.start(deviceId, stream.getStreamType());
    }

    private void ensureDefaultLiveStream(String deviceId) {
        if (streamRepository.findLiveByDeviceId(deviceId).isPresent()) {
            return;
        }
        resolveLiveStream(deviceId);
    }

    @Transactional
    public void deleteStream(Long id) {
        DeviceStream s = streamRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("码流不存在"));
        String deviceId = s.getDeviceId();
        boolean wasLive = Boolean.TRUE.equals(s.getLiveEnabled());
        streamRepository.deleteById(id);
        if (wasLive) {
            ensureDefaultLiveStream(deviceId);
        }
    }

    private Device fromDeviceRequest(DeviceRequest req) {
        Device d = new Device();
        d.setDeviceId(req.getDeviceId().trim());
        d.setName(req.getName());
        d.setPlatformId(req.getPlatformId());
        d.setFolderId(req.getFolderId());
        // 人工仅可设 已启用 / 已停用
        d.setStatus(DeviceStatus.normalizeManual(req.getStatus()));
        d.setManufacturer(req.getManufacturer());
        d.setModel(req.getModel());
        d.setAddress(req.getAddress());
        d.setPtzType(req.getPtzType() == null ? 0 : req.getPtzType());
        d.setGatewayId(req.getGatewayId());
        d.setLongitude(req.getLongitude());
        d.setLatitude(req.getLatitude());
        return d;
    }

    /**
     * 定时巡检：非「已停用」设备按 ZLM 推流在线情况切换 已启用 / 不可用。
     * ZLM 接口失败时跳过该设备，避免误杀。
     */
    public void reconcilePushStatus() {
        List<Device> devices = deviceRepository.findAll();
        int changed = 0;
        for (Device d : devices) {
            String current = DeviceStatus.normalize(d.getStatus());
            if (DeviceStatus.DISABLED.equals(current)) {
                continue;
            }
            Boolean online = isDevicePushing(d.getDeviceId());
            if (online == null) {
                continue;
            }
            String target = online ? DeviceStatus.ENABLED : DeviceStatus.UNAVAILABLE;
            if (!target.equals(current)) {
                deviceRepository.updateStatus(d.getId(), target);
                changed++;
                log.info("设备推流巡检 deviceId={} {} -> {}", d.getDeviceId(), current, target);
            }
        }
        if (changed > 0) {
            log.info("设备推流巡检完成，更新 {} 台", changed);
        }
    }

    /**
     * @return true 至少一路推流在线；false 全部离线或无码流；null ZLM 查询失败
     */
    private Boolean isDevicePushing(String deviceId) {
        List<DeviceStream> streams = streamRepository.findByDeviceId(deviceId);
        if (streams == null || streams.isEmpty()) {
            return false;
        }
        boolean sawQuery = false;
        boolean anyOnline = false;
        for (DeviceStream s : streams) {
            PreviewService.AppStream as = PreviewService.parseAppStream(s.getStreamUrl());
            if (as == null) {
                continue;
            }
            Optional<Boolean> online = zlmClient.isMediaOnline(as.app(), as.stream());
            if (online.isEmpty()) {
                continue;
            }
            sawQuery = true;
            if (Boolean.TRUE.equals(online.get())) {
                anyOnline = true;
                break;
            }
        }
        if (!sawQuery) {
            // 有码流但 URL 无法解析，或 ZLM 全部失败
            boolean hasUrl = streams.stream().anyMatch(s -> s.getStreamUrl() != null && !s.getStreamUrl().isBlank());
            if (!hasUrl) {
                return false;
            }
            return null;
        }
        return anyOnline;
    }

    private Map<String, Object> toDeviceView(Device d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("deviceId", d.getDeviceId());
        m.put("name", d.getName());
        m.put("platformId", d.getPlatformId());
        m.put("folderId", d.getFolderId());
        m.put("status", DeviceStatus.normalize(d.getStatus()));
        m.put("manufacturer", d.getManufacturer());
        m.put("model", d.getModel());
        m.put("address", d.getAddress());
        m.put("ptzType", d.getPtzType());
        m.put("gatewayId", d.getGatewayId());
        m.put("longitude", d.getLongitude());
        m.put("latitude", d.getLatitude());
        m.put("createdAt", d.getCreatedAt());
        m.put("updatedAt", d.getUpdatedAt());
        return m;
    }

    private Map<String, Object> toStreamView(DeviceStream s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("deviceId", s.getDeviceId());
        m.put("streamType", s.getStreamType());
        m.put("channelId", s.getChannelId());
        m.put("streamUrl", s.getStreamUrl());
        m.put("streamName", s.getStreamName());
        m.put("status", s.getStatus());
        m.put("sortNo", s.getSortNo());
        m.put("liveEnabled", Boolean.TRUE.equals(s.getLiveEnabled()));
        m.put("createdAt", s.getCreatedAt());
        m.put("updatedAt", s.getUpdatedAt());
        return m;
    }
}
