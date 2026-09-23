package com.jizhi.videomid.uniview.dto;

public class PtzActionRequest {
    private String deviceId;
    private String action;
    private Integer speed;

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public Integer getSpeed() { return speed; }
    public void setSpeed(Integer speed) { this.speed = speed; }
}
