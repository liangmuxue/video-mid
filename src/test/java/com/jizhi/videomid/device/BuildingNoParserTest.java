package com.jizhi.videomid.device;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BuildingNoParserTest {

    @Test
    void towerWithUnitFloorAndDevice() {
        assertEquals("32-1-5-A", BuildingNoParser.fromDeviceName("32号楼-1单元-5楼层-A设备"));
        assertEquals("32-1-5-B", BuildingNoParser.fromDeviceName("32号楼-1单元-5楼层-B设备"));
    }

    @Test
    void streetNumberWithFloorAndDeviceCode() {
        assertEquals("40-1-1-A", BuildingNoParser.fromDeviceName("邻海街40号1-1"));
        assertEquals("40-1-1-B", BuildingNoParser.fromDeviceName("邻海街40号1-B1"));
        assertEquals("40-1-4-C", BuildingNoParser.fromDeviceName("邻海街40号4-B2"));
    }

    @Test
    void streetFloorWithoutDeviceDefaultsA() {
        assertEquals("33-1-2-A", BuildingNoParser.fromDeviceName("邻海街33号2层侧门"));
        assertEquals("33-1-2-B", BuildingNoParser.fromDeviceName("邻海街33号2层大堂"));
    }

    @Test
    void roomLetterUsesBuildingUnitFloorAndSide() {
        assertEquals("32-1-10-A", BuildingNoParser.fromDeviceName("邻海街32号10A"));
        assertEquals("32-1-1-A", BuildingNoParser.fromDeviceName("邻海街32号1A"));
        assertEquals("34-1-9-B", BuildingNoParser.fromDeviceName("邻海34号9B"));
        assertEquals("34-1-12-B", BuildingNoParser.fromDeviceName("邻海园34号12B"));
    }

    @Test
    void roofAndBasement() {
        assertEquals("34-1-0-A", BuildingNoParser.fromDeviceName("邻海街34号天台A"));
        assertEquals("34-1-0-B", BuildingNoParser.fromDeviceName("邻海街34号天台B"));
        assertEquals("32-1-B1-A", BuildingNoParser.fromDeviceName("邻海街32号B1侧门"));
        assertEquals("32-1-B1-B", BuildingNoParser.fromDeviceName("邻海街32号B1大堂"));
        assertEquals("34-1-B1-A", BuildingNoParser.fromDeviceName("邻海街34号B1侧门"));
        assertEquals("34-1-B1-B", BuildingNoParser.fromDeviceName("邻海街34号B1大堂"));
        assertEquals("32-1-B2-A", BuildingNoParser.fromDeviceName("邻海街32号B2-A"));
        assertEquals("32-1-B2-B", BuildingNoParser.fromDeviceName("邻海街32号B2-B"));
        assertEquals("32-1-B3-A", BuildingNoParser.fromDeviceName("邻海街32号B3"));
        assertEquals("34-1-B3-A", BuildingNoParser.fromDeviceName("邻海街34号B3"));
    }

    @Test
    void unknownNameReturnsNull() {
        assertNull(BuildingNoParser.fromDeviceName("东门球机"));
        assertNull(BuildingNoParser.fromDeviceName(""));
    }
}
