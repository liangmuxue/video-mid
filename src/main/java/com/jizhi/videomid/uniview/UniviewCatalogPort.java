package com.jizhi.videomid.uniview;

import java.util.List;
import java.util.Map;

/** 设备/通道目录（与业务端列表、国标模拟下级保持一致） */
public interface UniviewCatalogPort {

    List<Map<String, Object>> listDevices();

    Map<String, Object> getDevice(String deviceId);
}
