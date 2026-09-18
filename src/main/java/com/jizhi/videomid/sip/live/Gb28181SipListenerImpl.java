package com.jizhi.videomid.sip.live;

import com.jizhi.videomid.config.Gb28181Properties;
import com.jizhi.videomid.gb28181.live.ZlmRtpBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipListener;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.address.SipURI;
import javax.sip.header.AuthorizationHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ExpiresHeader;
import javax.sip.header.WWWAuthenticateHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "gb28181.data-source", havingValue = "live")
public class Gb28181SipListenerImpl implements SipListener {

    private static final Logger log = LoggerFactory.getLogger(Gb28181SipListenerImpl.class);
    private static final Charset GB2312 = Charset.forName("GB2312");

    private final Gb28181Properties props;
    private final Gb28181SipSessionStore sessionStore;
    private final Gb28181CatalogMerger catalogMerger;
    private final Gb28181CatalogBuffer catalogBuffer;
    private final Gb28181SipOutboundClient outboundClient;
    private final Gb28181DigestAuth digestAuth;
    private final Gb28181SipResponseHandler responseHandler;
    private final Gb28181InviteSessionStore inviteStore;
    private final ZlmRtpBridge rtpBridge;

    private Gb28181SipStackManager stackManager;

    public Gb28181SipListenerImpl(Gb28181Properties props,
                                    Gb28181SipSessionStore sessionStore,
                                    Gb28181CatalogMerger catalogMerger,
                                    Gb28181CatalogBuffer catalogBuffer,
                                    Gb28181SipOutboundClient outboundClient,
                                    Gb28181DigestAuth digestAuth,
                                    Gb28181SipResponseHandler responseHandler,
                                    Gb28181InviteSessionStore inviteStore,
                                    ZlmRtpBridge rtpBridge) {
        this.props = props;
        this.sessionStore = sessionStore;
        this.catalogMerger = catalogMerger;
        this.catalogBuffer = catalogBuffer;
        this.outboundClient = outboundClient;
        this.digestAuth = digestAuth;
        this.responseHandler = responseHandler;
        this.inviteStore = inviteStore;
        this.rtpBridge = rtpBridge;
    }

    void bind(Gb28181SipStackManager stackManager) {
        this.stackManager = stackManager;
        outboundClient.bind(stackManager);
    }

    @Override
    public void processRequest(RequestEvent event) {
        Request request = event.getRequest();
        String method = request.getMethod();
        try {
            switch (method) {
                case Request.REGISTER -> handleRegister(event);
                case Request.MESSAGE -> handleMessage(event);
                case Request.BYE -> handleBye(event);
                default -> sendResponse(event, Response.NOT_IMPLEMENTED);
            }
        } catch (Exception e) {
            log.warn("[GB28181-SIP] 处理 {} 失败: {}", method, e.getMessage());
            try {
                sendResponse(event, Response.SERVER_INTERNAL_ERROR);
            } catch (Exception ignored) {
            }
        }
    }

    private void handleRegister(RequestEvent event) throws Exception {
        Request request = event.getRequest();
        String deviceId = sipUser(request);

        if (props.getLive().isEnableDigestAuth()) {
            AuthorizationHeader auth = (AuthorizationHeader) request.getHeader(AuthorizationHeader.NAME);
            if (auth == null) {
                sendUnauthorized(event);
                return;
            }
            if (!digestAuth.verify(request, auth, props.getUpper().getPassword())) {
                sendResponse(event, Response.FORBIDDEN);
                log.warn("[GB28181-SIP] REGISTER 鉴权失败 deviceId={}", deviceId);
                return;
            }
        }

        long expires = props.getUpper().getRegisterExpires();
        ExpiresHeader expiresHeader = (ExpiresHeader) request.getHeader(ExpiresHeader.NAME);
        if (expiresHeader != null) {
            expires = expiresHeader.getExpires();
        }

        if (expires <= 0) {
            sessionStore.remove(deviceId);
            sendResponse(event, Response.OK);
            log.info("[GB28181-SIP] REGISTER 注销 deviceId={}", deviceId);
            return;
        }

        ContactHeader contact = (ContactHeader) request.getHeader(ContactHeader.NAME);
        if (contact == null) {
            sendResponse(event, Response.BAD_REQUEST);
            return;
        }

        SipURI uri = (SipURI) contact.getAddress().getURI();
        String host = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : 5060;
        String transport = uri.getTransportParam();
        if (transport == null || transport.isBlank()) {
            transport = "UDP";
        }

        Gb28181SipSession session = new Gb28181SipSession(
                deviceId, host, port, transport, contact.toString(), expires);
        sessionStore.upsert(session);
        sendResponse(event, Response.OK);
        log.info("[GB28181-SIP] REGISTER 成功 deviceId={} contact={}:{} expires={}",
                deviceId, host, port, expires);

        if (props.getLive().isAutoCatalogQuery()) {
            outboundClient.sendCatalogQuery(deviceId);
        }
    }

