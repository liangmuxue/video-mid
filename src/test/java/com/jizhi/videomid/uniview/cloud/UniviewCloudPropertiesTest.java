package com.jizhi.videomid.uniview.cloud;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UniviewCloudPropertiesTest {

    @Test
    void namedDeviceUsesMainStream() {
        UniviewCloudProperties properties = new UniviewCloudProperties();
        properties.setQuality(1);
        properties.setMainStreamNames(List.of(
                "邻海街34号B3",
                "邻海街40号2-B2",
                "邻海街34号B2-B",
                "邻海街34号B2-A",
                "邻海街34号B1侧门",
                "邻海街34号B1大堂"));

        assertEquals(0, properties.qualityFor("邻海街34号B3"));
        assertEquals(0, properties.qualityFor("邻海街40号2-B2"));
        assertEquals(0, properties.qualityFor("邻海街34号B2-B"));
        assertEquals(0, properties.qualityFor("邻海街34号B2-A"));
        assertEquals(0, properties.qualityFor("邻海街34号B1侧门"));
        assertEquals(0, properties.qualityFor("邻海街34号B1大堂"));
        assertEquals(0, properties.qualityFor(" 邻海街34号B3 "));
        assertEquals(1, properties.qualityFor("邻海街32号B3"));
        assertEquals(1, properties.qualityFor(null));
    }
}
