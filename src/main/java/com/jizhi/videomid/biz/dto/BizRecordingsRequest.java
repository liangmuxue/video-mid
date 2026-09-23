package com.jizhi.videomid.biz.dto;

public class BizRecordingsRequest {
    private String deviceId;
    /** 开始时间（毫秒），数字或字符串，可空 */
    private Object from;
    /** 结束时间（毫秒），数字或字符串，可空 */
    private Object to;

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public Object getFrom() { return from; }
    public void setFrom(Object from) { this.from = from; }
    public Object getTo() { return to; }
    public void setTo(Object to) { this.to = to; }

    public String fromAsString() { return asMillisString(from); }
    public String toAsString() { return asMillisString(to); }

    private static String asMillisString(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return String.valueOf(n.longValue());
        }
        String s = String.valueOf(raw).trim();
        return s.isEmpty() ? null : s;
    }
}
