package com.jizhi.videomid.uniview.dto;

import java.util.Map;

/** 鹰眼扩展控制。其它平台调用时返回不支持。 */
public class PtzCommandRequest {
    private String deviceId;
    private String command;
    private Map<String, Object> params;

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }
    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }
}
