package com.jizhi.videomid.device;

/** 摄像头安装类型：0=云台，1=天台，2=楼梯，3=电梯，4=大厅，5=地下室 */
public final class DeviceType {

    public static final int PTZ = 0;
    public static final int ROOFTOP = 1;
    public static final int STAIRS = 2;
    public static final int ELEVATOR = 3;
    public static final int LOBBY = 4;
    public static final int BASEMENT = 5;

    private DeviceType() {
    }

    public static int normalize(Object value) {
        if (value == null) {
            return PTZ;
        }
        int v;
        if (value instanceof Number n) {
            v = n.intValue();
        } else {
            try {
                v = Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("deviceType 必须是 0～5 的整数");
            }
        }
        if (v < PTZ || v > BASEMENT) {
            throw new IllegalArgumentException("deviceType 必须是 0（云台）～5（地下室）");
        }
        return v;
    }
}
