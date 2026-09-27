package com.jizhi.videomid.gb28181.sip;

import com.jizhi.videomid.config.Gb28181Properties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class Gb28181SipBootstrap implements ApplicationRunner {

    private final Gb28181Properties props;
    private final Gb28181SipPort sipPort;

    public Gb28181SipBootstrap(Gb28181Properties props, Gb28181SipPort sipPort) {
        this.props = props;
        this.sipPort = sipPort;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.isEnabled()) {
            return;
        }
        sipPort.ensureStarted();
    }
}
