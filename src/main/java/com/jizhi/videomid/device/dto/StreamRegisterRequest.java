package com.jizhi.videomid.device.dto;

import jakarta.validation.constraints.NotBlank;

/** 码流注册：写入/更新 stream_url，不走国标通道取流 */
public class StreamRegisterRequest {
    @NotBlank
    private String deviceId;
    @NotBlank
    private String streamType;
    @NotBlank
    private String streamUrl;
    private String streamName;
    private String channelId;
    private String status;
    private Integer sortNo;
    /** 注册时可顺带创建/更新设备名称 */
    private String deviceName;

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getStreamType() { return streamType; }
    public void setStreamType(String streamType) { this.streamType = streamType; }
    public String getStreamUrl() { return streamUrl; }
    public void setStreamUrl(String streamUrl) { this.streamUrl = streamUrl; }
    public String getStreamName() { return streamName; }
    public void setStreamName(String streamName) { this.streamName = streamName; }
    public String getChannelId() { return channelId; }
    public void setChannelId(String channelId) { this.channelId = channelId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
}
