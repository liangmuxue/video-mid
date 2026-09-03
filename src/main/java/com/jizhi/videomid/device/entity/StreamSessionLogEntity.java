package com.jizhi.videomid.device.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "stream_session_log")
public class StreamSessionLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    @Column(name = "stream_type", length = 16)
    private String streamType;

    @Column(name = "play_type", nullable = false, length = 16)
    private String playType;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "stream_id", length = 128)
    private String streamId;

    @Column(name = "rtp_port")
    private Integer rtpPort;

    @Column(name = "play_url_flv", length = 512)
    private String playUrlFlv;

    @Column(name = "play_url_hls", length = 512)
    private String playUrlHls;

    @Column(name = "status", length = 16)
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "stopped_at")
    private LocalDateTime stoppedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = "STARTING";
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getStreamType() {
        return streamType;
    }

    public void setStreamType(String streamType) {
        this.streamType = streamType;
    }

    public String getPlayType() {
        return playType;
    }

    public void setPlayType(String playType) {
        this.playType = playType;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public String getStreamId() {
        return streamId;
    }

    public void setStreamId(String streamId) {
        this.streamId = streamId;
    }

    public Integer getRtpPort() {
        return rtpPort;
    }

    public void setRtpPort(Integer rtpPort) {
        this.rtpPort = rtpPort;
    }

    public String getPlayUrlFlv() {
        return playUrlFlv;
    }

    public void setPlayUrlFlv(String playUrlFlv) {
        this.playUrlFlv = playUrlFlv;
    }

    public String getPlayUrlHls() {
        return playUrlHls;
    }

    public void setPlayUrlHls(String playUrlHls) {
        this.playUrlHls = playUrlHls;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getStoppedAt() {
        return stoppedAt;
    }

    public void setStoppedAt(LocalDateTime stoppedAt) {
        this.stoppedAt = stoppedAt;
    }
}
