package com.jizhi.videomid.eagleeye;

/** 把统一云台参数换成鹰眼子系统的命令名。 */
public final class EagleEyeCommands {

    private EagleEyeCommands() {
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
