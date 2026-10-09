package com.jizhi.videomid.device;

public class Device {
    private Long id;
    private String deviceId;
    /** 位置编号，如 101 表示一层第一个摄像头（展示可格式化为 0101） */
    private Integer deviceNo;
    /** 设备类型：0=抓拍，1=视频流，2=两者 */
    private Integer deviceType;
    private String name;
    private String platformId;
    /** MOCK / UNIVIEW / HIKVISION。一台设备只对应一个平台。 */
    private String vendor;
    private Long folderId;
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
    private String accessStatus;
    private String accessError;
    private String lanIp;
    private Long recordDeviceId;
    private Integer recordChannel;
    private String recordChannelName;
    private Long createdAt;
    private Long updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public Integer getDeviceNo() { return deviceNo; }
    public void setDeviceNo(Integer deviceNo) { this.deviceNo = deviceNo; }
    public Integer getDeviceType() { return deviceType; }
    public void setDeviceType(Integer deviceType) { this.deviceType = deviceType; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPlatformId() { return platformId; }
    public void setPlatformId(String platformId) { this.platformId = platformId; }
    public String getVendor() { return vendor; }
    public void setVendor(String vendor) { this.vendor = vendor; }
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
    public String getAccessStatus() { return accessStatus; }
    public void setAccessStatus(String accessStatus) { this.accessStatus = accessStatus; }
    public String getAccessError() { return accessError; }
    public void setAccessError(String accessError) { this.accessError = accessError; }
    public String getLanIp() { return lanIp; }
    public void setLanIp(String lanIp) { this.lanIp = lanIp; }
    public Long getRecordDeviceId() { return recordDeviceId; }
    public void setRecordDeviceId(Long recordDeviceId) { this.recordDeviceId = recordDeviceId; }
    public Integer getRecordChannel() { return recordChannel; }
    public void setRecordChannel(Integer recordChannel) { this.recordChannel = recordChannel; }
    public String getRecordChannelName() { return recordChannelName; }
    public void setRecordChannelName(String recordChannelName) { this.recordChannelName = recordChannelName; }
    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
    public Long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Long updatedAt) { this.updatedAt = updatedAt; }
}
