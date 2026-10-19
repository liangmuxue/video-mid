package com.jizhi.videomid.uniview.cloud;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从设备名称算出位置编号。楼号 + 两位房号 + 点位，A 为 1、B 为 2。
 * 例如「邻海34号9B」→ 34092，「邻海街32号10A」→ 32101。
 */
public final class UniviewCloudDeviceNos {

    private static final Pattern ROOM = Pattern.compile("(\\d+)号(\\d+)([ABab])$");
    private static final Pattern ROOF = Pattern.compile("(\\d+)号天台([ABab])$");
    private static final Pattern FLOOR_PUBLIC = Pattern.compile("(\\d+)号(\\d+)层(侧门|大堂)$");
    private static final Pattern BASEMENT_PUBLIC = Pattern.compile("(\\d+)号B(\\d+)(侧门|大堂)$");
    private static final Pattern BASEMENT_SIDE = Pattern.compile("(\\d+)号B(\\d+)-([ABab])$");
    private static final Pattern BASEMENT = Pattern.compile("(\\d+)号B(\\d+)$");
    private static final Pattern UNIT_POINT = Pattern.compile("(\\d+)号(\\d+)-(\\d+)$");
    private static final Pattern UNIT_B_POINT = Pattern.compile("(\\d+)号(\\d+)-B(\\d+)$");

    private UniviewCloudDeviceNos() {
    }

    public static Integer of(String deviceName) {
        if (deviceName == null || deviceName.isBlank()) {
            return null;
        }
        String name = deviceName.trim();
        Matcher matcher = ROOM.matcher(name);
        if (matcher.find()) {
            return number(matcher.group(1), pad2(matcher.group(2)), side(matcher.group(3)));
        }
        matcher = ROOF.matcher(name);
        if (matcher.find()) {
            return number(matcher.group(1), "00", side(matcher.group(2)));
        }
        matcher = FLOOR_PUBLIC.matcher(name);
        if (matcher.find()) {
            String point = "侧门".equals(matcher.group(3)) ? "3" : "4";
            return number(matcher.group(1), pad2(matcher.group(2)), point);
        }
        matcher = BASEMENT_PUBLIC.matcher(name);
        if (matcher.find()) {
            String point = "侧门".equals(matcher.group(3)) ? "1" : "2";
            return number(matcher.group(1), "8" + matcher.group(2), point);
        }
        matcher = BASEMENT_SIDE.matcher(name);
        if (matcher.find()) {
            return number(matcher.group(1), "8" + matcher.group(2), side(matcher.group(3)));
        }
        matcher = BASEMENT.matcher(name);
        if (matcher.find()) {
            return number(matcher.group(1), "8" + matcher.group(2), "1");
        }
        matcher = UNIT_POINT.matcher(name);
        if (matcher.find()) {
            return number(matcher.group(1), pad2(matcher.group(2)), matcher.group(3));
        }
        matcher = UNIT_B_POINT.matcher(name);
        if (matcher.find()) {
            int point = 2 + Integer.parseInt(matcher.group(3));
            return number(matcher.group(1), pad2(matcher.group(2)), Integer.toString(point));
        }
        return null;
    }

    private static String side(String letter) {
        return "A".equalsIgnoreCase(letter) ? "1" : "2";
    }

    private static String pad2(String value) {
        return value.length() >= 2 ? value : "0" + value;
    }

    private static Integer number(String building, String room, String point) {
        try {
            return Integer.valueOf(building + room + point);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
