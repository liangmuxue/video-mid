package com.jizhi.videomid.uniview.dto;

public class PtzSnapshotRequest {
    private String deviceId;
    private String channelType;

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getChannelType() { return channelType; }
    public void setChannelType(String channelType) { this.channelType = channelType; }
}
