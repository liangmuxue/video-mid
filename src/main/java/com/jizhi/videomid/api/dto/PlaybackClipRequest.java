package com.jizhi.videomid.api.dto;

import jakarta.validation.constraints.NotBlank;

public class PlaybackClipRequest {

    @NotBlank
    private String deviceId;

    @NotBlank
    private String startTime;

    @NotBlank
    private String endTime;

    private String streamType;

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getStreamType() {
        return streamType;
    }

    public void setStreamType(String streamType) {
        this.streamType = streamType;
    }
}
