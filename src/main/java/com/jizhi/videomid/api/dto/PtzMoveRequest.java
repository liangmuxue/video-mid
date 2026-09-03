package com.jizhi.videomid.api.dto;

import jakarta.validation.constraints.NotBlank;

public class PtzMoveRequest {

    @NotBlank
    private String deviceId;

    @NotBlank
    private String cmd;

    private Integer speed;

    private Integer timeoutMs;

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getCmd() {
        return cmd;
    }

    public void setCmd(String cmd) {
        this.cmd = cmd;
    }

    public Integer getSpeed() {
        return speed;
    }

    public void setSpeed(Integer speed) {
        this.speed = speed;
    }

    public Integer getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(Integer timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
