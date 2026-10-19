package com.jizhi.videomid.device.dto;

import jakarta.validation.constraints.NotBlank;

public class DeviceRequest {
    @NotBlank
    private String deviceId;
    /** 位置编号，如 101（一层第一个）；可选 */
    private Integer deviceNo;
    /** 摄像头类型：0=云台 … 5=地下室；默认 0 */
    private Integer deviceType;
    /** 设备能力特性：0=抓拍，1=视频流，2=两者；默认 1 */
    private Integer deviceFeature;
    private String name;
    private String platformId;
    /** MOCK / UNIVIEW / UNIVIEW_CLOUD / HIKVISION / RTSP / GB28181 */
    private String vendor;
    /** vendor=GB28181 时的视频通道编号。 */
    private String videoChannelId;
    /** vendor=GB28181 时的音频通道编号。 */
    private String audioChannelId;
    /** vendor=RTSP 时的拉流地址。编辑时留空表示不修改。 */
    private String pullUrl;
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
    /** 摄像头 IP / 端口 / 账号（写入 device 表；宇视仍兼容 host/port/username） */
    private String cameraIp;
    private Integer cameraPort;
    private String cameraUsername;
    private String host;
    private Integer port;
    private String username;
    private String password;
    private String accessChannel;
    private String lanIp;
    private Long recordDeviceId;

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public Integer getDeviceNo() { return deviceNo; }
    public void setDeviceNo(Integer deviceNo) { this.deviceNo = deviceNo; }
    public Integer getDeviceType() { return deviceType; }
    public void setDeviceType(Integer deviceType) { this.deviceType = deviceType; }
    public Integer getDeviceFeature() { return deviceFeature; }
    public void setDeviceFeature(Integer deviceFeature) { this.deviceFeature = deviceFeature; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPlatformId() { return platformId; }
    public void setPlatformId(String platformId) { this.platformId = platformId; }
    public String getVendor() { return vendor; }
    public void setVendor(String vendor) { this.vendor = vendor; }
    public String getVideoChannelId() { return videoChannelId; }
    public void setVideoChannelId(String videoChannelId) { this.videoChannelId = videoChannelId; }
    public String getAudioChannelId() { return audioChannelId; }
    public void setAudioChannelId(String audioChannelId) { this.audioChannelId = audioChannelId; }
    public String getPullUrl() { return pullUrl; }
    public void setPullUrl(String pullUrl) { this.pullUrl = pullUrl; }
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
    public String getCameraIp() { return cameraIp; }
    public void setCameraIp(String cameraIp) { this.cameraIp = cameraIp; }
    public Integer getCameraPort() { return cameraPort; }
    public void setCameraPort(Integer cameraPort) { this.cameraPort = cameraPort; }
    public String getCameraUsername() { return cameraUsername; }
    public void setCameraUsername(String cameraUsername) { this.cameraUsername = cameraUsername; }
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
    public String getLanIp() { return lanIp; }
    public void setLanIp(String lanIp) { this.lanIp = lanIp; }
    public Long getRecordDeviceId() { return recordDeviceId; }
    public void setRecordDeviceId(Long recordDeviceId) { this.recordDeviceId = recordDeviceId; }
}
