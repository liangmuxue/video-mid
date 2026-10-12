package com.jizhi.videomid.gb28181.sip.live;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Gb28181InviteSdpTest {

    @Test
    void sdpUsesPublicMediaAddressAndTenDigitSsrc() {
        String sdp = Gb28181SipOutboundClient.buildInviteSdp("119.3.161.125", 7096, "0536870913");
        assertTrue(sdp.contains("c=IN IP4 119.3.161.125\r\n"));
        assertTrue(sdp.contains("m=video 7096 RTP/AVP 96\r\n"));
        assertTrue(sdp.contains("a=recvonly\r\n"));
        assertTrue(!sdp.contains("a=sendonly"));
        assertTrue(sdp.contains("y=0536870913\r\n"));
        assertEquals("0100000001", Gb28181SipOutboundClient.gbSsrc(null));
        assertEquals("0000000010", Gb28181SipOutboundClient.gbSsrc("10"));
        assertEquals("0000000255", Gb28181SipOutboundClient.gbSsrc("FF"));
    }

    @Test
    void tcpSdpAsksCameraToConnect() {
        String sdp = Gb28181SipOutboundClient.buildInviteSdp("119.3.161.125", 7096, "0536870913", true);
        assertTrue(sdp.contains("m=video 7096 TCP/RTP/AVP 96\r\n"));
        assertTrue(sdp.contains("a=setup:passive\r\n"));
        assertTrue(sdp.contains("a=connection:new\r\n"));
        assertTrue(!sdp.contains("m=video 7096 RTP/AVP 96\r\n"));
    }
}
