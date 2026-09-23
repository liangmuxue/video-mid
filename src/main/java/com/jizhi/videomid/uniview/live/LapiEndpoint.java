package com.jizhi.videomid.uniview.live;

import com.jizhi.videomid.device.Device;

/** 一台宇视摄像机的 LAPI 登录信息。 */
public record LapiEndpoint(String host, int port, String username, String password) {

    public static LapiEndpoint from(Device device) {
        int port = device.getPort() == null ? 80 : device.getPort();
        String username = device.getUsername() == null ? "" : device.getUsername();
        String password = device.getPassword() == null ? "" : device.getPassword();
        return new LapiEndpoint(device.getHost().trim(), port, username, password);
    }

    public String baseUrl() {
        String scheme = port == 443 ? "https" : "http";
        if (port == 80 || port == 443) {
            return scheme + "://" + host;
        }
        return scheme + "://" + host + ":" + port;
    }
}
