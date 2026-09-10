package com.jizhi.videomid.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "zlm")
public class ZlmProperties {

    private String baseUrl = "http://127.0.0.1:8080";
    private String secret = "";
    /** 按 ZLM 真实观看人数回写 Redis 的间隔，毫秒；0 表示关闭 */
    private int playCountSyncMs = 5000;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
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
