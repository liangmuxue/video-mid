package com.jizhi.videomid.uniview.cloud;

/** 云设备在本地的编号是「序列号:通道号」。IPC 通道号为 0。 */
public final class UniviewCloudDeviceIds {

    private UniviewCloudDeviceIds() {
    }

    public static String id(String serial, int channelNo) {
        if (serial == null || serial.isBlank()) {
            throw new IllegalArgumentException("宇视云设备序列号为空");
        }
        return serial.trim() + ":" + channelNo;
    }

    public static Target parse(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("宇视云设备ID请写成「序列号:通道号」，IPC 通道号为 0");
        }
        String raw = deviceId.trim();
        int colon = raw.lastIndexOf(':');
        if (colon <= 0 || colon == raw.length() - 1) {
            throw new IllegalArgumentException("宇视云设备ID请写成「序列号:通道号」，IPC 通道号为 0");
        }
        String serial = raw.substring(0, colon).trim();
        String channelText = raw.substring(colon + 1).trim();
        if (serial.isEmpty() || !channelText.matches("\\d+")) {
            throw new IllegalArgumentException("宇视云设备ID请写成「序列号:通道号」，IPC 通道号为 0");
        }
        return new Target(serial, Integer.parseInt(channelText));
    }

    public static String zlmStream(String serial, int channelNo) {
        String safe = serial.replaceAll("[^A-Za-z0-9]", "_");
        return safe + "_" + channelNo;
    }

    /** 播放路径里的流名还原成「序列号:通道号」。序列号本身不含下划线。 */
    public static String deviceIdFromZlmStream(String zlmStream) {
        if (zlmStream == null || zlmStream.isBlank()) {
            throw new IllegalArgumentException("播放地址无效");
        }
        String raw = zlmStream.trim();
        if (raw.endsWith(".live.flv")) {
            raw = raw.substring(0, raw.length() - ".live.flv".length());
        }
        int split = raw.lastIndexOf('_');
        if (split <= 0 || split == raw.length() - 1) {
            throw new IllegalArgumentException("播放地址无效");
        }
        String serial = raw.substring(0, split);
        String channel = raw.substring(split + 1);
        if (!serial.matches("[A-Za-z0-9]+") || !channel.matches("\\d+")) {
            throw new IllegalArgumentException("播放地址无效");
        }
        return id(serial, Integer.parseInt(channel));
    }

    public record Target(String serial, int channelNo) {
    }
}
