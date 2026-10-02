package com.jizhi.videomid.device;

/**
 * 按设备上的平台进入对应数据源。空白按模拟。
 */
public final class VendorDevices {

    public static final String HIKVISION_UNSUPPORTED = "海康尚未对接";

    private VendorDevices() {
    }

    public static AccessVendor of(Device device) {
        if (device == null || device.getVendor() == null || device.getVendor().isBlank()) {
            return AccessVendor.MOCK;
        }
        try {
            return AccessVendor.from(device.getVendor());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("平台只能是 MOCK、UNIVIEW、HIKVISION");
        }
    }
}
