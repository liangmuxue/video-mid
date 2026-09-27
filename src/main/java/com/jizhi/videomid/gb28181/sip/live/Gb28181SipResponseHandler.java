package com.jizhi.videomid.gb28181.sip.live;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.ResponseEvent;
import javax.sip.header.CSeqHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

@Component
@ConditionalOnProperty(name = "gb28181.data-source", havingValue = "live")
public class Gb28181SipResponseHandler {

    private static final Logger log = LoggerFactory.getLogger(Gb28181SipResponseHandler.class);

    private final Gb28181InviteSessionStore inviteStore;

    public Gb28181SipResponseHandler(Gb28181InviteSessionStore inviteStore) {
        this.inviteStore = inviteStore;
    }

    public void handle(ResponseEvent event) {
        Response response = event.getResponse();
        if (response == null) {
            return;
        }
        CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
        if (cseq == null) {
            return;
        }
        String method = cseq.getMethod();
        int status = response.getStatusCode();
        if (Request.INVITE.equals(method)) {
            handleInviteResponse(event, status);
        } else if (status >= 300 && Request.MESSAGE.equals(method)) {
            log.warn("[GB28181-SIP] MESSAGE 失败 status={}", status);
        }
    }

    private void handleInviteResponse(ResponseEvent event, int status) {
        Response response = event.getResponse();
        javax.sip.header.CallIdHeader callIdHeader =
                (javax.sip.header.CallIdHeader) response.getHeader(javax.sip.header.CallIdHeader.NAME);
        if (callIdHeader == null) {
            return;
        }
        Gb28181InviteSession session = inviteStore.findByCallId(callIdHeader.getCallId()).orElse(null);
        if (session == null) {
            return;
        }
        if (status == Response.TRYING || status == Response.RINGING) {
            return;
        }
        if (status == Response.OK) {
            try {
                ClientTransaction ct = event.getClientTransaction();
                if (ct == null) {
                    session.markFailed("无 ClientTransaction");
                    return;
                }
                Dialog dialog = ct.getDialog();
                if (dialog == null) {
                    session.markFailed("无 Dialog，无法 ACK");
                    return;
                }
                session.setDialog(dialog);
                CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
                Request ack = dialog.createAck(cseq.getSeqNumber());
                dialog.sendAck(ack);
                session.markPlaying();
                log.info("[GB28181-SIP] INVITE 200 OK + ACK channelId={}", session.getChannelId());
            } catch (Exception e) {
                session.markFailed(e.getMessage());
                log.warn("[GB28181-SIP] INVITE ACK 失败 channelId={} err={}",
                        session.getChannelId(), e.getMessage());
            }
            return;
        }
        if (status >= 300) {
            session.markFailed("SIP " + status + " " + response.getReasonPhrase());
            log.warn("[GB28181-SIP] INVITE 失败 channelId={} status={}", session.getChannelId(), status);
        }
    }
}
