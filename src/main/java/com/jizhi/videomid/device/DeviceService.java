package com.jizhi.videomid.device;

import com.jizhi.videomid.device.dto.DeviceRequest;
import com.jizhi.videomid.device.dto.StreamRegisterRequest;
import com.jizhi.videomid.session.PreviewService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final DeviceStreamRepository streamRepository;
    private final PreviewService previewService;

    public DeviceService(DeviceRepository deviceRepository,
                         DeviceStreamRepository streamRepository,
                         PreviewService previewService) {
        this.deviceRepository = deviceRepository;
        this.streamRepository = streamRepository;
        this.previewService = previewService;
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
        List<Device> devices = deviceRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Device d : devices) {
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
            d.setStatus("ON");
            d.setPtzType(0);
            deviceRepository.insert(d);
        } else if (req.getDeviceName() != null && !req.getDeviceName().isBlank()) {
            Device d = deviceOpt.get();
            d.setName(req.getDeviceName());
            d.setStatus("ON");
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
            long id = streamRepository.insert(s);
            s.setId(id);
        }
        Map<String, Object> view = toStreamView(s);
        view.put("playCount", previewService.getRef(deviceId, type));
        return view;
    }

    @Transactional
    public void deleteStream(Long id) {
        if (streamRepository.deleteById(id) <= 0) {
            throw new IllegalArgumentException("码流不存在");
        }
    }

    private Device fromDeviceRequest(DeviceRequest req) {
        Device d = new Device();
        d.setDeviceId(req.getDeviceId().trim());
        d.setName(req.getName());
        d.setPlatformId(req.getPlatformId());
        d.setStatus(req.getStatus() == null || req.getStatus().isBlank() ? "OFF" : req.getStatus());
        d.setManufacturer(req.getManufacturer());
        d.setModel(req.getModel());
        d.setAddress(req.getAddress());
        d.setPtzType(req.getPtzType() == null ? 0 : req.getPtzType());
        d.setGatewayId(req.getGatewayId());
        d.setLongitude(req.getLongitude());
        d.setLatitude(req.getLatitude());
        return d;
    }

    private Map<String, Object> toDeviceView(Device d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("deviceId", d.getDeviceId());
        m.put("name", d.getName());
        m.put("platformId", d.getPlatformId());
        m.put("status", d.getStatus());
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
        m.put("createdAt", s.getCreatedAt());
        m.put("updatedAt", s.getUpdatedAt());
        return m;
    }
}
