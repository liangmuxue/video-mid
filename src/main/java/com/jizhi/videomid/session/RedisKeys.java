package com.jizhi.videomid.session;

/**
 * Redis 运行时键（仅临时数据，禁止持久化设备业务元数据）。
 * <pre>
 * stream:ref:{deviceId}:{main|sub}
 * stream:url:{deviceId}:{main|sub}
 * stream:task:{deviceId}
 * device:online:{deviceId}
 * ptz:queue:{deviceId}
 * gw:session:{gatewayId}
 * </pre>
 */
public final class RedisKeys {

    private RedisKeys() {
    }

    public static String streamRef(String deviceId, String streamType) {
        return "stream:ref:" + deviceId + ":" + normalize(streamType);
    }

    public static String streamUrl(String deviceId, String streamType) {
        return "stream:url:" + deviceId + ":" + normalize(streamType);
    }

    public static String streamMeta(String deviceId, String streamType) {
        return "stream:meta:" + deviceId + ":" + normalize(streamType);
    }

    public static String streamTask(String deviceId) {
        return "stream:task:" + deviceId;
    }

    public static String deviceOnline(String deviceId) {
        return "device:online:" + deviceId;
    }

    public static String ptzQueue(String deviceId) {
        return "ptz:queue:" + deviceId;
    }

    public static String ptzSeq() {
        return "ptz:seq";
    }

    public static String gwSession(String gatewayId) {
        return "gw:session:" + gatewayId;
    }

    public static String streamReady(String streamId) {
        return "stream:ready:" + streamId;
    }

    private static String normalize(String streamType) {
        if (streamType == null || streamType.isBlank()) {
            return "sub";
        }
        return streamType.toLowerCase();
    }
}
