package com.jizhi.videomid.gb28181.sip.live;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Gb28181CatalogBufferTest {

    @Test
    void aggregatesPagedCatalog() {
        Gb28181CatalogBuffer buffer = new Gb28181CatalogBuffer();
        String p1 = """
                <Response><CmdType>Catalog</CmdType><SumNum>2</SumNum>
                <DeviceList><Item><DeviceID>a</DeviceID><Name>1</Name></Item></DeviceList></Response>
                """;
        assertTrue(buffer.accept("dev1", p1).isEmpty());
        String p2 = """
                <Response><CmdType>Catalog</CmdType><SumNum>2</SumNum>
                <DeviceList><Item><DeviceID>b</DeviceID><Name>2</Name></Item></DeviceList></Response>
                """;
        List<Map<String, String>> all = buffer.accept("dev1", p2).orElseThrow();
        assertEquals(2, all.size());
    }
}
