package com.jizhi.videomid.device;

public class Device {
    private Long id;
    private String deviceId;
    /** 位置编号。纯数字如 101，或楼栋编号如 40-1-3-B2。 */
    private String deviceNo;
    /** 摄像头类型：0=云台，1=天台，2=楼梯，3=电梯，4=大厅，5=地下室 */
    private Integer deviceType;
    /** 设备能力特性：0=抓拍，1=视频流，2=两者 */
    private Integer deviceFeature;
    private String name;
    /** 摄像头 IP（入库 device 表） */
    private String cameraIp;
    private Integer cameraPort;
    private String cameraUsername;
    private String cameraPassword;
    private String platformId;
    /** MOCK / UNIVIEW / HIKVISION / RTSP / GB28181。一台设备只对应一个平台。 */
    private String vendor;
    /** 国标 REGISTER 的 20 位设备编号。视频通道设备用它在注销时一起下线。 */
    private String gbRegisterId;
    /** 国标音频通道编号，类型 137。 */
    private String gbAudioId;
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
    public String getDeviceNo() { return deviceNo; }
    public void setDeviceNo(String deviceNo) { this.deviceNo = deviceNo; }
    public Integer getDeviceType() { return deviceType; }
    public void setDeviceType(Integer deviceType) { this.deviceType = deviceType; }
    public Integer getDeviceFeature() { return deviceFeature; }
    public void setDeviceFeature(Integer deviceFeature) { this.deviceFeature = deviceFeature; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCameraIp() { return cameraIp; }
    public void setCameraIp(String cameraIp) { this.cameraIp = cameraIp; }
    public Integer getCameraPort() { return cameraPort; }
    public void setCameraPort(Integer cameraPort) { this.cameraPort = cameraPort; }
    public String getCameraUsername() { return cameraUsername; }
    public void setCameraUsername(String cameraUsername) { this.cameraUsername = cameraUsername; }
    public String getCameraPassword() { return cameraPassword; }
    public void setCameraPassword(String cameraPassword) { this.cameraPassword = cameraPassword; }
    public String getPlatformId() { return platformId; }
    public void setPlatformId(String platformId) { this.platformId = platformId; }
    public String getVendor() { return vendor; }
    public void setVendor(String vendor) { this.vendor = vendor; }
    public String getGbRegisterId() { return gbRegisterId; }
    public void setGbRegisterId(String gbRegisterId) { this.gbRegisterId = gbRegisterId; }
    public String getGbAudioId() { return gbAudioId; }
    public void setGbAudioId(String gbAudioId) { this.gbAudioId = gbAudioId; }
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
