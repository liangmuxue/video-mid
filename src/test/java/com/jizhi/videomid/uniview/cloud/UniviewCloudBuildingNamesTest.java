package com.jizhi.videomid.uniview.cloud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UniviewCloudBuildingNamesTest {

    @Test
    void takesBuildingPrefixFromDeviceName() {
        assertEquals("邻海街34号", UniviewCloudBuildingNames.of("邻海街34号天台B"));
        assertEquals("邻海街40号", UniviewCloudBuildingNames.of("邻海街40号4-B2"));
        assertEquals("邻海街34号", UniviewCloudBuildingNames.of("邻海街34号B1大堂"));
        assertEquals(34, UniviewCloudBuildingNames.sortNo("邻海街34号"));
    }

    @Test
    void leavesIrregularNamesUngrouped() {
        assertNull(UniviewCloudBuildingNames.of("邻海34号9B"));
        assertNull(UniviewCloudBuildingNames.of("邻海园34号12B"));
    }
}
