package com.jizhi.videomid.device.dto;

import jakarta.validation.constraints.NotBlank;

public class DeviceRequest {
    @NotBlank
    private String deviceId;
    private String name;
    private String platformId;
    private String status;
    private String manufacturer;
    private String model;
    private String address;
    private Integer ptzType;
    private String gatewayId;
    private Double longitude;
    private Double latitude;

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPlatformId() { return platformId; }
    public void setPlatformId(String platformId) { this.platformId = platformId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
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
}
