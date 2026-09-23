package com.jizhi.videomid.uniview.dto;

public class PtzPresetSaveRequest {
    private String deviceId;
    private Integer index;
    private String name;
    private Boolean overwrite;

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public Integer getIndex() { return index; }
    public void setIndex(Integer index) { this.index = index; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Boolean getOverwrite() { return overwrite; }
    public void setOverwrite(Boolean overwrite) { this.overwrite = overwrite; }
}
