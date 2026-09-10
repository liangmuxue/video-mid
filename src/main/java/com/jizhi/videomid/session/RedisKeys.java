package com.jizhi.videomid.session;

/** Redis 仅存码流播放引用计数 */
public final class RedisKeys {
    private RedisKeys() {}

    public static String streamRef(String deviceId, String streamType) {
        String type = streamType == null || streamType.isBlank() ? "sub" : streamType.toLowerCase();
        return "stream:ref:" + deviceId + ":" + type;
    }

    public static String streamPlayers(String deviceId, String streamType) {
        String type = streamType == null || streamType.isBlank() ? "sub" : streamType.toLowerCase();
        return "stream:players:" + deviceId + ":" + type;
    }
}