    private void handleMessage(RequestEvent event) throws Exception {
        Request request = event.getRequest();
        byte[] raw = request.getRawContent();
        String body = raw == null ? "" : new String(raw, GB2312);

        if (Gb28181XmlHelper.isCatalogResponse(body)) {
            String rootId = Gb28181XmlHelper.tagValue(body, "DeviceID");
            if (rootId.isBlank()) {
                rootId = sipUser(request);
            }
            Optional<List<Map<String, String>>> complete = catalogBuffer.accept(rootId, body);
            if (complete.isPresent()) {
                catalogMerger.mergeCatalogResponse(rootId, complete.get());
                log.info("[GB28181-SIP] Catalog 完成 deviceId={} items={}", rootId, complete.get().size());
            } else {
                int sum = Gb28181XmlHelper.parseSumNum(body);
                log.info("[GB28181-SIP] Catalog 分包 deviceId={} sumNum={}", rootId, sum);
            }
        } else if (Gb28181XmlHelper.isKeepalive(body)) {
            log.debug("[GB28181-SIP] Keepalive from {}", sipUser(request));
        }

        sendResponse(event, Response.OK);
    }

    private void handleBye(RequestEvent event) throws Exception {
        Request request = event.getRequest();
        javax.sip.header.CallIdHeader callIdHeader =
                (javax.sip.header.CallIdHeader) request.getHeader(javax.sip.header.CallIdHeader.NAME);
        if (callIdHeader != null) {
            inviteStore.findByCallId(callIdHeader.getCallId()).ifPresent(session -> {
                rtpBridge.closeReceive(session.getChannelId());
                session.markStopped();
                inviteStore.remove(session.getChannelId());
                log.info("[GB28181-SIP] BYE 收到，已关闭 RTP channelId={}", session.getChannelId());
            });
        }
        sendResponse(event, Response.OK);
    }

    private void sendUnauthorized(RequestEvent event) throws Exception {
        Request request = event.getRequest();
        Response response = stackManager.messageFactory().createResponse(Response.UNAUTHORIZED, request);
        copyHeaders(request, response);
        WWWAuthenticateHeader www = stackManager.headerFactory().createWWWAuthenticateHeader("Digest");
        www.setParameter("realm", props.getUpper().getSipDomain());
        www.setParameter("nonce", digestAuth.issueNonce());
        response.addHeader(www);
        ServerTransaction tx = event.getServerTransaction();
        if (tx == null) {
            tx = stackManager.sipProvider().getNewServerTransaction(request);
        }
        tx.sendResponse(response);
    }

    private void sendResponse(RequestEvent event, int status) throws Exception {
        Response response = stackManager.messageFactory().createResponse(status, event.getRequest());
        copyHeaders(event.getRequest(), response);
        ServerTransaction tx = event.getServerTransaction();
        if (tx == null) {
            tx = stackManager.sipProvider().getNewServerTransaction(event.getRequest());
        }
        tx.sendResponse(response);
    }

    private static void copyHeaders(Request request, Response response) {
        for (String name : List.of("Via", "From", "To", "Call-ID", "CSeq")) {
            Iterator<?> it = request.getHeaders(name);
            while (it.hasNext()) {
                response.addHeader((javax.sip.header.Header) it.next());
            }
        }
    }

    private static String sipUser(Request request) {
        javax.sip.header.FromHeader from = (javax.sip.header.FromHeader) request.getHeader("From");
        if (from == null) {
            return "unknown";
        }
        SipURI uri = (SipURI) from.getAddress().getURI();
        return uri.getUser();
    }

    @Override
    public void processResponse(ResponseEvent responseEvent) {
        responseHandler.handle(responseEvent);
    }

    @Override
    public void processTimeout(javax.sip.TimeoutEvent timeoutEvent) {
        log.debug("[GB28181-SIP] 事务超时");
    }

    @Override
    public void processIOException(javax.sip.IOExceptionEvent exceptionEvent) {
        log.warn("[GB28181-SIP] IO 异常");
    }

    @Override
    public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {
    }

    @Override
    public void processDialogTerminated(javax.sip.DialogTerminatedEvent dialogTerminatedEvent) {
    }
}
