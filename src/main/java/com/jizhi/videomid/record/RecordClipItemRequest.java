package com.jizhi.videomid.record;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * 批量截取录像：单条请求（deviceId + 时间戳 + 秒数）。
 */
public class RecordClipItemRequest {

    private String deviceId;

    /** 毫秒时间戳，支持数字或字符串 */
    @JsonAlias({"timestamp", "time", "ts"})
    private Object at;

    /** 时间戳前后各取 seconds 秒；不传则用默认值 */
    private Integer seconds;

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public Object getAt() {
        return at;
    }

    public void setAt(Object at) {
        this.at = at;
    }

    public Integer getSeconds() {
        return seconds;
    }

    public void setSeconds(Integer seconds) {
        this.seconds = seconds;
    }

    public String atAsString() {
        if (at == null) {
            return null;
        }
        if (at instanceof Number n) {
            long v = n.longValue();
            return String.valueOf(v);
        }
        String s = String.valueOf(at).trim();
        return s.isEmpty() ? null : s;
    }
}
