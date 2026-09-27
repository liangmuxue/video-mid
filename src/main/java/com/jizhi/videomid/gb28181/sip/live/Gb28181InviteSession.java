package com.jizhi.videomid.gb28181.sip.live;

import javax.sip.Dialog;
import java.time.Instant;

/** 国标 INVITE 点播会话 */
public class Gb28181InviteSession {

    public enum Status { PENDING, PLAYING, FAILED, STOPPED }

    private final String channelId;
    private final String gbDeviceId;
    private final String callId;
    private final int rtpPort;
    private final String streamId;
    private final String ssrc;
    private volatile Status status;
    private volatile String failReason = "";
    private volatile Dialog dialog;
    private final Instant createdAt = Instant.now();

    public Gb28181InviteSession(String channelId,
                                String gbDeviceId,
                                String callId,
                                int rtpPort,
                                String streamId,
                                String ssrc) {
        this.channelId = channelId;
        this.gbDeviceId = gbDeviceId;
        this.callId = callId;
        this.rtpPort = rtpPort;
        this.streamId = streamId;
        this.ssrc = ssrc;
        this.status = Status.PENDING;
    }

    public String getChannelId() { return channelId; }
    public String getGbDeviceId() { return gbDeviceId; }
    public String getCallId() { return callId; }
    public int getRtpPort() { return rtpPort; }
    public String getStreamId() { return streamId; }
    public String getSsrc() { return ssrc; }
    public Status getStatus() { return status; }
    public String getFailReason() { return failReason; }
    public Dialog getDialog() { return dialog; }
    public Instant getCreatedAt() { return createdAt; }

    public void setDialog(Dialog dialog) { this.dialog = dialog; }
    public void markPlaying() { this.status = Status.PLAYING; }
    public void markFailed(String reason) {
        this.status = Status.FAILED;
        this.failReason = reason == null ? "" : reason;
    }
    public void markStopped() { this.status = Status.STOPPED; }
}
