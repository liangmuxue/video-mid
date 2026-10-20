package com.jizhi.videomid.device;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 从设备名称推导楼栋编号，格式：楼号-单元-楼层-设备位（如 32-1-5-A）。 */
public final class BuildingNoParser {

    private static final Pattern TOWER = Pattern.compile(
            "(\\d+)号楼(?:-(\\d+)单元)?-(\\d+)楼层(?:-([A-Za-z0-9]+)设备)?");
    /** 邻海街40号3-B2、邻海街40号1-1 */
    private static final Pattern STREET_FLOOR_DEVICE = Pattern.compile("(\\d+)号(\\d+)-([A-Za-z0-9]+)");
    /** 邻海街33号2层侧门、邻海街33号2层大堂 */
    private static final Pattern STREET_FLOOR_LAYER = Pattern.compile("(\\d+)号(\\d+)层(侧门|大堂)?");
    /** 邻海街32号B2-A */
    private static final Pattern BASEMENT_SIDE = Pattern.compile("(\\d+)号B(\\d+)-([A-Za-z0-9]+)");
    /** 邻海街32号B1侧门、邻海街32号B1大堂 */
    private static final Pattern BASEMENT_PUBLIC = Pattern.compile("(\\d+)号B(\\d+)(侧门|大堂)");
    /** 邻海街32号B3 */
    private static final Pattern BASEMENT = Pattern.compile("(\\d+)号B(\\d+)$");
    /** 邻海街34号天台A */
    private static final Pattern ROOF = Pattern.compile("(\\d+)号天台([A-Za-z0-9]+)");
    /** 邻海街32号10A、邻海34号9B */
    private static final Pattern ROOM = Pattern.compile("(\\d+)号(\\d+)([A-Za-z])$");

    private BuildingNoParser() {
    }

    /**
     * @return 解析成功返回 buildingNo，无法识别返回 null
     */
    public static String fromDeviceName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String s = name.trim();

        Matcher tower = TOWER.matcher(s);
        if (tower.find()) {
            String building = tower.group(1);
            String unit = tower.group(2) != null ? tower.group(2) : "1";
            String floor = tower.group(3);
            String device = deviceSuffix(tower.group(4));
            return building + "-" + unit + "-" + floor + "-" + device;
        }

        Matcher streetDevice = STREET_FLOOR_DEVICE.matcher(s);
        if (streetDevice.find()) {
            return streetDevice.group(1) + "-1-" + streetDevice.group(2) + "-"
                    + pointLetter(streetDevice.group(3));
        }

        Matcher streetLayer = STREET_FLOOR_LAYER.matcher(s);
        if (streetLayer.find()) {
            return streetLayer.group(1) + "-1-" + streetLayer.group(2) + "-"
                    + pointLetter(streetLayer.group(3));
        }

        Matcher basementSide = BASEMENT_SIDE.matcher(s);
        if (basementSide.find()) {
            return basementSide.group(1) + "-1-B" + basementSide.group(2) + "-"
                    + pointLetter(basementSide.group(3));
        }

        Matcher basementPublic = BASEMENT_PUBLIC.matcher(s);
        if (basementPublic.find()) {
            return basementPublic.group(1) + "-1-B" + basementPublic.group(2) + "-"
                    + pointLetter(basementPublic.group(3));
        }

        Matcher basement = BASEMENT.matcher(s);
        if (basement.find()) {
            return basement.group(1) + "-1-B" + basement.group(2) + "-A";
        }

        Matcher roof = ROOF.matcher(s);
        if (roof.find()) {
            return roof.group(1) + "-1-0-" + pointLetter(roof.group(2));
        }

        Matcher room = ROOM.matcher(s);
        if (room.find()) {
            return room.group(1) + "-1-" + room.group(2) + "-" + deviceSuffix(room.group(3));
        }

        return null;
    }

    private static String deviceSuffix(String raw) {
        return pointLetter(raw);
    }

    /** 第四位只保留 A–Z。1→A，B1→B，B2→C，侧门→A，大堂→B，缺省为 A。 */
    private static String pointLetter(String raw) {
        if (raw == null || raw.isBlank()) {
            return "A";
        }
        String text = raw.trim();
        if (text.length() == 1 && Character.isLetter(text.charAt(0))) {
            return text.toUpperCase(Locale.ROOT);
        }
        if (text.matches("\\d+")) {
            return letterAt(Integer.parseInt(text));
        }
        if (text.matches("(?i)B\\d+")) {
            return letterAt(Integer.parseInt(text.substring(1)) + 1);
        }
        if ("侧门".equals(text)) {
            return "A";
        }
        if ("大堂".equals(text)) {
            return "B";
        }
        return "A";
    }

    private static String letterAt(int index) {
        if (index < 1 || index > 26) {
            return "A";
        }
        return String.valueOf((char) ('A' + index - 1));
    }
}
