package com.jizhi.videomid.sip;

import com.jizhi.videomid.config.SipProperties;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 拼装 GB/T 28181 SDP。Java 只拼 SDP/发 SIP，不处理媒体字节。
 */
public final class SdpBuilder {

    private static final DateTimeFormatter GB = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private SdpBuilder() {
    }

    /**
     * 直播 Play SDP。
     */
    public static String buildPlaySdp(SipProperties sip, String channelId, String ssrc, int rtpPort) {
        String ip = sip.getPublicIp();
        StringBuilder sb = new StringBuilder();
        sb.append("v=0\r\n");
        sb.append("o=").append(channelId).append(" 0 0 IN IP4 ").append(ip).append("\r\n");
        sb.append("s=Play\r\n");
        sb.append("c=IN IP4 ").append(ip).append("\r\n");
        sb.append("t=0 0\r\n");
        sb.append("m=video ").append(rtpPort).append(" RTP/AVP 96 98 97\r\n");
        sb.append("a=recvonly\r\n");
        sb.append("a=rtpmap:96 PS/90000\r\n");
        sb.append("a=rtpmap:98 H264/90000\r\n");
        sb.append("a=rtpmap:97 MPEG4/90000\r\n");
        sb.append("y=").append(ssrc).append("\r\n");
        return sb.toString();
    }

    /**
     * 回放 Playback SDP，携带起止时间。
     */
    public static String buildPlaybackSdp(SipProperties sip, String channelId, String ssrc,
                                          int rtpPort, String startTime, String endTime) {
        String ip = sip.getPublicIp();
        long tStart = toEpochSeconds(startTime);
        long tEnd = toEpochSeconds(endTime);
        StringBuilder sb = new StringBuilder();
        sb.append("v=0\r\n");
        sb.append("o=").append(channelId).append(" 0 0 IN IP4 ").append(ip).append("\r\n");
        sb.append("s=Playback\r\n");
        sb.append("u=").append(channelId).append(":0\r\n");
        sb.append("c=IN IP4 ").append(ip).append("\r\n");
        sb.append("t=").append(tStart).append(" ").append(tEnd).append("\r\n");
        sb.append("m=video ").append(rtpPort).append(" RTP/AVP 96 98 97\r\n");
        sb.append("a=recvonly\r\n");
        sb.append("a=rtpmap:96 PS/90000\r\n");
        sb.append("a=rtpmap:98 H264/90000\r\n");
        sb.append("a=rtpmap:97 MPEG4/90000\r\n");
        sb.append("y=").append(ssrc).append("\r\n");
        return sb.toString();
    }

    public static String generateSsrc(boolean playback) {
        // 国标习惯：直播 0 开头，回放 1 开头
        String prefix = playback ? "1" : "0";
        String tail = String.valueOf(System.currentTimeMillis() % 1_000_000_000L);
        while (tail.length() < 9) {
            tail = "0" + tail;
        }
        return prefix + tail;
    }

    private static long toEpochSeconds(String time) {
        if (time == null || time.isBlank()) {
            return 0;
        }
        String t = time.trim().replace(" ", "T");
        LocalDateTime ldt;
        if (t.length() >= 19) {
            ldt = LocalDateTime.parse(t.substring(0, 19), GB);
        } else {
            ldt = LocalDateTime.parse(t);
        }
        return ldt.atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
    }
}
