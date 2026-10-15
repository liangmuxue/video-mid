package com.jizhi.videomid.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "zlm")
public class ZlmProperties {

    private String baseUrl = "http://127.0.0.1:8080";
    /** 返回给浏览器的播放地址前缀。空则与 baseUrl 相同。 */
    private String playBaseUrl = "";
    /** 列表里返回的 RTSP 前缀，含主机和端口。空则从播放地址取主机，端口用 554。 */
    private String rtspBaseUrl = "";
    private String secret = "";
    /** 按 ZLM 真实观看人数回写 Redis 的间隔，毫秒；0 表示关闭 */
    private int playCountSyncMs = 5000;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getPlayBaseUrl() {
        if (playBaseUrl == null || playBaseUrl.isBlank()) {
            return baseUrl;
        }
        return playBaseUrl;
    }

    public void setPlayBaseUrl(String playBaseUrl) {
        this.playBaseUrl = playBaseUrl;
    }

    public String getRtspBaseUrl() {
        if (rtspBaseUrl != null && !rtspBaseUrl.isBlank()) {
            return rtspBaseUrl.trim();
        }
        String play = getPlayBaseUrl();
        if (play == null || play.isBlank()) {
            return "";
        }
        try {
            java.net.URI uri = java.net.URI.create(play.trim());
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                return "";
            }
            return "rtsp://" + uri.getHost() + ":554";
        } catch (Exception e) {
            return "";
        }
    }

    public void setRtspBaseUrl(String rtspBaseUrl) {
        this.rtspBaseUrl = rtspBaseUrl;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public int getPlayCountSyncMs() {
        return playCountSyncMs;
    }

    public void setPlayCountSyncMs(int playCountSyncMs) {
        this.playCountSyncMs = playCountSyncMs;
    }
}
