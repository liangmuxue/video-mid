package com.jizhi.videomid.eagleeye;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/** 鹰眼子系统地址。IP 未定时 {@code baseUrl} 留空。 */
@ConfigurationProperties(prefix = "eagle-eye")
public class EagleEyeProperties {

    private boolean enabled = true;
    /** 子系统根地址，协议 + IP + 端口，不含路径。 */
    private String baseUrl = "";
    /** 子系统控制命令路径。 */
    private String controlPath = "/api/control";
    /** 本系统 deviceId 与子系统 terminalId 的对应。不填则 terminalId 用 deviceId。 */
    private List<Terminal> terminals = new ArrayList<>();

    public boolean ready() {
        return enabled && baseUrl != null && !baseUrl.isBlank();
    }

    public String terminalIdOf(String deviceId) {
        if (deviceId == null || terminals == null) {
            return deviceId;
        }
        for (Terminal terminal : terminals) {
            if (terminal == null || terminal.getDeviceId() == null || terminal.getTerminalId() == null) {
                continue;
            }
            if (deviceId.equals(terminal.getDeviceId().trim()) && !terminal.getTerminalId().isBlank()) {
                return terminal.getTerminalId().trim();
            }
        }
        return deviceId;
    }

    public String rtspBaseUrlOf(String deviceId) {
        Terminal terminal = find(deviceId);
        return terminal == null ? "" : blank(terminal.getRtspBaseUrl());
    }

    public String playBaseUrlOf(String deviceId) {
        Terminal terminal = find(deviceId);
        return terminal == null ? "" : blank(terminal.getPlayBaseUrl());
    }

    private Terminal find(String deviceId) {
        if (deviceId == null || terminals == null) {
            return null;
        }
        for (Terminal terminal : terminals) {
            if (terminal == null || terminal.getDeviceId() == null) {
                continue;
            }
            if (deviceId.equals(terminal.getDeviceId().trim())) {
                return terminal;
            }
        }
        return null;
    }

    private static String blank(String value) {
        return value == null ? "" : value.trim();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getControlPath() {
        return controlPath;
    }

    public void setControlPath(String controlPath) {
        this.controlPath = controlPath;
    }

    public List<Terminal> getTerminals() {
        return terminals;
    }

    public void setTerminals(List<Terminal> terminals) {
        this.terminals = terminals == null ? new ArrayList<>() : terminals;
    }

    public static class Terminal {
        private String terminalId;
        private String deviceId;
        /** 这一台在设备列表里返回的 RTSP 前缀。空则用全局 zlm.rtsp-base-url。 */
        private String rtspBaseUrl;
        /** 这一台勾选播放返回的 HTTP 前缀。空则用全局 zlm.play-base-url。 */
        private String playBaseUrl;

        public String getTerminalId() {
            return terminalId;
        }

        public void setTerminalId(String terminalId) {
            this.terminalId = terminalId;
        }

        public String getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(String deviceId) {
            this.deviceId = deviceId;
        }

        public String getRtspBaseUrl() {
            return rtspBaseUrl;
        }

        public void setRtspBaseUrl(String rtspBaseUrl) {
            this.rtspBaseUrl = rtspBaseUrl;
        }

        public String getPlayBaseUrl() {
            return playBaseUrl;
        }

        public void setPlayBaseUrl(String playBaseUrl) {
            this.playBaseUrl = playBaseUrl;
        }
    }
}
