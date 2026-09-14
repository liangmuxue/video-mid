package com.jizhi.videomid.device;

/**
 * 设备业务状态：
 * - 已启用 / 已停用：人工设置
 * - 不可用：定时巡检发现未推流时自动设置（不会覆盖「已停用」）
 */
public final class DeviceStatus {

    public static final String ENABLED = "已启用";
    public static final String DISABLED = "已停用";
    public static final String UNAVAILABLE = "不可用";

    private DeviceStatus() {
    }

    /** 规范为三态之一；兼容历史 ON/OFF */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return DISABLED;
        }
        String s = raw.trim();
        if (ENABLED.equals(s) || "ON".equalsIgnoreCase(s) || "ENABLED".equalsIgnoreCase(s)) {
            return ENABLED;
        }
        if (DISABLED.equals(s) || "OFF".equalsIgnoreCase(s) || "DISABLED".equalsIgnoreCase(s)) {
            return DISABLED;
        }
        if (UNAVAILABLE.equals(s) || "UNAVAILABLE".equalsIgnoreCase(s)) {
            return UNAVAILABLE;
        }
        return s;
    }

    /** 人工可写状态（表单/API）；不可用只能由巡检写入 */
    public static String normalizeManual(String raw) {
        String n = normalize(raw);
        if (UNAVAILABLE.equals(n)) {
            return ENABLED;
        }
        if (!ENABLED.equals(n) && !DISABLED.equals(n)) {
            return DISABLED;
        }
        return n;
    }

    public static boolean isDisabled(String raw) {
        return DISABLED.equals(normalize(raw));
    }
}
