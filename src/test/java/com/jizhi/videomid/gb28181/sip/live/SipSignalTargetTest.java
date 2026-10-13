package com.jizhi.videomid.gb28181.sip.live;

import gov.nist.javax.sip.address.AddressFactoryImpl;
import org.junit.jupiter.api.Test;

import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SipSignalTargetTest {

    @Test
    void retargetReplacesPrivateContactWithSignalPeer() throws Exception {
        AddressFactory factory = new AddressFactoryImpl();
        SipURI uri = factory.createSipURI("34020000001320000001", "192.168.1.8");
        uri.setPort(5060);
        Address contact = factory.createAddress(uri);

        SipSignalTarget.retarget(contact, "127.0.0.1", 40000, "TCP");

        SipURI updated = (SipURI) contact.getURI();
        assertEquals("127.0.0.1", updated.getHost());
        assertEquals(40000, updated.getPort());
        assertEquals("tcp", updated.getTransportParam());
    }
}
