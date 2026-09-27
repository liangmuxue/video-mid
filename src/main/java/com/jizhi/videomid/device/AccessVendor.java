package com.jizhi.videomid.device;

/**
 * 一台设备只对接一个平台。查看、直播、云台、录像都按这个值进入对应实现。
 */
public enum AccessVendor {
    MOCK,
    UNIVIEW,
    HIKVISION;

    public static AccessVendor from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return AccessVendor.valueOf(raw.trim().toUpperCase());
    }
}
