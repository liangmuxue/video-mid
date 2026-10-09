package com.jizhi.videomid.device;

import java.util.List;

/** 设备能力类型：0=抓拍，1=视频流，2=视频流+抓拍 */
public final class DeviceType {

    public static final int SNAPSHOT = 0;
    public static final int VIDEO = 1;
    public static final int BOTH = 2;

    private DeviceType() {
    }

    public static int normalize(Object value) {
        if (value == null) {
            return VIDEO;
        }
        int v;
        if (value instanceof Number n) {
            v = n.intValue();
        } else {
            try {
                v = Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("deviceType 必须是 0、1 或 2");
            }
        }
        if (v != SNAPSHOT && v != VIDEO && v != BOTH) {
            throw new IllegalArgumentException("deviceType 必须是 0（抓拍）、1（视频流）或 2（两者）");
        }
        return v;
    }

    /** 业务能力 key 列表（不入库，随 deviceType 推导）。 */
    public static List<String> capabilityKeys(Object deviceType) {
        return switch (normalize(deviceType)) {
            case SNAPSHOT -> List.of("snap_face");
            case VIDEO -> List.of("photo");
            case BOTH -> List.of("photo", "snap_face");
            default -> List.of("photo");
        };
    }
}
