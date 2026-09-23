package com.jizhi.videomid.biz.dto;

public class BizRecordingDaysRequest {
    private String deviceId;
    private Integer year;
    private Integer month;

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public Integer getMonth() { return month; }
    public void setMonth(Integer month) { this.month = month; }
}
