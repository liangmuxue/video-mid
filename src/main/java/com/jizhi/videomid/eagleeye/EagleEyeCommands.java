package com.jizhi.videomid.eagleeye;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 把统一云台参数换成鹰眼子系统的命令名。 */
public final class EagleEyeCommands {

    private static final Pattern CLOCK = Pattern.compile("^([01]\\d|2[0-3])_[0-5]\\d_[0-5]\\d$");
    private static final List<String> WEEKDAYS = List.of(
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday");

    private EagleEyeCommands() {
    }

    /**
     * 校验并收成子系统要的 params。只收尚未单独开接口的命令。
     * 方向、停止、变倍、聚焦仍走原来的云台接口。
     */
    public static Map<String, Object> params(String command, Map<String, Object> raw) {
        Map<String, Object> in = raw == null ? Map.of() : raw;
        return switch (command == null ? "" : command) {
            case "camera_autofocus" -> Map.of("autoFocus", bool(in, "autoFocus"));
            case "autoScan_switch", "regularTime_switch", "detectMode_switch" ->
                    Map.of("switch", oneOf(in, "switch", "on", "off"));
            case "autoScan_control" -> Map.of("control", oneOf(in, "control", "pause", "continue"));
            case "regularTime_timeSet" -> times(in);
            case "regularTime_daySet" -> days(in);
            case "specialScan_control" -> scan(in);
            default -> throw new IllegalArgumentException("不支持的鹰眼命令: " + command);
        };
    }

    private static Map<String, Object> times(Map<String, Object> in) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("beginTimeFirst", clock(in, "beginTimeFirst"));
        params.put("endTimeFirst", clock(in, "endTimeFirst"));
        params.put("beginTimeSecond", clock(in, "beginTimeSecond"));
        params.put("endTimeSecond", clock(in, "endTimeSecond"));
        return params;
    }

    private static Map<String, Object> days(Map<String, Object> in) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (String day : WEEKDAYS) {
            params.put(day, bool(in, day));
        }
        return params;
    }

    private static Map<String, Object> scan(Map<String, Object> in) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("building", text(in, "building"));
        params.put("floor", integer(in, "floor"));
        params.put("repeatTimes", integer(in, "repeatTimes"));
        return params;
    }

    private static String text(Map<String, Object> in, String field) {
        Object value = in.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return text.trim();
    }

    private static String clock(Map<String, Object> in, String field) {
        String value = text(in, field);
        if (!CLOCK.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " 格式必须是 HH_mm_ss，例如 08_00_00");
        }
        return value;
    }

    private static String oneOf(Map<String, Object> in, String field, String... allowed) {
        String value = text(in, field).toLowerCase();
        for (String item : allowed) {
            if (item.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException(field + " 只能是 " + String.join(" / ", allowed));
    }

    private static boolean bool(Map<String, Object> in, String field) {
        Object value = in.get(field);
        if (!(value instanceof Boolean flag)) {
            throw new IllegalArgumentException(field + " 必须是 true 或 false");
        }
        return flag;
    }

    private static int integer(Map<String, Object> in, String field) {
        Object value = in.get(field);
        if (!(value instanceof Number number) || value instanceof Boolean) {
            throw new IllegalArgumentException(field + " 必须是整数");
        }
        double raw = number.doubleValue();
        if (raw != Math.rint(raw) || raw > Integer.MAX_VALUE || raw < Integer.MIN_VALUE) {
            throw new IllegalArgumentException(field + " 必须是整数");
        }
        return number.intValue();
    }

    /** 统一接口的 left_up 换成协议里的 upleft。停止不走方向命令。 */
    public static String direction(String direction) {
        return switch (direction == null ? "" : direction.toLowerCase()) {
            case "up", "down", "left", "right" -> direction.toLowerCase();
            case "left_up" -> "upleft";
            case "right_up" -> "upright";
            case "left_down" -> "downleft";
            case "right_down" -> "downright";
            default -> null;
        };
    }

    public static String zoom(String action) {
        return switch (action == null ? "" : action.toLowerCase()) {
            case "in", "zoom_in", "tele" -> "zoom_in";
            case "out", "zoom_out", "wide" -> "zoom_out";
            default -> null;
        };
    }

    public static String focus(String action) {
        return switch (action == null ? "" : action.toLowerCase()) {
            case "near", "focus_near" -> "focus_near";
            case "far", "focus_far" -> "focus_far";
            default -> null;
        };
    }

    /** 统一接口速度是 1~8，子系统只收 1~7。 */
    public static int speed(int speed) {
        return Math.max(1, Math.min(speed, 7));
    }
}
