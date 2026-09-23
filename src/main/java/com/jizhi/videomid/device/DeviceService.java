package com.jizhi.videomid.device;

import com.jizhi.videomid.device.dto.DeviceRequest;
import com.jizhi.videomid.device.dto.StreamRegisterRequest;
import com.jizhi.videomid.gb28181.Gb28181PlayService;
import com.jizhi.videomid.media.ZlmClient;
import com.jizhi.videomid.session.PreviewService;
import com.jizhi.videomid.uniview.live.UniviewStreamDiscovery;
import com.jizhi.videomid.uniview.live.UniviewStreamIds;
import com.jizhi.videomid.uniview.live.UniviewVideoStream;
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
    private final Gb28181PlayService gb28181PlayService;
    private final UniviewStreamDiscovery streamDiscovery;

    public DeviceService(DeviceRepository deviceRepository,
                         DeviceStreamRepository streamRepository,
                         PreviewService previewService,
                         ZlmClient zlmClient,
                         DeviceFolderService folderService,
                         Gb28181PlayService gb28181PlayService,
                         UniviewStreamDiscovery streamDiscovery) {
        this.deviceRepository = deviceRepository;
        this.streamRepository = streamRepository;
        this.previewService = previewService;
        this.zlmClient = zlmClient;
        this.folderService = folderService;
        this.gb28181PlayService = gb28181PlayService;
        this.streamDiscovery = streamDiscovery;
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
        assertLoginFree(d, null);
        long id = deviceRepository.insert(d);
        d.setId(id);
        syncUniviewStreams(d);
        return toDeviceView(d);
    }

    @Transactional
    public Map<String, Object> updateDevice(Long id, DeviceRequest req) {
        Device existing = deviceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在"));
        Device d = fromDeviceRequest(req);
        d.setId(existing.getId());
        d.setDeviceId(existing.getDeviceId());
        if (d.getHost() == null) {
            d.setPassword(null);
            d.setUsername(null);
            d.setPort(null);
            d.setAccessChannel(null);
        } else if (d.getPassword() == null || d.getPassword().isBlank()) {
            d.setPassword(existing.getPassword());
        }
        boolean loginChanged = !sameText(existing.getHost(), d.getHost())
                || !Objects.equals(existing.getPort(), d.getPort())
                || !sameText(existing.getUsername(), d.getUsername())
                || !sameText(existing.getAccessChannel(), d.getAccessChannel())
                || (req.getPassword() != null && !req.getPassword().isBlank());
        if (loginChanged) {
            d.setAccessStatus("unknown");
            d.setAccessError(null);
        } else {
            d.setAccessStatus(existing.getAccessStatus());
            d.setAccessError(existing.getAccessError());
        }
        assertLoginFree(d, existing.getId());
        deviceRepository.update(d);
        if (hasUniviewLogin(d)) {
            syncUniviewStreams(d);
        } else {
            removePullStreams(d.getDeviceId());
        }
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
        Optional<DeviceStream> found = LiveStreamSupport.resolveByPriority(streamRepository, deviceId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        DeviceStream s = found.get();
        if (!Boolean.TRUE.equals(s.getLiveEnabled())) {
            setLiveStream(s.getId());
            return streamRepository.findById(s.getId());
        }
        return found;
    }

    /** 业务端：宇视设备按观看拉流；其余仍优先国标 INVITE，否则走已注册 streamUrl */
    public Map<String, Object> startBizLive(String deviceId) {
        Optional<Device> device = deviceRepository.findByDeviceId(deviceId);
        if (device.isPresent() && hasUniviewLogin(device.get())) {
            DeviceStream stream = resolveLiveStream(deviceId)
                    .orElseThrow(() -> new IllegalArgumentException("设备未配置可直播码流"));
            return previewService.start(deviceId, stream.getStreamType());
        }
        if (gb28181PlayService.preferForBizLive()) {
            Optional<Map<String, Object>> gb = gb28181PlayService.startLive(deviceId);
            if (gb.isPresent()) {
                return gb.get();
            }
        }
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
        d.setStatus(DeviceStatus.normalizeManual(req.getStatus() == null ? DeviceStatus.ENABLED : req.getStatus()));
        d.setManufacturer(req.getManufacturer());
        d.setModel(req.getModel());
        d.setAddress(req.getAddress());
        d.setPtzType(req.getPtzType() == null ? 0 : req.getPtzType());
        d.setGatewayId(req.getGatewayId());
        d.setLongitude(req.getLongitude());
        d.setLatitude(req.getLatitude());
        d.setHost(blank(req.getHost()));
        d.setPort(req.getPort());
        d.setUsername(blank(req.getUsername()));
        d.setPassword(req.getPassword());
        if (d.getHost() == null) {
            d.setPort(null);
            d.setUsername(null);
            d.setPassword(null);
            d.setAccessChannel(null);
        } else {
            String channel = blank(req.getAccessChannel());
            d.setAccessChannel(channel == null ? "0" : channel);
            if (d.getPort() == null) {
                d.setPort(80);
            }
        }
        d.setAccessStatus("unknown");
        return d;
    }

    private void assertLoginFree(Device d, Long excludeId) {
        if (!hasUniviewLogin(d) || d.getPort() == null) {
            return;
        }
        deviceRepository.findOtherByLogin(d.getHost(), d.getPort(), d.getAccessChannel(), excludeId)
                .ifPresent(other -> {
                    throw new IllegalArgumentException("该 IP、端口和通道已被设备 " + other.getDeviceId() + " 使用");
                });
    }

    /** 按摄像机当前启用的码流重写码流表：有的更新，多的补上，没有的删掉。 */
    private void syncUniviewStreams(Device d) {
        List<UniviewVideoStream> streams = streamDiscovery.listEnabled(d);
        deviceRepository.updateAccess(d.getId(), "online", null);
        d.setAccessStatus("online");
        d.setAccessError(null);
        Set<String> keep = new HashSet<>();
        for (UniviewVideoStream stream : streams) {
            keep.add(stream.streamType());
            upsertUniviewStream(d, stream);
        }
        for (DeviceStream existing : streamRepository.findByDeviceId(d.getDeviceId())) {
            if (!UniviewStreamIds.isPull(existing) || keep.contains(existing.getStreamType())) {
                continue;
            }
            zlmClient.delStreamProxy(existing.getZlmApp(), existing.getZlmStream());
            streamRepository.deleteById(existing.getId());
        }
        if (streamRepository.findLiveByDeviceId(d.getDeviceId()).isEmpty()) {
            String liveType = "sub";
            if (streams.stream().noneMatch(s -> "sub".equals(s.streamType()))) {
                liveType = streams.stream().anyMatch(s -> "main".equals(s.streamType()))
                        ? "main" : streams.get(0).streamType();
            }
            streamRepository.findByDeviceIdAndType(d.getDeviceId(), liveType)
                    .ifPresent(s -> setLiveStream(s.getId()));
        }
    }

    private void removePullStreams(String deviceId) {
        for (DeviceStream existing : streamRepository.findByDeviceId(deviceId)) {
            if (!UniviewStreamIds.isPull(existing)) {
                continue;
            }
            zlmClient.delStreamProxy(existing.getZlmApp(), existing.getZlmStream());
            streamRepository.deleteById(existing.getId());
        }
    }

    private void upsertUniviewStream(Device d, UniviewVideoStream stream) {
        String zlmStream = UniviewStreamIds.zlmStream(d.getDeviceId(), stream.streamType());
        String playUrl = UniviewStreamIds.playUrl(zlmClient.mediaBaseUrl(), zlmStream);
        Optional<DeviceStream> existing = streamRepository.findByDeviceIdAndType(d.getDeviceId(), stream.streamType());
        if (existing.isPresent()) {
            DeviceStream s = existing.get();
            s.setStreamName(stream.streamName());
            s.setStreamIndex(stream.id());
            s.setSortNo(stream.id() + 1);
            s.setZlmApp(UniviewStreamIds.APP);
            s.setZlmStream(zlmStream);
            s.setStreamUrl(playUrl);
            streamRepository.update(s);
            return;
        }
        DeviceStream s = new DeviceStream();
        s.setDeviceId(d.getDeviceId());
        s.setStreamType(stream.streamType());
        s.setStreamName(stream.streamName());
        s.setStreamUrl(playUrl);
        s.setStatus("OFF");
        s.setSortNo(stream.id() + 1);
        s.setLiveEnabled(false);
        s.setStreamIndex(stream.id());
        s.setZlmApp(UniviewStreamIds.APP);
        s.setZlmStream(zlmStream);
        streamRepository.insert(s);
    }

    private static boolean hasUniviewLogin(Device d) {
        return d.getHost() != null && !d.getHost().isBlank();
    }

    private static String blank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static boolean sameText(String a, String b) {
        String left = a == null ? "" : a.trim();
        String right = b == null ? "" : b.trim();
        return left.equals(right);
    }

    /**
     * 定时巡检：非「已停用」设备按 ZLM 推流在线情况切换 已启用 / 不可用。
     * ZLM 接口失败时跳过该设备，避免误杀。
     */
    public void reconcilePushStatus() {
        List<Device> devices = deviceRepository.findAll();
        int changed = 0;
        for (Device d : devices) {
            int current = DeviceStatus.normalize(d.getStatus());
            if (DeviceStatus.isDisabled(current)) {
                continue;
            }
            if (hasUniviewLogin(d)) {
                continue;
            }
            Boolean online = isDevicePushing(d.getDeviceId());
            if (online == null) {
                continue;
            }
            int target = online ? DeviceStatus.ENABLED : DeviceStatus.UNAVAILABLE;
            if (target != current) {
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
        m.put("host", d.getHost());
        m.put("port", d.getPort());
        m.put("username", d.getUsername());
        m.put("passwordSet", d.getPassword() != null && !d.getPassword().isBlank());
        m.put("accessChannel", d.getAccessChannel());
        m.put("accessStatus", d.getAccessStatus() == null ? "unknown" : d.getAccessStatus());
        m.put("accessError", d.getAccessError());
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
        m.put("streamIndex", s.getStreamIndex());
        m.put("zlmApp", s.getZlmApp());
        m.put("zlmStream", s.getZlmStream());
        m.put("createdAt", s.getCreatedAt());
        m.put("updatedAt", s.getUpdatedAt());
        return m;
    }
}
