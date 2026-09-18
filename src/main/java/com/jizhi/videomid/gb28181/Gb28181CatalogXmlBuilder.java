package com.jizhi.videomid.gb28181;

import java.util.List;
import java.util.Map;

/** 国标 Catalog XML 构建（mock / live 共用） */
public final class Gb28181CatalogXmlBuilder {

    private Gb28181CatalogXmlBuilder() {
    }

    public static String build(List<Map<String, Object>> catalog) {
        StringBuilder sb = new StringBuilder();
        int channelCount = countChannels(catalog);
        sb.append("<?xml version=\"1.0\" encoding=\"GB2312\"?>\n");
        sb.append("<Response>\n<CmdType>Catalog</CmdType>\n");
        sb.append("<SnapNum>").append(channelCount).append("</SnapNum>\n");
        sb.append("<DeviceList Num=\"").append(channelCount).append("\">\n");
        for (Map<String, Object> dev : catalog) {
            sb.append("  <Item>\n");
            sb.append("    <DeviceID>").append(dev.get("gbDeviceId")).append("</DeviceID>\n");
            sb.append("    <Name>").append(escape(String.valueOf(dev.get("name")))).append("</Name>\n");
            sb.append("    <Status>ON</Status>\n");
            sb.append("    <Info><DeviceType>IPC</DeviceType></Info>\n");
            sb.append("  </Item>\n");
            Object chs = dev.get("channels");
            if (chs instanceof List<?> list) {
                for (Object o : list) {
                    if (!(o instanceof Map<?, ?> ch)) {
                        continue;
                    }
                    sb.append("  <Item>\n");
                    sb.append("    <DeviceID>").append(ch.get("channelId")).append("</DeviceID>\n");
                    sb.append("    <Name>").append(escape(String.valueOf(ch.get("streamName")))).append("</Name>\n");
                    sb.append("    <Status>ON</Status>\n");
                    sb.append("    <ParentID>").append(dev.get("gbDeviceId")).append("</ParentID>\n");
                    sb.append("  </Item>\n");
                }
            }
        }
        sb.append("</DeviceList>\n</Response>");
        return sb.toString();
    }

    private static int countChannels(List<Map<String, Object>> catalog) {
        int n = 0;
        for (Map<String, Object> dev : catalog) {
            Object chs = dev.get("channels");
            if (chs instanceof List<?> list) {
                n += list.size();
            }
        }
        return n;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
