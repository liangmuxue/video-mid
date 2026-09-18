package com.jizhi.videomid.sip.live;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Gb28181XmlHelperTest {

    @Test
    void parseCatalogItems() {
        String xml = """
                <?xml version="1.0"?>
                <Response>
                <CmdType>Catalog</CmdType>
                <SumNum>2</SumNum>
                <DeviceList>
                  <Item><DeviceID>34020000001320000001</DeviceID><Name>cam1</Name></Item>
                  <Item><DeviceID>34020000001320000002</DeviceID><Name>cam2</Name><ParentID>34020000002000000001</ParentID></Item>
                </DeviceList>
                </Response>
                """;
        assertTrue(Gb28181XmlHelper.isCatalogResponse(xml));
        List<Map<String, String>> items = Gb28181XmlHelper.parseCatalogItems(xml);
        assertEquals(2, items.size());
        assertEquals("34020000001320000001", items.get(0).get("DeviceID"));
        assertEquals(2, Gb28181XmlHelper.parseSumNum(xml));
    }

    @Test
    void buildCatalogQueryAndKeepalive() {
        String q = Gb28181XmlHelper.buildCatalogQuery("34020000002000000001", 1);
        assertTrue(q.contains("<CmdType>Catalog</CmdType>"));
        String k = Gb28181XmlHelper.buildKeepaliveNotify("34020000002000000001", 2);
        assertTrue(Gb28181XmlHelper.isKeepalive(k));
    }
}
