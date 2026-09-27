package com.jizhi.videomid.gb28181.sip.live;

import java.time.Instant;

/** 下级平台 SIP REGISTER 会话 */
public class Gb28181SipSession {

    private final String deviceId;
    private final String contactHost;
    private final int contactPort;
    private final String transport;
    private final String rawContact;
    private final long expiresSeconds;
    private final Instant registeredAt;
    private volatile Instant expiresAt;

    public Gb28181SipSession(String deviceId,
                           String contactHost,
                           int contactPort,
                           String transport,
                           String rawContact,
                           long expiresSeconds) {
        this.deviceId = deviceId;
        this.contactHost = contactHost;
        this.contactPort = contactPort;
        this.transport = transport == null || transport.isBlank() ? "UDP" : transport;
        this.rawContact = rawContact;
        this.expiresSeconds = expiresSeconds;
        this.registeredAt = Instant.now();
        this.expiresAt = expiresSeconds <= 0
                ? Instant.now()
                : registeredAt.plusSeconds(expiresSeconds);
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getContactHost() {
        return contactHost;
    }

    public int getContactPort() {
        return contactPort;
    }

    public String getTransport() {
        return transport;
    }

    public String getRawContact() {
        return rawContact;
    }

    public long getExpiresSeconds() {
        return expiresSeconds;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired() {
        return expiresSeconds > 0 && Instant.now().isAfter(expiresAt);
    }

    public void refreshExpiry(long seconds) {
        this.expiresAt = seconds <= 0 ? Instant.now() : Instant.now().plusSeconds(seconds);
    }
}
