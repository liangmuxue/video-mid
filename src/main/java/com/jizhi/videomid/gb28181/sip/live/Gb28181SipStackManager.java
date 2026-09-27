package com.jizhi.videomid.gb28181.sip.live;

import com.jizhi.videomid.config.Gb28181Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.sip.ListeningPoint;
import javax.sip.SipFactory;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.address.AddressFactory;
import javax.sip.header.HeaderFactory;
import javax.sip.message.MessageFactory;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@ConditionalOnProperty(name = "gb28181.data-source", havingValue = "live")
public class Gb28181SipStackManager {

    private static final Logger log = LoggerFactory.getLogger(Gb28181SipStackManager.class);

    private final Gb28181Properties props;
    private final AtomicInteger snSeq = new AtomicInteger(1);

    private SipStack sipStack;
    private SipProvider sipProvider;
    private Gb28181SipListenerImpl boundListener;
    private AddressFactory addressFactory;
    private HeaderFactory headerFactory;
    private MessageFactory messageFactory;
    private volatile boolean running;

    public Gb28181SipStackManager(Gb28181Properties props) {
        this.props = props;
    }

    public synchronized void start(Gb28181SipListenerImpl listener) throws Exception {
        if (running) {
            return;
        }
        if (!props.getLive().isEnableSipStack()) {
            log.info("[GB28181-SIP] enable-sip-stack=false，跳过 JAIN-SIP 启动");
            return;
        }

        SipFactory sipFactory = SipFactory.getInstance();
        sipFactory.setPathName("gov.nist");

        Properties stackProps = new Properties();
        stackProps.setProperty("javax.sip.STACK_NAME", "gb28181-upper");
        stackProps.setProperty("gov.nist.javax.sip.MAX_MESSAGE_SIZE", "1048576");
        stackProps.setProperty("gov.nist.javax.sip.THREAD_POOL_SIZE", "16");
        stackProps.setProperty("gov.nist.javax.sip.REENTRANT_LISTENER", "true");
        stackProps.setProperty("gov.nist.javax.sip.MESSAGE_PROCESSOR_FACTORY",
                "gov.nist.javax.sip.stack.NioMessageProcessorFactory");
        stackProps.setProperty("gov.nist.javax.sip.STACK_LOGGER", "gov.nist.core.StackLoggerImpl");
        stackProps.setProperty("gov.nist.javax.sip.SERVER_LOGGER", "gov.nist.core.ServerLoggerImpl");
        stackProps.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "0");

        sipStack = sipFactory.createSipStack(stackProps);
        addressFactory = sipFactory.createAddressFactory();
        headerFactory = sipFactory.createHeaderFactory();
        messageFactory = sipFactory.createMessageFactory();

        String bindIp = props.getLive().getBindIp();
        if (bindIp == null || bindIp.isBlank()) {
            bindIp = "0.0.0.0";
        }
        int port = props.getUpper().getSipPort();
        ListeningPoint lp = sipStack.createListeningPoint(bindIp.trim(), port, ListeningPoint.UDP);
        sipProvider = sipStack.createSipProvider(lp);
        sipProvider.addSipListener(listener);
        listener.bind(this);
        boundListener = listener;

        running = true;
        log.info("[GB28181-SIP] JAIN-SIP 已监听 {}:{} upperId={}",
                bindIp, port, props.getUpper().getSipId());
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }
        try {
            if (sipProvider != null && boundListener != null) {
                sipProvider.removeSipListener(boundListener);
            }
        } catch (Exception ignored) {
        }
        boundListener = null;
        try {
            if (sipStack != null) {
                sipStack.stop();
            }
        } catch (Exception e) {
            log.warn("[GB28181-SIP] stop 异常: {}", e.getMessage());
        }
        running = false;
        sipProvider = null;
        sipStack = null;
    }

    public boolean isRunning() {
        return running;
    }

    public SipProvider sipProvider() {
        return sipProvider;
    }

    public AddressFactory addressFactory() {
        return addressFactory;
    }

    public HeaderFactory headerFactory() {
        return headerFactory;
    }

    public MessageFactory messageFactory() {
        return messageFactory;
    }

    public int nextSn() {
        return snSeq.getAndIncrement();
    }

    public String mediaIp() {
        String local = props.getLive().getLocalIp();
        if (local != null && !local.isBlank()) {
            return local.trim();
        }
        String upper = props.getUpper().getSipIp();
        return upper == null || upper.isBlank() ? "127.0.0.1" : upper.trim();
    }

    public String sipDomain() {
        return props.getUpper().getSipDomain();
    }

    public String upperSipId() {
        return props.getUpper().getSipId();
    }
}
