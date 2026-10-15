package com.jizhi.videomid.uniview.cloud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UniviewCloudDeviceIdsTest {

    @Test
    void parsesSerialAndChannel() {
        UniviewCloudDeviceIds.Target target = UniviewCloudDeviceIds.parse("210235CBDY3267000054:0");
        assertEquals("210235CBDY3267000054", target.serial());
        assertEquals(0, target.channelNo());
        assertEquals("210235CBDY3267000054_0", UniviewCloudDeviceIds.zlmStream(target.serial(), target.channelNo()));
    }

    @Test
    void restoresDeviceIdFromPlayPath() {
        assertEquals("210235CBDY3267000054:0",
                UniviewCloudDeviceIds.deviceIdFromZlmStream("210235CBDY3267000054_0"));
        assertEquals("210235CBDY3267000054:0",
                UniviewCloudDeviceIds.deviceIdFromZlmStream("210235CBDY3267000054_0.live.flv"));
    }

    @Test
    void rejectsIdWithoutChannel() {
        assertThrows(IllegalArgumentException.class, () -> UniviewCloudDeviceIds.parse("210235CBDY3267000054"));
    }
}
