package com.jizhi.videomid.gb28181.sip.live;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** GB28181 MANSCDP XML 简易解析/构建 */
public final class Gb28181XmlHelper {

    private static final Pattern TAG = Pattern.compile("<(\\w+)>([^<]*)</\\1>");

    private Gb28181XmlHelper() {
    }

    public static String buildCatalogQuery(String deviceId, int sn) {
        return "<?xml version=\"1.0\" encoding=\"GB2312\"?>\n"
                + "<Query>\n"
                + "<CmdType>Catalog</CmdType>\n"
                + "<SN>" + sn + "</SN>\n"
                + "<DeviceID>" + deviceId + "</DeviceID>\n"
                + "</Query>";
    }

    public static boolean isCatalogResponse(String xml) {
        if (xml == null || xml.isBlank()) {
            return false;
        }
        return xml.contains("<CmdType>Catalog</CmdType>") && xml.contains("<Response>");
    }

    public static boolean isKeepalive(String xml) {
        return xml != null && xml.contains("<CmdType>Keepalive</CmdType>");
    }

    public static String buildKeepaliveNotify(String deviceId, int sn) {
        return "<?xml version=\"1.0\" encoding=\"GB2312\"?>\n"
                + "<Notify>\n"
                + "<CmdType>Keepalive</CmdType>\n"
                + "<SN>" + sn + "</SN>\n"
                + "<DeviceID>" + deviceId + "</DeviceID>\n"
                + "<Status>OK</Status>\n"
                + "</Notify>";
    }

    public static int parseSumNum(String xml) {
        String v = tagValue(xml, "SumNum");
        if (v.isBlank()) {
            return -1;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static List<Map<String, String>> parseCatalogItems(String xml) {
        List<Map<String, String>> items = new ArrayList<>();
        if (xml == null || xml.isBlank()) {
            return items;
        }
        Matcher block = Pattern.compile("<Item>(.*?)</Item>", Pattern.DOTALL).matcher(xml);
        while (block.find()) {
            Map<String, String> item = new LinkedHashMap<>();
            Matcher tag = TAG.matcher(block.group(1));
            while (tag.find()) {
                item.put(tag.group(1), tag.group(2).trim());
            }
            if (!item.isEmpty()) {
                items.add(item);
            }
        }
        return items;
    }

    public static String tagValue(String xml, String tag) {
        if (xml == null) {
            return "";
        }
        Matcher m = Pattern.compile("<" + tag + ">([^<]*)</" + tag + ">").matcher(xml);
        return m.find() ? m.group(1).trim() : "";
    }
}
