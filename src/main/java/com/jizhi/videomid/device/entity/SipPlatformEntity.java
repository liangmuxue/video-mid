package com.jizhi.videomid.device.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sip_platform")
public class SipPlatformEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "platform_id", nullable = false, unique = true, length = 64)
    private String platformId;

    @Column(name = "domain", length = 64)
    private String domain;

    @Column(name = "ip", length = 64)
    private String ip;

    @Column(name = "port")
    private Integer port;

    @Column(name = "transport", length = 8)
    private String transport;

    @Column(name = "status", length = 16)
    private String status;

    @Column(name = "last_register_at")
    private LocalDateTime lastRegisterAt;

    @Column(name = "last_keepalive_at")
    private LocalDateTime lastKeepaliveAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = "OFFLINE";
        }
        if (transport == null) {
            transport = "UDP";
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPlatformId() {
        return platformId;
    }

    public void setPlatformId(String platformId) {
        this.platformId = platformId;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getTransport() {
        return transport;
    }

    public void setTransport(String transport) {
        this.transport = transport;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getLastRegisterAt() {
        return lastRegisterAt;
    }

    public void setLastRegisterAt(LocalDateTime lastRegisterAt) {
        this.lastRegisterAt = lastRegisterAt;
    }

    public LocalDateTime getLastKeepaliveAt() {
        return lastKeepaliveAt;
    }

    public void setLastKeepaliveAt(LocalDateTime lastKeepaliveAt) {
        this.lastKeepaliveAt = lastKeepaliveAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
