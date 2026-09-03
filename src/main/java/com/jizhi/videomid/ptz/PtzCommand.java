package com.jizhi.videomid.ptz;

/**
 * 云台指令报文（中台 → 边缘网关）。
 */
public class PtzCommand {

    private long seq;
    private String deviceId;
    private String cmd;
    private Integer speed;
    private Integer timeoutMs;

    public PtzCommand() {
    }

    public PtzCommand(long seq, String deviceId, String cmd, Integer speed, Integer timeoutMs) {
        this.seq = seq;
        this.deviceId = deviceId;
        this.cmd = cmd;
        this.speed = speed;
        this.timeoutMs = timeoutMs;
    }

    public long getSeq() {
        return seq;
    }

    public void setSeq(long seq) {
        this.seq = seq;
    }

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
