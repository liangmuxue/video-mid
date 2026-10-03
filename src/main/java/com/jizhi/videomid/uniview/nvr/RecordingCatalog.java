package com.jizhi.videomid.uniview.nvr;

import com.jizhi.videomid.device.AccessVendor;
import com.jizhi.videomid.device.Device;
import com.jizhi.videomid.device.DeviceRepository;
import com.jizhi.videomid.device.VendorDevices;
import com.jizhi.videomid.record.RecordFileService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 宇视且已绑定录像机时查录像机，模拟或未绑定查本地文件，海康拒绝。
 */
@Service
public class RecordingCatalog {
    private final DeviceRepository deviceRepository;
    private final RecordFileService recordFileService;
    private final NvrRecordingService nvrRecordingService;

    public RecordingCatalog(DeviceRepository deviceRepository,
                            RecordFileService recordFileService,
                            NvrRecordingService nvrRecordingService) {
        this.deviceRepository = deviceRepository;
        this.recordFileService = recordFileService;
        this.nvrRecordingService = nvrRecordingService;
    }

    public List<String> listRecordingDays(String deviceId, int year, int month) {
        Device device = boundUniview(deviceId);
        if (device != null) {
            return nvrRecordingService.listRecordingDays(device, year, month);
        }
        return recordFileService.listRecordingDays(deviceId, year, month);
    }

    public List<Map<String, Object>> listWithVideoUrls(String deviceId, String from, String to, String publicBaseUrl) {
        Device device = boundUniview(deviceId);
        if (device != null) {
            return nvrRecordingService.listRecordings(device, from, to);
        }
        return recordFileService.listWithVideoUrls(deviceId, from, to, publicBaseUrl);
    }

    public List<Map<String, Object>> list(String deviceId, String from, String to) {
        Device device = boundUniview(deviceId);
        if (device != null) {
            return nvrRecordingService.listRecordings(device, from, to);
        }
        return recordFileService.list(deviceId, from, to);
    }

    public String openNvrPlayback(String deviceId, long begin, long end) {
        Device device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在"));
        if (VendorDevices.of(device) == AccessVendor.HIKVISION) {
            throw new IllegalArgumentException(VendorDevices.HIKVISION_UNSUPPORTED);
        }
        if (VendorDevices.of(device) != AccessVendor.UNIVIEW || !nvrRecordingService.isBound(device)) {
            throw new IllegalArgumentException("该设备没有绑定录像设备");
        }
        return nvrRecordingService.openPlayback(device, begin, end);
    }

    public void releaseNvrPlayback(String flvUrl) {
        nvrRecordingService.releasePlayback(flvUrl);
    }

    /** 宇视且已绑定录像机时返回设备，模拟或未绑定返回 null 走本地文件。海康直接拒绝。 */
    private Device boundUniview(String deviceId) {
        Device device = deviceRepository.findByDeviceId(deviceId).orElse(null);
        if (device == null) {
            return null;
        }
        if (VendorDevices.of(device) == AccessVendor.HIKVISION) {
            throw new IllegalArgumentException(VendorDevices.HIKVISION_UNSUPPORTED);
        }
        if (VendorDevices.of(device) == AccessVendor.UNIVIEW && nvrRecordingService.isBound(device)) {
            return device;
        }
        return null;
    }
}
