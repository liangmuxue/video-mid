package com.jizhi.videomid.uniview.dto;

public class PtzMoveRequest {
    private String deviceId;
    private String direction;
    private Integer speed;

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public Integer getSpeed() { return speed; }
    public void setSpeed(Integer speed) { this.speed = speed; }
}
