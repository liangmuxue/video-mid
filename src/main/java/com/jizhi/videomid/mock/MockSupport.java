package com.jizhi.videomid.mock;

/** 仅 mock 夹具使用的设备号约定，正式库不要写这些 ID。 */
public final class MockSupport {

    public static final String DEVICE_ID_PREFIX = "TIC7632";

    public static final String FIXTURES_ENABLED =
            "'${uniview.data-source:mock}'.equalsIgnoreCase('mock')"
                    + " || '${gb28181.data-source:mock}'.equalsIgnoreCase('mock')";

    private MockSupport() {
    }

    public static boolean isFixtureDeviceId(String deviceId) {
        return deviceId != null && deviceId.startsWith(DEVICE_ID_PREFIX);
    }
}
