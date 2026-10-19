package com.jizhi.videomid.uniview.cloud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UniviewCloudDeviceNosTest {

    @Test
    void numbersRoomByBuildingFloorAndSide() {
        assertEquals(34092, UniviewCloudDeviceNos.of("邻海34号9B"));
        assertEquals(32101, UniviewCloudDeviceNos.of("邻海街32号10A"));
        assertEquals(32011, UniviewCloudDeviceNos.of("邻海街32号1A"));
        assertEquals(34122, UniviewCloudDeviceNos.of("邻海园34号12B"));
    }

    @Test
    void numbersPublicAndBasementNames() {
        assertEquals(34001, UniviewCloudDeviceNos.of("邻海街34号天台A"));
        assertEquals(34002, UniviewCloudDeviceNos.of("邻海街34号天台B"));
        assertEquals(33023, UniviewCloudDeviceNos.of("邻海街33号2层侧门"));
        assertEquals(33024, UniviewCloudDeviceNos.of("邻海街33号2层大堂"));
        assertEquals(32811, UniviewCloudDeviceNos.of("邻海街32号B1侧门"));
        assertEquals(32812, UniviewCloudDeviceNos.of("邻海街32号B1大堂"));
        assertEquals(32821, UniviewCloudDeviceNos.of("邻海街32号B2-A"));
        assertEquals(32822, UniviewCloudDeviceNos.of("邻海街32号B2-B"));
        assertEquals(32831, UniviewCloudDeviceNos.of("邻海街32号B3"));
        assertEquals(40011, UniviewCloudDeviceNos.of("邻海街40号1-1"));
        assertEquals(40013, UniviewCloudDeviceNos.of("邻海街40号1-B1"));
        assertEquals(40014, UniviewCloudDeviceNos.of("邻海街40号1-B2"));
    }

    @Test
    void leavesBlankNamesEmpty() {
        assertNull(UniviewCloudDeviceNos.of(null));
        assertNull(UniviewCloudDeviceNos.of("  "));
    }
}
