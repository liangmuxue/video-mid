package com.jizhi.videomid.device.dto;

import jakarta.validation.constraints.NotBlank;

public class DeviceRequest {
    @NotBlank
    private String deviceId;
    private String name;
    private String platformId;
    private Long folderId;
    /** 1=已启用 2=已停用（0 不可用仅系统写入） */
    private Integer status;
    private String manufacturer;
    private String model;
    private String address;
    private Integer ptzType;
    private String gatewayId;
    private Double longitude;
    private Double latitude;
    private String host;
    private Integer port;
    private String username;
    private String password;
    private String accessChannel;

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPlatformId() { return platformId; }
    public void setPlatformId(String platformId) { this.platformId = platformId; }
    public Long getFolderId() { return folderId; }
    public void setFolderId(Long folderId) { this.folderId = folderId; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public Integer getPtzType() { return ptzType; }
    public void setPtzType(Integer ptzType) { this.ptzType = ptzType; }
    public String getGatewayId() { return gatewayId; }
    public void setGatewayId(String gatewayId) { this.gatewayId = gatewayId; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getAccessChannel() { return accessChannel; }
    public void setAccessChannel(String accessChannel) { this.accessChannel = accessChannel; }
}
