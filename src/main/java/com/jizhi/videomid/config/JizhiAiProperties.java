package com.jizhi.videomid.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 极知 AI 业务侧接口（如 /aidemo/device/updateStatus）。
 * {@code base-url} 填协议 + IP + 端口，不含路径，例如 {@code http://119.3.161.125:8080}。
 */
@ConfigurationProperties(prefix = "jizhi-ai")
public class JizhiAiProperties {

    private final Sync sync = new Sync();

    public Sync getSync() {
        return sync;
    }

    public static class Sync {
        /** 是否向极知 AI 同步设备状态 */
        private boolean enabled = false;
        /** 极知 AI 服务根地址，如 http://host:port */
        private String baseUrl = "";

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
    }
}
