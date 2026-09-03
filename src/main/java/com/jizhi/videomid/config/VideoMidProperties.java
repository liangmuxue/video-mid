package com.jizhi.videomid.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "video-mid")
public class VideoMidProperties {

    private String defaultStreamType = "sub";
    private long playbackUrlTtlSeconds = 600;
    private long liveUrlTtlSeconds = 3600;
    private long streamReadyTimeoutMs = 15000;

    public String getDefaultStreamType() {
        return defaultStreamType;
    }

    public void setDefaultStreamType(String defaultStreamType) {
        this.defaultStreamType = defaultStreamType;
    }

    public long getPlaybackUrlTtlSeconds() {
        return playbackUrlTtlSeconds;
    }

    public void setPlaybackUrlTtlSeconds(long playbackUrlTtlSeconds) {
        this.playbackUrlTtlSeconds = playbackUrlTtlSeconds;
    }

    public long getLiveUrlTtlSeconds() {
        return liveUrlTtlSeconds;
    }

    public void setLiveUrlTtlSeconds(long liveUrlTtlSeconds) {
        this.liveUrlTtlSeconds = liveUrlTtlSeconds;
    }

    public long getStreamReadyTimeoutMs() {
        return streamReadyTimeoutMs;
    }

    public void setStreamReadyTimeoutMs(long streamReadyTimeoutMs) {
        this.streamReadyTimeoutMs = streamReadyTimeoutMs;
    }
}
