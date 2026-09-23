package com.jizhi.videomid.uniview.live;

import com.jizhi.videomid.device.DeviceStream;

/** 宇视拉流在 ZLM 上的固定标识。播放地址可以先写入码流表，不代表已经在拉。 */
public final class UniviewStreamIds {

    public static final String APP = "live";

    private UniviewStreamIds() {
    }

    public static boolean isPull(DeviceStream stream) {
        return stream != null
                && stream.getZlmApp() != null && !stream.getZlmApp().isBlank()
                && stream.getZlmStream() != null && !stream.getZlmStream().isBlank();
    }

    public static String zlmStream(String deviceId, String streamType) {
        String raw = deviceId == null ? "device" : deviceId.trim();
        String safe = raw.replaceAll("[^A-Za-z0-9_]", "_");
        return "uv_" + safe + "_" + streamType;
    }

    public static String playUrl(String base, String zlmStream) {
        String value = base == null || base.isBlank() ? "http://127.0.0.1:8080" : base.trim();
        if (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value + "/" + APP + "/" + zlmStream + ".live.flv";
    }

    public static int indexOf(String streamType) {
        if ("main".equalsIgnoreCase(streamType)) {
            return 0;
        }
        if ("third".equalsIgnoreCase(streamType)) {
            return 2;
        }
        return 1;
    }
}
