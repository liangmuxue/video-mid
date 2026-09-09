package com.jizhi.videomid.device.dto;

import jakarta.validation.constraints.NotBlank;

public class PreviewRequest {
    @NotBlank
    private String deviceId;
    private String streamType;

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getStreamType() { return streamType; }
    public void setStreamType(String streamType) { this.streamType = streamType; }
}
