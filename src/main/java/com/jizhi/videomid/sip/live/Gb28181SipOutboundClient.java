package com.jizhi.videomid.sip.live;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.DialogState;
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.SubjectHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/** 向已 REGISTER 下级发送 MESSAGE / INVITE / BYE */
@Component
@ConditionalOnProperty(name = "gb28181.data-source", havingValue = "live")
public class Gb28181SipOutboundClient {

    private static final Logger log = LoggerFactory.getLogger(Gb28181SipOutboundClient.class);
    private static final Charset GB2312 = Charset.forName("GB2312");

    private final Gb28181SipSessionStore sessionStore;
    private final Gb28181InviteSessionStore inviteStore;
    private final AtomicLong cseqSeq = new AtomicLong(1);
    private Gb28181SipStackManager stackManager;

    public Gb28181SipOutboundClient(Gb28181SipSessionStore sessionStore,
                                    Gb28181InviteSessionStore inviteStore) {
        this.sessionStore = sessionStore;
        this.inviteStore = inviteStore;
    }

    void bind(Gb28181SipStackManager stackManager) {
        this.stackManager = stackManager;
    }

    public boolean isReady() {
        return stackManager != null && stackManager.isRunning();
    }

    public void sendCatalogQuery(String deviceId) {
        if (!isReady()) {
            return;
        }
        Optional<Gb28181SipSession> session = sessionStore.findByDeviceId(deviceId);
        if (session.isEmpty()) {
            log.warn("[GB28181-SIP] Catalog Query 跳过，设备未注册: {}", deviceId);
            return;
        }
        try {
            String body = Gb28181XmlHelper.buildCatalogQuery(deviceId, stackManager.nextSn());
            sendMessage(session.get(), deviceId, body);
            log.info("[GB28181-SIP] 已发送 Catalog Query deviceId={}", deviceId);
        } catch (Exception e) {
            log.warn("[GB28181-SIP] Catalog Query 失败 deviceId={} err={}", deviceId, e.getMessage());
        }
    }

    public void sendKeepalive(String deviceId) {
        if (!isReady()) {
            return;
        }
        sessionStore.findByDeviceId(deviceId).ifPresent(session -> {
            try {
                String body = Gb28181XmlHelper.buildKeepaliveNotify(
                        stackManager.upperSipId(), stackManager.nextSn());
                sendMessage(session, deviceId, body);
            } catch (Exception e) {
                log.debug("[GB28181-SIP] Keepalive 发送失败 deviceId={}", deviceId);
            }
        });
    }

    public Map<String, Object> sendPlayInvite(String channelId,
                                              String gbDeviceId,
                                              int rtpPort,
                                              String ssrcHex,
                                              String streamId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sent", false);
        if (!isReady()) {
            result.put("reason", "SIP 栈未运行");
            return result;
        }
        Optional<Gb28181SipSession> session = sessionStore.findByDeviceId(gbDeviceId);
        if (session.isEmpty()) {
            result.put("reason", "下级未 REGISTER: " + gbDeviceId);
            return result;
        }
        try {
            String sdp = buildInviteSdp(stackManager.mediaIp(), rtpPort, ssrcHex);
            Request invite = buildInviteRequest(session.get(), channelId, sdp);
            CallIdHeader callId = (CallIdHeader) invite.getHeader(CallIdHeader.NAME);
            inviteStore.register(new Gb28181InviteSession(
                    channelId, gbDeviceId, callId.getCallId(), rtpPort, streamId, ssrcHex));
            ClientTransaction tx = stackManager.sipProvider().getNewClientTransaction(invite);
            tx.sendRequest();
            result.put("sent", true);
            result.put("callId", callId.getCallId());
            result.put("channelId", channelId);
            result.put("gbDeviceId", gbDeviceId);
            result.put("rtpPort", rtpPort);
            result.put("ssrc", ssrcHex);
            result.put("contact", session.get().getContactHost() + ":" + session.get().getContactPort());
            log.info("[GB28181-SIP] INVITE 已发送 channelId={} deviceId={} rtpPort={}",
                    channelId, gbDeviceId, rtpPort);
        } catch (Exception e) {
            result.put("reason", e.getMessage());
            log.warn("[GB28181-SIP] INVITE 失败 channelId={} err={}", channelId, e.getMessage());
        }
        return result;
    }

