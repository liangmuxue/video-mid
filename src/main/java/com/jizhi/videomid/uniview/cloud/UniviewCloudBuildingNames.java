package com.jizhi.videomid.uniview.cloud;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 从云上设备名称取出楼号，例如「邻海街34号天台B」→「邻海街34号」。 */
public final class UniviewCloudBuildingNames {

    private static final Pattern BUILDING = Pattern.compile("^(邻海街\\d+号)");

    private UniviewCloudBuildingNames() {
    }

    public static String of(String deviceName) {
        if (deviceName == null || deviceName.isBlank()) {
            return null;
        }
        Matcher matcher = BUILDING.matcher(deviceName.trim());
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    public static int sortNo(String buildingName) {
        if (buildingName == null) {
            return 0;
        }
        Matcher matcher = Pattern.compile("\\d+").matcher(buildingName);
        if (!matcher.find()) {
            return 0;
        }
        return Integer.parseInt(matcher.group());
    }
}
