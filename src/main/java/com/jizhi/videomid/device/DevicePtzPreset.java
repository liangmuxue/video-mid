package com.jizhi.videomid.device;

/** 中台预置位目录（名称/编号）。真实姿态在设备上。 */
public class DevicePtzPreset {
    private Long id;
    private String deviceId;
    private Integer presetIndex;
    private String name;
    private Double zoom;
    private Long createdAt;
    private Long updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public Integer getPresetIndex() { return presetIndex; }
    public void setPresetIndex(Integer presetIndex) { this.presetIndex = presetIndex; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Double getZoom() { return zoom; }
    public void setZoom(Double zoom) { this.zoom = zoom; }
    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
    public Long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Long updatedAt) { this.updatedAt = updatedAt; }
}
