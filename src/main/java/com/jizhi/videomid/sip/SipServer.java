package com.jizhi.videomid.sip;

import com.jizhi.videomid.config.SipProperties;
import com.jizhi.videomid.device.service.CatalogParser;
import com.jizhi.videomid.device.service.DeviceService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sip.*;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.*;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * GB/T 28181 国标上级：JAIN-SIP 实现。
 * 接收下级宇视注册、心跳、保活、注销；处理 MESSAGE(Catalog/Keepalive)；
 * 由 SipInviteService 发 INVITE/BYE。
 */
@Component
public class SipServer implements SipListener {

    private static final Logger log = LoggerFactory.getLogger(SipServer.class);

    private final SipProperties sipProperties;
    private final DeviceService deviceService;
    private final CatalogParser catalogParser;

    private SipStack sipStack;
    private SipProvider sipProvider;
    private AddressFactory addressFactory;
    private HeaderFactory headerFactory;
    private MessageFactory messageFactory;
    private ListeningPoint listeningPoint;

    private final AtomicLong cseqCounter = new AtomicLong(1);
    private final Map<String, ClientTransaction> inviteTxMap = new ConcurrentHashMap<>();
    private final Map<String, Dialog> dialogMap = new ConcurrentHashMap<>();
    private final Map<String, String> authNonceMap = new ConcurrentHashMap<>();

    public SipServer(SipProperties sipProperties,
                     DeviceService deviceService,
                     CatalogParser catalogParser) {
        this.sipProperties = sipProperties;
        this.deviceService = deviceService;
        this.catalogParser = catalogParser;
    }

    @PostConstruct
    public void start() throws Exception {
        if (!sipProperties.isEnabled()) {
            log.warn("SIP server disabled");
            return;
        }
        SipFactory sipFactory = SipFactory.getInstance();
        sipFactory.setPathName("gov.nist");
        Properties properties = new Properties();
        properties.setProperty("javax.sip.STACK_NAME", "video-mid-gb28181");
        properties.setProperty("gov.nist.javax.sip.LOG_MESSAGE_CONTENT", "false");
        properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "0");

        sipStack = sipFactory.createSipStack(properties);
        addressFactory = sipFactory.createAddressFactory();
        headerFactory = sipFactory.createHeaderFactory();
        messageFactory = sipFactory.createMessageFactory();

