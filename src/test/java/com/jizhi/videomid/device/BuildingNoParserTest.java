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
        assertEquals("40-1-3-B2", BuildingNoParser.fromDeviceName("邻海街40号3-B2"));
    }

    @Test
    void streetFloorWithoutDeviceDefaultsA() {
        assertEquals("33-1-2-A", BuildingNoParser.fromDeviceName("邻海街33号2层侧门"));
    }

    @Test
    void unknownNameReturnsNull() {
        assertNull(BuildingNoParser.fromDeviceName("东门球机"));
        assertNull(BuildingNoParser.fromDeviceName(""));
    }
}
