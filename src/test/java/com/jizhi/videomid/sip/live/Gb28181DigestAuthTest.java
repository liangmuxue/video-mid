package com.jizhi.videomid.sip.live;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Gb28181DigestAuthTest {

    @Test
    void md5Deterministic() {
        assertEquals("e10adc3949ba59abbe56e057f20f883e", Gb28181DigestAuth.md5("123456"));
    }

    @Test
    void issueNonceUnique() {
        Gb28181DigestAuth auth = new Gb28181DigestAuth();
        assertNotEquals(auth.issueNonce(), auth.issueNonce());
    }
}
