package com.jizhi.videomid.device;

/**
 * 设备状态（库存 int）：
 * 0=不可用 1=已启用 2=已停用
 */
public final class DeviceStatus {

    public static final int UNAVAILABLE = 0;
    public static final int ENABLED = 1;
    public static final int DISABLED = 2;

    private DeviceStatus() {
    }

    /** 规范为 0/1/2；兼容历史中文与 ON/OFF 字符串。 */
    public static int normalize(Object raw) {
        if (raw == null) {
            return DISABLED;
        }
        if (raw instanceof Number n) {
            return normalizeCode(n.intValue());
        }
        String s = String.valueOf(raw).trim();
        if (s.isEmpty()) {
            return DISABLED;
        }
        if (s.matches("-?\\d+")) {
            return normalizeCode(Integer.parseInt(s));
        }
        if ("已启用".equals(s) || "ON".equalsIgnoreCase(s) || "ENABLED".equalsIgnoreCase(s)) {
            return ENABLED;
        }
        if ("已停用".equals(s) || "OFF".equalsIgnoreCase(s) || "DISABLED".equalsIgnoreCase(s)) {
            return DISABLED;
        }
        if ("不可用".equals(s) || "UNAVAILABLE".equalsIgnoreCase(s)) {
            return UNAVAILABLE;
        }
        return DISABLED;
    }

    /** 人工可写：仅 1/2；不可用只能巡检写入 */
    public static int normalizeManual(Object raw) {
        int n = normalize(raw);
        if (n == UNAVAILABLE) {
            return ENABLED;
        }
        if (n != ENABLED && n != DISABLED) {
            return DISABLED;
        }
        return n;
    }

    public static boolean isEnabled(int status) {
        return status == ENABLED;
    }

    public static boolean isDisabled(int status) {
        return status == DISABLED;
    }

    public static boolean isUnavailable(int status) {
        return status == UNAVAILABLE;
    }

    private static int normalizeCode(int code) {
        if (code == ENABLED || code == DISABLED || code == UNAVAILABLE) {
            return code;
        }
        return DISABLED;
    }
}