    public Map<String, Object> sendBye(String channelId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stopped", false);
        Optional<Gb28181InviteSession> sessionOpt = inviteStore.findByChannelId(channelId);
        if (sessionOpt.isEmpty()) {
            result.put("reason", "无活跃 INVITE 会话");
            return result;
        }
        Gb28181InviteSession session = sessionOpt.get();
        try {
            Dialog dialog = session.getDialog();
            if (dialog != null && dialog.getState() == DialogState.CONFIRMED) {
                Request bye = dialog.createRequest(Request.BYE);
                ClientTransaction tx = stackManager.sipProvider().getNewClientTransaction(bye);
                tx.sendRequest();
            }
            session.markStopped();
            inviteStore.remove(channelId);
            result.put("stopped", true);
            result.put("channelId", channelId);
            log.info("[GB28181-SIP] BYE 已发送 channelId={}", channelId);
        } catch (Exception e) {
            result.put("reason", e.getMessage());
        }
        return result;
    }

    private void sendMessage(Gb28181SipSession session, String deviceId, String body) throws Exception {
        Request request = buildRequestBase(session, deviceId, Request.MESSAGE);
        ContentTypeHeader ct = stackManager.headerFactory().createContentTypeHeader("Application", "MANSCDP+xml");
        request.setContent(body.getBytes(GB2312), ct);
        ClientTransaction tx = stackManager.sipProvider().getNewClientTransaction(request);
        tx.sendRequest();
    }

    private Request buildInviteRequest(Gb28181SipSession session, String channelId, String sdp) throws Exception {
        Request request = buildRequestBase(session, channelId, Request.INVITE);
        ContentTypeHeader ct = stackManager.headerFactory().createContentTypeHeader("application", "sdp");
        request.setContent(sdp.getBytes(), ct);
        SubjectHeader subject = stackManager.headerFactory().createSubjectHeader(channelId + ":0,0");
        request.addHeader(subject);
        return request;
    }

    private Request buildRequestBase(Gb28181SipSession session, String targetDeviceId, String method) throws Exception {
        String domain = stackManager.sipDomain();
        String upperId = stackManager.upperSipId();

        SipURI requestUri = stackManager.addressFactory().createSipURI(targetDeviceId, domain);
        requestUri.setPort(session.getContactPort());
        requestUri.setHost(session.getContactHost());
        requestUri.setTransportParam(session.getTransport().toLowerCase());

        Address fromAddress = stackManager.addressFactory().createAddress(
                stackManager.addressFactory().createSipURI(upperId, domain));
        FromHeader from = stackManager.headerFactory().createFromHeader(fromAddress, tag());

        Address toAddress = stackManager.addressFactory().createAddress(
                stackManager.addressFactory().createSipURI(targetDeviceId, domain));
        ToHeader to = stackManager.headerFactory().createToHeader(toAddress, null);

        List<ViaHeader> viaHeaders = new ArrayList<>();
        String host = stackManager.mediaIp();
        int port = stackManager.sipProvider().getListeningPoint("udp").getPort();
        ViaHeader via = stackManager.headerFactory().createViaHeader(host, port, "udp", tag());
        viaHeaders.add(via);

        CallIdHeader callId = stackManager.sipProvider().getNewCallId();
        CSeqHeader cseq = stackManager.headerFactory().createCSeqHeader(cseqSeq.getAndIncrement(), method);
        MaxForwardsHeader maxForwards = stackManager.headerFactory().createMaxForwardsHeader(70);

        Request request = stackManager.messageFactory().createRequest(
                requestUri, method, callId, cseq, from, to, viaHeaders, maxForwards);

        SipURI contactUri = stackManager.addressFactory().createSipURI(upperId, host);
        contactUri.setPort(port);
        Address contactAddress = stackManager.addressFactory().createAddress(contactUri);
        ContactHeader contact = stackManager.headerFactory().createContactHeader(contactAddress);
        request.addHeader(contact);
        return request;
    }

    static String buildInviteSdp(String mediaIp, int rtpPort, String ssrcHex) {
        String y = ssrcHex == null ? "0100000001" : ssrcHex.replaceAll("^0x", "");
        return "v=0\r\n"
                + "o=34020000002000000001 0 0 IN IP4 " + mediaIp + "\r\n"
                + "s=Play\r\n"
                + "c=IN IP4 " + mediaIp + "\r\n"
                + "t=0 0\r\n"
                + "m=video " + rtpPort + " RTP/AVP 96\r\n"
                + "a=rtpmap:96 PS/90000\r\n"
                + "a=sendonly\r\n"
                + "y=" + y + "\r\n";
    }

    private static String tag() {
        return Long.toHexString(System.nanoTime());
    }
}
