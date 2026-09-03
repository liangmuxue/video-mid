package com.jizhi.videomid.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "zlm")
public class ZlmProperties {

    private String baseUrl = "http://127.0.0.1:80";
    private String secret = "035c73f7-bb6b-4889-a715-d9eb2d1925cc";
    private String playHost = "http://127.0.0.1";
    private String rtpPortRange = "30000-30500";
    private String vhost = "__defaultVhost__";
    private String app = "rtp";

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

    public String getPlayHost() {
        return playHost;
    }

    public void setPlayHost(String playHost) {
        this.playHost = playHost;
    }

    public String getRtpPortRange() {
        return rtpPortRange;
    }

    public void setRtpPortRange(String rtpPortRange) {
        this.rtpPortRange = rtpPortRange;
    }

    public String getVhost() {
        return vhost;
    }

    public void setVhost(String vhost) {
        this.vhost = vhost;
    }

    public String getApp() {
        return app;
    }

    public void setApp(String app) {
        this.app = app;
    }
}
