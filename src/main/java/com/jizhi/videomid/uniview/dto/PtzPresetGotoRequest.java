package com.jizhi.videomid.uniview.dto;

public class PtzPresetGotoRequest {
    private String deviceId;
    private Integer index;

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public Integer getIndex() { return index; }
    public void setIndex(Integer index) { this.index = index; }
}
