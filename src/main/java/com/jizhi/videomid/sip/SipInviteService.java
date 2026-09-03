package com.jizhi.videomid.sip;

import com.jizhi.videomid.config.SipProperties;
import com.jizhi.videomid.device.entity.SipPlatformEntity;
import com.jizhi.videomid.device.service.DeviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.SipProvider;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.*;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Play / Playback INVITE 与 BYE。
 * SDP 中 RTP 端口必须为 ZLM openRtpServer 返回的端口。
 */
@Service
public class SipInviteService {

    private static final Logger log = LoggerFactory.getLogger(SipInviteService.class);

    private final SipServer sipServer;
    private final DeviceService deviceService;
    private final ConcurrentHashMap<String, String> callIdByChannel = new ConcurrentHashMap<>();

    public SipInviteService(SipServer sipServer, DeviceService deviceService) {
        this.sipServer = sipServer;
        this.deviceService = deviceService;
    }

    public void invitePlay(String channelId, String streamId, int rtpPort) {
        invite(channelId, streamId, rtpPort, false, null, null);
    }

    public void invitePlayback(String channelId, String streamId, int rtpPort,
                               String startTime, String endTime) {
        invite(channelId, streamId, rtpPort, true, startTime, endTime);
    }

    private void invite(String channelId, String streamId, int rtpPort,
                        boolean playback, String startTime, String endTime) {
        try {
            SipProperties sip = sipServer.getSipProperties();
            SipProvider provider = sipServer.getSipProvider();
            if (provider == null) {
                throw new IllegalStateException("SIP server not started");
            }
            AddressFactory af = sipServer.getAddressFactory();
            HeaderFactory hf = sipServer.getHeaderFactory();
            MessageFactory mf = sipServer.getMessageFactory();

            SipPlatformEntity platform = resolvePlatform(channelId);
            String ip = platform.getIp();
            int port = platform.getPort() == null ? 5060 : platform.getPort();
            String transport = platform.getTransport() == null ? "udp" : platform.getTransport().toLowerCase();

            String ssrc = SdpBuilder.generateSsrc(playback);
            String sdp = playback
                    ? SdpBuilder.buildPlaybackSdp(sip, channelId, ssrc, rtpPort, startTime, endTime)
                    : SdpBuilder.buildPlaySdp(sip, channelId, ssrc, rtpPort);

            String subject = channelId + ":" + ssrc + "," + sip.getServerId() + ":0";

            SipURI requestUri = af.createSipURI(channelId, ip + ":" + port);
            requestUri.setTransportParam(transport);

            Address fromAddress = af.createAddress(
                    af.createSipURI(sip.getServerId(), sip.getPublicIp() + ":" + sip.getPort()));
            FromHeader fromHeader = hf.createFromHeader(fromAddress, String.valueOf(System.nanoTime()));

            Address toAddress = af.createAddress(af.createSipURI(channelId, ip + ":" + port));
            ToHeader toHeader = hf.createToHeader(toAddress, null);

            ArrayList<ViaHeader> viaHeaders = new ArrayList<>();
            ViaHeader via = hf.createViaHeader(sip.getPublicIp(), sip.getPort(), transport, null);
            via.setRPort();
            viaHeaders.add(via);

            CallIdHeader callIdHeader = provider.getNewCallId();
            CSeqHeader cSeqHeader = hf.createCSeqHeader(sipServer.nextCseq(), Request.INVITE);
            MaxForwardsHeader maxForwards = hf.createMaxForwardsHeader(70);
            ContentTypeHeader contentType = hf.createContentTypeHeader("APPLICATION", "SDP");

            ContactHeader contact = hf.createContactHeader(
                    af.createAddress(af.createSipURI(sip.getServerId(),
                            sip.getPublicIp() + ":" + sip.getPort())));

            SubjectHeader subjectHeader = hf.createSubjectHeader(subject);

            Request request = mf.createRequest(
                    requestUri, Request.INVITE, callIdHeader, cSeqHeader,
                    fromHeader, toHeader, viaHeaders, maxForwards);
            request.addHeader(contact);
            request.addHeader(subjectHeader);
            request.setContent(sdp.getBytes(StandardCharsets.UTF_8), contentType);

            ClientTransaction tx = provider.getNewClientTransaction(request);
            sipServer.registerInviteTx(channelId, tx);
            callIdByChannel.put(channelId, callIdHeader.getCallId());
            tx.sendRequest();
            Dialog dialog = tx.getDialog();
            sipServer.registerDialog(channelId, dialog);

            log.info("SIP INVITE {} channelId={} rtpPort={} streamId={} peer={}:{}",
                    playback ? "Playback" : "Play", channelId, rtpPort, streamId, ip, port);
        } catch (Exception e) {
            log.error("SIP INVITE failed channelId={}", channelId, e);
            throw new IllegalStateException("SIP INVITE failed: " + e.getMessage(), e);
        }
    }

    public void sendBye(String channelId) {
        try {
            Dialog dialog = sipServer.getDialog(channelId);
            if (dialog == null) {
                log.warn("No dialog for channel {}, skip BYE", channelId);
                return;
            }
            Request bye = dialog.createRequest(Request.BYE);
            ClientTransaction tx = sipServer.getSipProvider().getNewClientTransaction(bye);
            dialog.sendRequest(tx);
            sipServer.removeDialog(channelId);
            callIdByChannel.remove(channelId);
            log.info("SIP BYE sent for channel {}", channelId);
        } catch (Exception e) {
            log.warn("SIP BYE failed for {}: {}", channelId, e.getMessage());
            sipServer.removeDialog(channelId);
        }
    }

    private SipPlatformEntity resolvePlatform(String channelId) {
        Optional<SipPlatformEntity> byDevice = deviceService.findByDeviceId(channelId)
                .flatMap(d -> {
                    String pid = d.getPlatformId();
                    if (pid != null && !pid.isBlank()) {
                        return deviceService.findPlatform(pid);
                    }
                    return Optional.empty();
                });
        if (byDevice.isPresent()) {
            return byDevice.get();
        }
        String prefix = channelId.length() >= 20 ? channelId.substring(0, 20) : channelId;
        return deviceService.findPlatform(prefix)
                .or(() -> deviceService.findPlatform(channelId))
                .or(deviceService::findAnyOnlinePlatform)
                .orElseThrow(() -> new IllegalStateException(
                        "无可用下级平台，请先让宇视向本中台完成 SIP 注册: channel=" + channelId));
    }
}
