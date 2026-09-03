package com.jizhi.videomid.device.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "device")
public class DeviceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, unique = true, length = 64)
    private String deviceId;

    @Column(name = "parent_id", length = 64)
    private String parentId;

    @Column(name = "name", length = 256)
    private String name;

    @Column(name = "manufacturer", length = 128)
    private String manufacturer;

    @Column(name = "model", length = 128)
    private String model;

    @Column(name = "owner", length = 128)
    private String owner;

    @Column(name = "civil_code", length = 64)
    private String civilCode;

    @Column(name = "address", length = 256)
    private String address;

    @Column(name = "parental")
    private Integer parental;

    @Column(name = "register_way")
    private Integer registerWay;

    @Column(name = "secrecy")
    private Integer secrecy;

    @Column(name = "status", length = 16)
    private String status;

    @Column(name = "device_type", length = 32)
    private String deviceType;

    @Column(name = "stream_type", length = 16)
    private String streamType;

    @Column(name = "main_channel_id", length = 64)
    private String mainChannelId;

    @Column(name = "sub_channel_id", length = 64)
    private String subChannelId;

    @Column(name = "ptz_type")
    private Integer ptzType;

    @Column(name = "gateway_id", length = 64)
    private String gatewayId;

    @Column(name = "platform_id", length = 64)
    private String platformId;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "extra_xml", columnDefinition = "TEXT")
    private String extraXml;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (status == null) {
            status = "OFF";
        }
        if (deviceType == null) {
            deviceType = "CAMERA";
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getCivilCode() {
        return civilCode;
    }

    public void setCivilCode(String civilCode) {
        this.civilCode = civilCode;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Integer getParental() {
        return parental;
    }

    public void setParental(Integer parental) {
        this.parental = parental;
    }

    public Integer getRegisterWay() {
        return registerWay;
    }

    public void setRegisterWay(Integer registerWay) {
        this.registerWay = registerWay;
    }

    public Integer getSecrecy() {
        return secrecy;
    }

    public void setSecrecy(Integer secrecy) {
        this.secrecy = secrecy;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getStreamType() {
        return streamType;
    }

    public void setStreamType(String streamType) {
        this.streamType = streamType;
    }

    public String getMainChannelId() {
        return mainChannelId;
    }

    public void setMainChannelId(String mainChannelId) {
        this.mainChannelId = mainChannelId;
    }

    public String getSubChannelId() {
        return subChannelId;
    }

    public void setSubChannelId(String subChannelId) {
        this.subChannelId = subChannelId;
    }

    public Integer getPtzType() {
        return ptzType;
    }

    public void setPtzType(Integer ptzType) {
        this.ptzType = ptzType;
    }

    public String getGatewayId() {
        return gatewayId;
    }

    public void setGatewayId(String gatewayId) {
        this.gatewayId = gatewayId;
    }

    public String getPlatformId() {
        return platformId;
    }

    public void setPlatformId(String platformId) {
        this.platformId = platformId;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public String getExtraXml() {
        return extraXml;
    }

    public void setExtraXml(String extraXml) {
        this.extraXml = extraXml;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