        String transport = sipProperties.getTransport() == null ? "udp" : sipProperties.getTransport();
        listeningPoint = sipStack.createListeningPoint("0.0.0.0", sipProperties.getPort(), transport);
        sipProvider = sipStack.createSipProvider(listeningPoint);
        sipProvider.addSipListener(this);
        log.info("SIP GB/T28181 server started on 0.0.0.0:{} ({})", sipProperties.getPort(), transport);
    }

    @PreDestroy
    public void stop() {
        try {
            if (sipStack != null) {
                sipStack.stop();
            }
        } catch (Exception e) {
            log.warn("SIP stop error: {}", e.getMessage());
        }
    }

    public SipProvider getSipProvider() {
        return sipProvider;
    }

    public AddressFactory getAddressFactory() {
        return addressFactory;
    }

    public HeaderFactory getHeaderFactory() {
        return headerFactory;
    }

    public MessageFactory getMessageFactory() {
        return messageFactory;
    }

    public SipProperties getSipProperties() {
        return sipProperties;
    }

    public long nextCseq() {
        return cseqCounter.getAndIncrement();
    }

    public void registerDialog(String channelId, Dialog dialog) {
        if (dialog != null) {
            dialogMap.put(channelId, dialog);
        }
    }

    public Dialog getDialog(String channelId) {
        return dialogMap.get(channelId);
    }

    public void removeDialog(String channelId) {
        dialogMap.remove(channelId);
        inviteTxMap.remove(channelId);
    }

    public void registerInviteTx(String channelId, ClientTransaction tx) {
        inviteTxMap.put(channelId, tx);
    }

    @Override
    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        String method = request.getMethod();
        try {
            switch (method) {
                case Request.REGISTER -> handleRegister(requestEvent);
                case Request.MESSAGE -> handleMessage(requestEvent);
                case Request.BYE -> handleBye(requestEvent);
                case Request.ACK -> log.debug("SIP ACK received");
                default -> {
                    log.info("SIP unsupported method {}, reply 200", method);
                    reply(requestEvent, Response.OK);
                }
            }
        } catch (Exception e) {
            log.error("processRequest error method={}", method, e);
            try {
                reply(requestEvent, Response.SERVER_INTERNAL_ERROR);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void processResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        log.debug("SIP response {} {}", response.getStatusCode(),
                response.getHeader(CSeqHeader.NAME) != null
                        ? ((CSeqHeader) response.getHeader(CSeqHeader.NAME)).getMethod() : "");
        ClientTransaction ct = responseEvent.getClientTransaction();
        if (ct != null && response.getStatusCode() == Response.OK
                && Request.INVITE.equals(ct.getRequest().getMethod())) {
            Dialog dialog = ct.getDialog();
            if (dialog != null) {
                try {
                    CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
                    Request ack = dialog.createAck(cseq.getSeqNumber());
                    dialog.sendAck(ack);
                } catch (Exception e) {
                    log.warn("send ACK failed: {}", e.getMessage());
                }
            }
        }
    }

    @Override
    public void processTimeout(TimeoutEvent timeoutEvent) {
        log.warn("SIP timeout: {}", timeoutEvent);
    }

    @Override
    public void processIOException(IOExceptionEvent exceptionEvent) {
        log.warn("SIP IOException: {}", exceptionEvent);
    }

    @Override
    public void processTransactionTerminated(TransactionTerminatedEvent tte) {
        // no-op
    }

    @Override
    public void processDialogTerminated(DialogTerminatedEvent dte) {
        // no-op
    }

    private void handleRegister(RequestEvent event) throws Exception {
        Request request = event.getRequest();
        FromHeader from = (FromHeader) request.getHeader(FromHeader.NAME);
        SipURI fromUri = (SipURI) from.getAddress().getURI();
        String platformId = fromUri.getUser();

        AuthorizationHeader auth = (AuthorizationHeader) request.getHeader(AuthorizationHeader.NAME);
        ExpiresHeader expiresHeader = (ExpiresHeader) request.getHeader(ExpiresHeader.NAME);
        int expires = expiresHeader != null ? expiresHeader.getExpires() : 3600;

        if (auth == null && expires > 0) {
            String nonce = UUID.randomUUID().toString().replace("-", "");
            authNonceMap.put(platformId, nonce);
            Response response = messageFactory.createResponse(Response.UNAUTHORIZED, request);
            WWWAuthenticateHeader www = headerFactory.createWWWAuthenticateHeader("Digest");
            www.setParameter("realm", sipProperties.getDomain());
            www.setParameter("nonce", nonce);
            www.setParameter("algorithm", "MD5");
            response.addHeader(www);
            sendResponse(event, response);
            return;
        }

        if (expires == 0) {
            deviceService.markPlatformOffline(platformId);
            authNonceMap.remove(platformId);
            replyOkWithToTag(event, 0);
            log.info("Platform {} unregistered", platformId);
            return;
        }

        if (auth != null && !validateDigest(auth, platformId, request.getMethod())) {
            reply(event, Response.FORBIDDEN);
            return;
        }

        ViaHeader via = (ViaHeader) request.getHeader(ViaHeader.NAME);
        String ip = via.getReceived() != null ? via.getReceived() : via.getHost();
        int port = via.getRPort() > 0 ? via.getRPort() : via.getPort();
        if (port <= 0) {
            port = 5060;
        }
        ContactHeader contact = (ContactHeader) request.getHeader(ContactHeader.NAME);
        if (contact != null && contact.getAddress().getURI() instanceof SipURI contactUri) {
            if (contactUri.getHost() != null) {
                ip = contactUri.getHost();
            }
            if (contactUri.getPort() > 0) {
                port = contactUri.getPort();
            }
        }

        deviceService.markPlatformOnline(platformId, ip, port, via.getTransport(), sipProperties.getDomain());
        replyOkWithToTag(event, expires);
        log.info("Platform {} registered from {}:{}", platformId, ip, port);

        try {
            queryCatalog(platformId, ip, port);
        } catch (Exception e) {
            log.warn("Auto Catalog after register failed: {}", e.getMessage());
        }
    }

    private boolean validateDigest(AuthorizationHeader auth, String username, String method) {
        try {
            String nonce = auth.getNonce();
            String realm = auth.getRealm() != null ? auth.getRealm() : sipProperties.getDomain();
            String uri = auth.getURI() != null ? auth.getURI().toString() : "";
            String response = auth.getResponse();
            String ha1 = md5(username + ":" + realm + ":" + sipProperties.getPassword());
            String ha2 = md5(method + ":" + uri);
            String calc = md5(ha1 + ":" + nonce + ":" + ha2);
            return calc.equalsIgnoreCase(response);
        } catch (Exception e) {
            log.warn("Digest validate error: {}", e.getMessage());
            return false;
        }
    }

    private void handleMessage(RequestEvent event) throws Exception {
        Request request = event.getRequest();
        byte[] raw = request.getRawContent();
        String body = raw == null ? "" : new String(raw, StandardCharsets.UTF_8);
        reply(event, Response.OK);

        String cmdType = extractXmlTag(body, "CmdType");
        String deviceId = extractXmlTag(body, "DeviceID");
        if ("Keepalive".equalsIgnoreCase(cmdType)) {
            if (deviceId != null) {
                deviceService.keepalive(deviceId);
            }
            log.debug("Keepalive from {}", deviceId);
            return;
        }
        if ("Catalog".equalsIgnoreCase(cmdType)) {
            String sn = extractXmlTag(body, "SN");
            log.info("Catalog response SN={} DeviceID={}", sn, deviceId);
            catalogParser.parseAndSave(body, deviceId);
            return;
        }
        log.debug("SIP MESSAGE CmdType={} ignored", cmdType);
    }

    private void handleBye(RequestEvent event) throws Exception {
        reply(event, Response.OK);
        log.info("SIP BYE received from peer");
    }

    public void queryCatalog(String platformId, String ip, int port) throws Exception {
        String sn = String.valueOf(System.currentTimeMillis() % 1000000000L);
        String xml = "<?xml version=\"1.0\" encoding=\"GB2312\"?>\r\n"
                + "<Query>\r\n"
                + "<CmdType>Catalog</CmdType>\r\n"
                + "<SN>" + sn + "</SN>\r\n"
                + "<DeviceID>" + platformId + "</DeviceID>\r\n"
                + "</Query>\r\n";
        sendMessage(platformId, ip, port, xml);
        log.info("Sent Catalog query to platform {} {}:{}", platformId, ip, port);
    }

    public void sendMessage(String deviceId, String ip, int port, String xmlBody) throws Exception {
        String transport = sipProperties.getTransport();
        SipURI requestUri = addressFactory.createSipURI(deviceId, ip + ":" + port);
        requestUri.setTransportParam(transport);

        Address fromAddress = addressFactory.createAddress(
                addressFactory.createSipURI(sipProperties.getServerId(),
                        sipProperties.getPublicIp() + ":" + sipProperties.getPort()));
        FromHeader fromHeader = headerFactory.createFromHeader(fromAddress, String.valueOf(System.nanoTime()));

        Address toAddress = addressFactory.createAddress(requestUri);
        ToHeader toHeader = headerFactory.createToHeader(toAddress, null);

        ArrayList<ViaHeader> viaHeaders = new ArrayList<>();
        ViaHeader via = headerFactory.createViaHeader(
                sipProperties.getPublicIp(), sipProperties.getPort(), transport, null);
        via.setRPort();
        viaHeaders.add(via);

        CallIdHeader callId = sipProvider.getNewCallId();
        CSeqHeader cSeq = headerFactory.createCSeqHeader(nextCseq(), Request.MESSAGE);
        MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);
        ContentTypeHeader contentType = headerFactory.createContentTypeHeader("Application", "MANSCDP+xml");

        Request request = messageFactory.createRequest(
                requestUri, Request.MESSAGE, callId, cSeq, fromHeader, toHeader, viaHeaders, maxForwards);
        request.setContent(xmlBody.getBytes(StandardCharsets.UTF_8), contentType);

        ClientTransaction tx = sipProvider.getNewClientTransaction(request);
        tx.sendRequest();
    }

    private void reply(RequestEvent event, int status) throws Exception {
        Response response = messageFactory.createResponse(status, event.getRequest());
        sendResponse(event, response);
    }

    private void replyOkWithToTag(RequestEvent event, int expires) throws Exception {
        Response response = messageFactory.createResponse(Response.OK, event.getRequest());
        ToHeader to = (ToHeader) response.getHeader(ToHeader.NAME);
        if (to.getTag() == null) {
            to.setTag(String.valueOf(System.nanoTime()));
        }
        response.addHeader(headerFactory.createExpiresHeader(expires));
        ContactHeader contact = (ContactHeader) event.getRequest().getHeader(ContactHeader.NAME);
        if (contact != null) {
            response.addHeader((ContactHeader) contact.clone());
        }
        DateHeader date = headerFactory.createDateHeader(Calendar.getInstance());
        response.addHeader(date);
        sendResponse(event, response);
    }

    private void sendResponse(RequestEvent event, Response response) throws Exception {
        ServerTransaction st = event.getServerTransaction();
        if (st == null) {
            st = sipProvider.getNewServerTransaction(event.getRequest());
        }
        st.sendResponse(response);
    }

    private static String extractXmlTag(String xml, String tag) {
        if (xml == null) {
            return null;
        }
        String start = "<" + tag + ">";
        String end = "</" + tag + ">";
        int i = xml.indexOf(start);
        int j = xml.indexOf(end);
        if (i < 0 || j < 0 || j <= i) {
            return null;
        }
        return xml.substring(i + start.length(), j).trim();
    }

    private static String md5(String raw) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] dig = md.digest(raw.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : dig) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
