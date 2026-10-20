package com.jizhi.videomid.device;

import com.jizhi.videomid.device.dto.DeviceRequest;
import com.jizhi.videomid.device.dto.StreamRegisterRequest;
import com.jizhi.videomid.gb28181.Gb28181Ids;
import com.jizhi.videomid.gb28181.Gb28181PlayService;
import com.jizhi.videomid.integration.jizhiai.JizhiAiDeviceSyncClient;
import com.jizhi.videomid.media.ZlmClient;
import com.jizhi.videomid.rtsp.RtspPullService;
import com.jizhi.videomid.session.PreviewService;
import com.jizhi.videomid.uniview.cloud.UniviewCloudDeviceIds;
import com.jizhi.videomid.uniview.cloud.UniviewCloudPlayService;
import com.jizhi.videomid.uniview.cloud.UniviewCloudSyncService;
import com.jizhi.videomid.uniview.live.UniviewStreamDiscovery;
import com.jizhi.videomid.uniview.live.UniviewStreamIds;
import com.jizhi.videomid.uniview.live.UniviewVideoStream;
import com.jizhi.videomid.uniview.nvr.NvrRecordingService;
import com.jizhi.videomid.uniview.nvr.RecordDevice;
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
    private final NvrRecordingService nvrRecordingService;
    private final LiveStreamRtspResolver liveStreamRtspResolver;
    private final JizhiAiDeviceSyncClient jizhiAiDeviceSyncClient;
    private final RtspPullService rtspPullService;
    private final UniviewCloudPlayService univiewCloudPlayService;

    public DeviceService(DeviceRepository deviceRepository,
                         DeviceStreamRepository streamRepository,
                         PreviewService previewService,
                         ZlmClient zlmClient,
                         DeviceFolderService folderService,
                         Gb28181PlayService gb28181PlayService,
                         UniviewStreamDiscovery streamDiscovery,
                         NvrRecordingService nvrRecordingService,
                         LiveStreamRtspResolver liveStreamRtspResolver,
                         JizhiAiDeviceSyncClient jizhiAiDeviceSyncClient,
                         RtspPullService rtspPullService,
                         UniviewCloudPlayService univiewCloudPlayService) {
        this.deviceRepository = deviceRepository;
        this.streamRepository = streamRepository;
        this.previewService = previewService;
        this.zlmClient = zlmClient;
        this.folderService = folderService;
        this.gb28181PlayService = gb28181PlayService;
        this.streamDiscovery = streamDiscovery;
        this.nvrRecordingService = nvrRecordingService;
        this.liveStreamRtspResolver = liveStreamRtspResolver;
        this.jizhiAiDeviceSyncClient = jizhiAiDeviceSyncClient;
        this.rtspPullService = rtspPullService;
        this.univiewCloudPlayService = univiewCloudPlayService;
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
        m.put("liveStream", liveStreamForDevice(d.getDeviceId()));
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
            m.put("liveStream", liveStreamForDevice(d.getDeviceId()));
            result.add(m);
        }
        return result;
    }

    /**
     * 业务端目录。码流一次查出再挂到 liveStream，不逐台访问摄像机。
     */
    public List<Map<String, Object>> listBizCatalog(Long folderId, boolean includeChildren) {
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
        Map<String, List<DeviceStream>> streamsByDevice = new HashMap<>();
        for (DeviceStream stream : streamRepository.findAll()) {
            if (stream.getDeviceId() == null) {
                continue;
            }
            streamsByDevice.computeIfAbsent(stream.getDeviceId(), key -> new ArrayList<>()).add(stream);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Device d : deviceRepository.findAll()) {
            if (folderIds != null && (d.getFolderId() == null || !folderIds.contains(d.getFolderId()))) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("deviceId", d.getDeviceId());
            m.put("deviceType", DeviceType.normalize(d.getDeviceType()));
            m.put("deviceFeature", DeviceFeature.normalize(d.getDeviceFeature()));
            m.put("keys", DeviceFeature.capabilityKeys(d.getDeviceFeature()));
            m.put("name", d.getName());
            m.put("address", d.getAddress());
            m.put("folderId", d.getFolderId());
            m.put("status", DeviceStatus.normalize(d.getStatus()));
            m.put("liveStream", liveStreamForList(d, streamsByDevice.get(d.getDeviceId())));
            result.add(m);
        }
        return result;
    }

    private Map<String, Object> liveStreamForList(Device device, List<DeviceStream> streams) {
        if (VendorDevices.of(device) == AccessVendor.UNIVIEW_CLOUD) {
            if (!DeviceStatus.isEnabled(device.getStatus())) {
                return storedLiveStream(streams);
            }
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("streamType", "main");
            view.put("streamUrl", univiewCloudPlayService.listedPlayUrl(device.getDeviceId()));
            return view;
        }
        return storedLiveStream(streams);
    }

    /** 与设备详情的 liveStream 相同：streamType + streamUrl。占位地址不当成播放地址。 */
    private Map<String, Object> storedLiveStream(List<DeviceStream> streams) {
        DeviceStream live = LiveStreamSupport.pick(streams).orElse(null);
        if (live == null) {
            return null;
        }
        String stored = live.getStreamUrl();
        String url = null;
        if (LiveStreamRtspResolver.isRtsp(stored)) {
            url = stored.trim();
        } else if (stored != null && !UniviewCloudSyncService.STREAM_PLACEHOLDER.equals(stored.trim())) {
            url = LiveStreamRtspResolver.zlmProxyRtsp(live, stored);
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("streamType", live.getStreamType());
        view.put("streamUrl", url);
        return view;
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
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("deviceId", d.getDeviceId());
            m.put("name", d.getName());
            m.put("status", DeviceStatus.normalize(d.getStatus()));
            m.put("address", d.getAddress());
            m.put("folderId", d.getFolderId());
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
        if (AccessVendor.GB28181.name().equals(d.getVendor())) {
            prepareGb(d, req, null);
        }
        assertLoginFree(d, null);
        bindRecord(d, null);
        long id = deviceRepository.insert(d);
        d.setId(id);
        deviceRepository.saveUniview(d);
        if (hasUniviewLogin(d)) {
            syncUniviewStreams(d);
        } else if (AccessVendor.RTSP.name().equals(d.getVendor())) {
            rtspPullService.sync(d, req.getPullUrl());
        } else if (AccessVendor.GB28181.name().equals(d.getVendor())) {
            saveGbVideoChannel(d.getDeviceId(), req.getVideoChannelId(), d.getName());
        }
        Device persisted = deviceRepository.findById(id).orElse(d);
        notifyJizhiAiSave(persisted);
        return toDeviceView(persisted);
    }

    @Transactional
    public Map<String, Object> updateDevice(Long id, DeviceRequest req) {
        Device existing = deviceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在"));
        if (AccessVendor.GB28181.name().equals(existing.getVendor())) {
            return updateGbDevice(existing, req);
        }
        Device d = fromDeviceRequest(req);
        d.setId(existing.getId());
        d.setDeviceId(existing.getDeviceId());
        if (d.getCameraIp() == null) {
            d.setCameraPassword(null);
            d.setCameraUsername(null);
            d.setCameraPort(null);
            d.setPassword(null);
            d.setUsername(null);
            d.setPort(null);
            d.setAccessChannel(null);
        } else if (d.getPassword() == null || d.getPassword().isBlank()) {
            d.setPassword(existing.getPassword());
            d.setCameraPassword(existing.getCameraPassword());
        }
        boolean loginChanged = !sameText(existing.getCameraIp(), d.getCameraIp())
                || !Objects.equals(existing.getCameraPort(), d.getCameraPort())
                || !sameText(existing.getCameraUsername(), d.getCameraUsername())
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
        if (sameRecordBinding(existing, d) && existing.getRecordChannel() != null) {
            d.setRecordChannel(existing.getRecordChannel());
            d.setRecordChannelName(existing.getRecordChannelName());
        } else {
            bindRecord(d, existing.getId());
        }
        deviceRepository.update(d);
        deviceRepository.saveUniview(d);
        if (hasUniviewLogin(d)) {
            if (loginChanged) {
                syncUniviewStreams(d);
            } else {
                try {
                    syncUniviewStreams(d);
                } catch (RuntimeException e) {
                    log.warn("宇视码流未刷新，保留原码流 deviceId={} err={}", d.getDeviceId(), e.getMessage());
                }
            }
        } else if (AccessVendor.RTSP.name().equals(d.getVendor())) {
            rtspPullService.sync(d, req.getPullUrl());
        } else {
            removePullStreams(d.getDeviceId());
        }
        Device persisted = deviceRepository.findById(id).orElse(d);
        notifyJizhiAiUpdate(persisted);
        return toDeviceView(persisted);
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
        boolean created = deviceOpt.isEmpty();
        if (created) {
            Device d = new Device();
            d.setDeviceId(deviceId);
            d.setName(req.getDeviceName() == null || req.getDeviceName().isBlank() ? deviceId : req.getDeviceName());
            d.setStatus(DeviceStatus.ENABLED);
            d.setDeviceFeature(DeviceFeature.VIDEO);
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
            markLiveStream(s.getId());
            s = streamRepository.findById(s.getId()).orElse(s);
        } else {
            ensureDefaultLiveStream(deviceId);
            s = streamRepository.findById(s.getId()).orElse(s);
        }
        deviceRepository.findByDeviceId(deviceId).ifPresent(device -> {
            if (created) {
                notifyJizhiAiSave(device);
            } else {
                notifyJizhiAiUpdate(device);
            }
        });
        Map<String, Object> view = toStreamView(s);
        view.put("playCount", previewService.getRef(deviceId, type));
        return view;
    }

    /**
     * 将指定码流设为业务端直播流（同设备互斥）。
     */
    @Transactional
    public Map<String, Object> setLiveStream(Long streamId) {
        DeviceStream current = streamRepository.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("码流不存在"));
        String deviceId = current.getDeviceId();
        Map<String, Object> view = markLiveStream(streamId);
        deviceRepository.findByDeviceId(deviceId).ifPresent(this::notifyJizhiAiUpdate);
        return view;
    }

    private Map<String, Object> markLiveStream(Long streamId) {
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
     * 业务直播流摘要（streamType + streamUrl），与业务端设备详情 {@code liveStream} 一致；未配置时为 null。
     */
    public Map<String, Object> liveStreamForDevice(String deviceId) {
        return resolveLiveStream(deviceId).map(live -> toLiveStreamSummary(deviceId, live)).orElse(null);
    }

    private Map<String, Object> toLiveStreamSummary(String deviceId, DeviceStream live) {
        Map<String, Object> liveView = new LinkedHashMap<>();
        liveView.put("streamType", live.getStreamType());
        liveView.put("streamUrl", liveStreamRtspResolver.resolve(deviceId, live));
        return liveView;
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
            markLiveStream(s.getId());
            return streamRepository.findById(s.getId());
        }
        return found;
    }

    /** 业务端直播：宇视向摄像机拉流，模拟走国标或已登记地址，海康未对接。 */
    public Map<String, Object> startBizLive(String deviceId) {
        Device device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在: " + deviceId));
        return switch (VendorDevices.of(device)) {
            case HIKVISION -> throw new IllegalArgumentException(VendorDevices.HIKVISION_UNSUPPORTED);
            case UNIVIEW -> previewService.start(deviceId, requireLiveStream(deviceId).getStreamType());
            case UNIVIEW_CLOUD -> previewService.start(deviceId, "main");
            case RTSP -> previewService.start(deviceId, requireLiveStream(deviceId).getStreamType());
            case MOCK -> startMockLive(deviceId);
            case GB28181 -> gb28181PlayService.startLive(deviceId)
                    .orElseThrow(() -> new IllegalArgumentException("国标点播未返回播放地址"));
        };
    }

    /** 国标设备可改资料和通道编号。设备编号、注册编号保持不变。 */
    private Map<String, Object> updateGbDevice(Device existing, DeviceRequest req) {
        prepareGb(existing, req, existing.getId());
        existing.setDeviceNo(req.getDeviceNo());
        existing.setDeviceType(DeviceType.normalize(req.getDeviceType()));
        existing.setDeviceFeature(DeviceFeature.normalize(req.getDeviceFeature()));
        existing.setName(req.getName());
        String prevCameraPassword = existing.getCameraPassword();
        applyCameraCredentials(existing, req);
        if (req.getPassword() == null || req.getPassword().isBlank()) {
            existing.setCameraPassword(prevCameraPassword);
            existing.setPassword(prevCameraPassword);
        }
        existing.setPlatformId(req.getPlatformId());
        existing.setFolderId(req.getFolderId());
        existing.setStatus(DeviceStatus.normalizeManual(req.getStatus() == null ? DeviceStatus.ENABLED : req.getStatus()));
        existing.setManufacturer(req.getManufacturer());
        existing.setModel(req.getModel());
        existing.setAddress(req.getAddress());
        existing.setPtzType(req.getPtzType() == null ? 0 : req.getPtzType());
        existing.setGatewayId(req.getGatewayId());
        existing.setLongitude(req.getLongitude());
        existing.setLatitude(req.getLatitude());
        existing.setVendor(AccessVendor.GB28181.name());
        deviceRepository.update(existing);
        saveGbVideoChannel(existing.getDeviceId(), req.getVideoChannelId(), existing.getName());
        Device persisted = deviceRepository.findById(existing.getId()).orElse(existing);
        notifyJizhiAiUpdate(persisted);
        return toDeviceView(persisted);
    }

    private Map<String, Object> startMockLive(String deviceId) {
        if (gb28181PlayService.preferForBizLive()) {
            Optional<Map<String, Object>> gb = gb28181PlayService.startLive(deviceId);
            if (gb.isPresent()) {
                return gb.get();
            }
        }
        return previewService.start(deviceId, requireLiveStream(deviceId).getStreamType());
    }

    private DeviceStream requireLiveStream(String deviceId) {
        return resolveLiveStream(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("设备未配置可直播码流"));
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
        deviceRepository.findByDeviceId(deviceId).ifPresent(this::notifyJizhiAiUpdate);
    }

    private Device fromDeviceRequest(DeviceRequest req) {
        Device d = new Device();
        d.setDeviceId(req.getDeviceId().trim());
        d.setDeviceNo(req.getDeviceNo());
        d.setDeviceType(DeviceType.normalize(req.getDeviceType()));
        d.setDeviceFeature(DeviceFeature.normalize(req.getDeviceFeature()));
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
        applyCameraCredentials(d, req);
        d.setLanIp(blank(req.getLanIp()));
        d.setRecordDeviceId(req.getRecordDeviceId());
        d.setVendor(resolveVendor(req, d).name());
        if (AccessVendor.UNIVIEW_CLOUD.name().equals(d.getVendor())) {
            UniviewCloudDeviceIds.parse(d.getDeviceId());
        }
        if (!AccessVendor.UNIVIEW.name().equals(d.getVendor())) {
            clearUniview(d);
        } else if (d.getCameraIp() == null) {
            d.setCameraPort(null);
            d.setCameraUsername(null);
            d.setCameraPassword(null);
            d.setHost(null);
            d.setPort(null);
            d.setUsername(null);
            d.setPassword(null);
            d.setAccessChannel(null);
        } else {
            String channel = blank(req.getAccessChannel());
            d.setAccessChannel(channel == null ? "0" : channel);
            if (d.getCameraPort() == null) {
                d.setCameraPort(80);
                d.setPort(80);
            }
        }
        d.setAccessStatus("unknown");
        return d;
    }

    private void prepareGb(Device device, DeviceRequest req, Long excludeId) {
        String deviceId = device.getDeviceId() == null ? "" : device.getDeviceId().trim();
        if (!Gb28181Ids.isVideoChannel(deviceId)) {
            throw new IllegalArgumentException("国标设备ID必须是20位数字，类型为131或132");
        }
        String video = requireGbVideo(req.getVideoChannelId());
        String audio = requireGbAudio(req.getAudioChannelId());
        if (audio.equals(deviceId) || audio.equals(video)) {
            throw new IllegalArgumentException("音频通道ID不能和设备ID、视频通道ID相同");
        }
        streamRepository.findByChannelId(video).ifPresent(stream -> {
            if (!deviceId.equals(stream.getDeviceId())) {
                throw new IllegalArgumentException("该视频通道ID已被设备 " + stream.getDeviceId() + " 使用");
            }
        });
        deviceRepository.findOtherByGbAudio(audio, excludeId).ifPresent(other -> {
            throw new IllegalArgumentException("该音频通道ID已被设备 " + other.getDeviceId() + " 使用");
        });
        device.setGbAudioId(audio);
        if (device.getGbRegisterId() == null || device.getGbRegisterId().isBlank()) {
            device.setGbRegisterId(deviceId);
        }
    }

    private void saveGbVideoChannel(String deviceId, String videoChannelId, String name) {
        String video = requireGbVideo(videoChannelId);
        String streamName = name == null || name.isBlank() ? video : name.trim();
        Optional<DeviceStream> main = streamRepository.findByDeviceIdAndType(deviceId, "main");
        if (main.isPresent()) {
            DeviceStream stream = main.get();
            stream.setChannelId(video);
            if (stream.getStreamName() == null || stream.getStreamName().isBlank()) {
                stream.setStreamName(streamName);
            }
            streamRepository.update(stream);
            if (!Boolean.TRUE.equals(stream.getLiveEnabled())
                    && streamRepository.findLiveByDeviceId(deviceId).isEmpty()) {
                streamRepository.setLive(stream.getId(), true);
            }
            return;
        }
        DeviceStream stream = new DeviceStream();
        stream.setDeviceId(deviceId);
        stream.setStreamType("main");
        stream.setChannelId(video);
        stream.setStreamName(streamName);
        stream.setStatus("OFF");
        stream.setSortNo(1);
        stream.setLiveEnabled(true);
        streamRepository.insert(stream);
    }

    private static String requireGbVideo(String videoChannelId) {
        if (!Gb28181Ids.isVideoChannel(videoChannelId)) {
            throw new IllegalArgumentException("视频通道ID必须是20位数字，类型为131或132");
        }
        return videoChannelId.trim();
    }

    private static String requireGbAudio(String audioChannelId) {
        if (!Gb28181Ids.isAudioChannel(audioChannelId)) {
            throw new IllegalArgumentException("音频通道ID必须是20位数字，类型为137");
        }
        return audioChannelId.trim();
    }

    private static AccessVendor resolveVendor(DeviceRequest req, Device d) {
        String raw = req.getVendor();
        if (raw != null && !raw.isBlank()) {
            try {
                return AccessVendor.from(raw);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("平台只能是 MOCK、UNIVIEW、UNIVIEW_CLOUD、HIKVISION、RTSP、GB28181");
            }
        }
        if (d.getHost() != null || d.getLanIp() != null || d.getRecordDeviceId() != null) {
            return AccessVendor.UNIVIEW;
        }
        return AccessVendor.MOCK;
    }

    private static void clearUniview(Device d) {
        d.setAccessChannel(null);
        d.setLanIp(null);
        d.setRecordDeviceId(null);
        d.setRecordChannel(null);
        d.setRecordChannelName(null);
    }

    private static void applyCameraCredentials(Device d, DeviceRequest req) {
        String ip = firstNonBlank(blank(req.getCameraIp()), blank(req.getHost()));
        d.setCameraIp(ip);
        d.setHost(ip);
        Integer port = req.getCameraPort() != null ? req.getCameraPort() : req.getPort();
        d.setCameraPort(port);
        d.setPort(port);
        String username = firstNonBlank(blank(req.getCameraUsername()), blank(req.getUsername()));
        d.setCameraUsername(username);
        d.setUsername(username);
        String password = req.getPassword();
        if (password != null && !password.isBlank()) {
            d.setCameraPassword(password);
            d.setPassword(password);
        } else {
            d.setCameraPassword(null);
            d.setPassword(null);
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }

    private void bindRecord(Device d, Long excludeId) {
        boolean hasIp = d.getLanIp() != null;
        boolean hasRecorder = d.getRecordDeviceId() != null;
        if (!hasIp && !hasRecorder) {
            d.setRecordChannel(null);
            d.setRecordChannelName(null);
            return;
        }
        if (!hasIp || !hasRecorder) {
            throw new IllegalArgumentException("内网 IP 和录像设备需要一起填写");
        }
        RecordDevice recorder = nvrRecordingService.requireRecorder(d.getRecordDeviceId());
        NvrRecordingService.LanChannel channel = nvrRecordingService.matchLanIp(recorder, d.getLanIp());
        deviceRepository.findOtherByRecordChannel(recorder.getId(), channel.channelId(), excludeId)
                .ifPresent(other -> {
                    throw new IllegalArgumentException("该录像通道已被设备 " + other.getDeviceId() + " 使用");
                });
        d.setRecordDeviceId(recorder.getId());
        d.setRecordChannel(channel.channelId());
        d.setRecordChannelName(channel.name());
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
                    .ifPresent(s -> markLiveStream(s.getId()));
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

    private static boolean sameRecordBinding(Device existing, Device next) {
        return Objects.equals(existing.getRecordDeviceId(), next.getRecordDeviceId())
                && sameText(existing.getLanIp(), next.getLanIp());
    }

    private static boolean sameText(String a, String b) {
        String left = a == null ? "" : a.trim();
        String right = b == null ? "" : b.trim();
        return left.equals(right);
    }

    /**
     * 定时巡检：非「已停用」的推流设备按 ZLM 在线情况切换 已启用 / 不可用。
     * 宇视登录设备、直接拉流和国标不走这里。国标上下线跟着 SIP 注册。
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
            if (AccessVendor.GB28181.name().equals(d.getVendor())) {
                continue;
            }
            if (AccessVendor.UNIVIEW_CLOUD.name().equals(d.getVendor())) {
                continue;
            }
            if (AccessVendor.RTSP.name().equals(d.getVendor())) {
                if (DeviceStatus.isUnavailable(current)) {
                    deviceRepository.updateStatus(d.getId(), DeviceStatus.ENABLED);
                    deviceRepository.findById(d.getId()).ifPresent(this::notifyJizhiAiUpdate);
                    changed++;
                    log.info("直接拉流不按推流巡检，恢复已启用 deviceId={}", d.getDeviceId());
                }
                continue;
            }
            Boolean online = isDevicePushing(d.getDeviceId());
            if (online == null) {
                continue;
            }
            int target = online ? DeviceStatus.ENABLED : DeviceStatus.UNAVAILABLE;
            if (target != current) {
                deviceRepository.updateStatus(d.getId(), target);
                deviceRepository.findById(d.getId()).ifPresent(this::notifyJizhiAiUpdate);
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

    /** 把库里的全部设备按修改接口同步到极知。不看当前目录筛选。 */
    public Map<String, Object> syncAllDevicesToJizhi() {
        if (!jizhiAiDeviceSyncClient.isReady()) {
            throw new IllegalArgumentException("极知同步未开启或未配置地址");
        }
        int success = 0;
        int failed = 0;
        int skipped = 0;
        List<Device> devices = deviceRepository.findAll();
        for (Device device : devices) {
            if (device.getDeviceId() == null || device.getDeviceId().isBlank()) {
                skipped++;
                continue;
            }
            if (notifyJizhiAiUpdate(device)) {
                success++;
            } else {
                failed++;
            }
        }
        log.info("[极知AI] 全量同步完成 total={} success={} failed={} skipped={}",
                devices.size(), success, failed, skipped);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", devices.size());
        result.put("success", success);
        result.put("failed", failed);
        result.put("skipped", skipped);
        return result;
    }

    private boolean notifyJizhiAiUpdate(Device device) {
        Map<String, Object> live = liveStreamForDevice(device.getDeviceId());
        String rtsp = live == null ? null : (String) live.get("streamUrl");
        return jizhiAiDeviceSyncClient.syncDeviceUpdate(device, rtsp);
    }

    private void notifyJizhiAiSave(Device device) {
        Map<String, Object> live = liveStreamForDevice(device.getDeviceId());
        String rtsp = live == null ? null : (String) live.get("streamUrl");
        jizhiAiDeviceSyncClient.syncDeviceSave(device, rtsp);
    }

    private Map<String, Object> toDeviceView(Device d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("deviceId", d.getDeviceId());
        m.put("deviceNo", d.getDeviceNo());
        m.put("deviceType", DeviceType.normalize(d.getDeviceType()));
        m.put("deviceFeature", DeviceFeature.normalize(d.getDeviceFeature()));
        m.put("keys", DeviceFeature.capabilityKeys(d.getDeviceFeature()));
        m.put("name", d.getName());
        m.put("platformId", d.getPlatformId());
        m.put("vendor", d.getVendor() == null || d.getVendor().isBlank() ? AccessVendor.MOCK.name() : d.getVendor());
        if (AccessVendor.RTSP.name().equals(d.getVendor())) {
            streamRepository.findByDeviceIdAndType(d.getDeviceId(), "main")
                    .ifPresent(s -> m.put("pullUrl", s.getPullUrl()));
        }
        if (AccessVendor.GB28181.name().equals(d.getVendor())) {
            m.put("audioChannelId", d.getGbAudioId());
            streamRepository.findByDeviceIdAndType(d.getDeviceId(), "main")
                    .ifPresent(s -> m.put("videoChannelId", s.getChannelId()));
        }
        m.put("folderId", d.getFolderId());
        m.put("status", DeviceStatus.normalize(d.getStatus()));
        m.put("manufacturer", d.getManufacturer());
        m.put("model", d.getModel());
        m.put("address", d.getAddress());
        m.put("gatewayId", d.getGatewayId());
        m.put("cameraIp", d.getCameraIp());
        m.put("cameraPort", d.getCameraPort());
        m.put("cameraUsername", d.getCameraUsername());
        m.put("host", d.getCameraIp());
        m.put("port", d.getCameraPort());
        m.put("username", d.getCameraUsername());
        m.put("accessChannel", d.getAccessChannel());
        m.put("lanIp", d.getLanIp());
        m.put("recordDeviceId", d.getRecordDeviceId());
        m.put("recordChannel", d.getRecordChannel());
        m.put("recordChannelName", d.getRecordChannelName());
        return m;
    }

    private Map<String, Object> toStreamView(DeviceStream s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("streamType", s.getStreamType());
        m.put("streamUrl", s.getStreamUrl());
        m.put("streamName", s.getStreamName());
        m.put("status", s.getStatus());
        m.put("liveEnabled", Boolean.TRUE.equals(s.getLiveEnabled()));
        return m;
    }
}
