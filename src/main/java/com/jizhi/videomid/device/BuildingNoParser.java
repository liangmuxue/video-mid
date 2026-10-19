package com.jizhi.videomid.device;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 从设备名称推导楼栋编号，格式：楼号-单元-楼层-设备位（如 32-1-5-A）。 */
public final class BuildingNoParser {

    private static final Pattern TOWER = Pattern.compile(
            "(\\d+)号楼(?:-(\\d+)单元)?-(\\d+)楼层(?:-([A-Za-z0-9]+)设备)?");
    /** 邻海街40号3-B2 */
    private static final Pattern STREET_FLOOR_DEVICE = Pattern.compile("(\\d+)号(\\d+)-([A-Za-z0-9]+)");
    /** 邻海街33号2层侧门 */
    private static final Pattern STREET_FLOOR_LAYER = Pattern.compile("(\\d+)号(\\d+)层");

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
                    + deviceSuffix(streetDevice.group(3));
        }

        Matcher streetLayer = STREET_FLOOR_LAYER.matcher(s);
        if (streetLayer.find()) {
            return streetLayer.group(1) + "-1-" + streetLayer.group(2) + "-A";
        }

        return null;
    }

    private static String deviceSuffix(String raw) {
        if (raw == null || raw.isBlank()) {
            return "A";
        }
        return raw.length() == 1 ? raw.toUpperCase(Locale.ROOT) : raw;
    }
}
