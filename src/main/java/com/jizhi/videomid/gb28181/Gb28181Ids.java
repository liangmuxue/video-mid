package com.jizhi.videomid.gb28181;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 国标 20 位编码。第 11–13 位是类型：131/132 视频，137 音频。 */
public final class Gb28181Ids {

    private Gb28181Ids() {
    }

    public static boolean isVideoChannel(String id) {
        String type = typeCode(id);
        return "131".equals(type) || "132".equals(type);
    }

    public static boolean isAudioChannel(String id) {
        return "137".equals(typeCode(id));
    }

    /** 目录里的第一个音频通道。没有则返回 null。 */
    public static String firstAudioChannel(List<Map<String, String>> items) {
        if (items == null) {
            return null;
        }
        for (Map<String, String> item : items) {
            if (item == null) {
                continue;
            }
            String id = trim(item.get("DeviceID"));
            if (isAudioChannel(id)) {
                return id;
            }
        }
        return null;
    }

    public static String typeCode(String id) {
        if (id == null) {
            return "";
        }
        String value = id.trim();
        if (value.length() != 20 || !value.chars().allMatch(Character::isDigit)) {
            return "";
        }
        return value.substring(10, 13);
    }

    /**
     * 目录里的视频通道。没有视频通道时，注册号本身是摄像机则用注册号。
     * 音频、录像机和平台编号不返回。
     */
    public static List<VideoChannel> videoChannels(List<Map<String, String>> items, String registerId) {
        LinkedHashMap<String, VideoChannel> channels = new LinkedHashMap<>();
        if (items != null) {
            for (Map<String, String> item : items) {
                if (item == null) {
                    continue;
                }
                String id = trim(item.get("DeviceID"));
                if (!isVideoChannel(id)) {
                    continue;
                }
                channels.putIfAbsent(id, new VideoChannel(
                        id,
                        trimToNull(item.get("Name")),
                        trimToNull(item.get("Manufacturer")),
                        trimToNull(item.get("Model"))));
            }
        }
        if (channels.isEmpty() && isVideoChannel(registerId)) {
            String id = registerId.trim();
            channels.put(id, new VideoChannel(id, null, null, null));
        }
        return new ArrayList<>(channels.values());
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String trimToNull(String value) {
        String trimmed = trim(value);
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record VideoChannel(String channelId, String name, String manufacturer, String model) {
    }
}
