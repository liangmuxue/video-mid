package com.jizhi.videomid.uniview;

import java.util.List;
import java.util.Map;

/** 宇视云台控制（mock / live 统一接口） */
public interface UniviewPtzPort {

    List<Map<String, Object>> listPtzDevices();

    Map<String, Object> move(String deviceId, String direction, int speed);

    Map<String, Object> zoom(String deviceId, String action, int speed);

    /** focus_near / focus_far */
    Map<String, Object> focus(String deviceId, String action, int speed);

    Map<String, Object> wideAngle(String deviceId);

    Map<String, Object> gotoPreset(String deviceId, int presetIndex);

    Map<String, Object> setPreset(String deviceId, int presetIndex, String name, boolean overwrite);

    Map<String, Object> snapshot(String deviceId, String channelType);
}
