package com.jizhi.videomid.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 宇视对接总开关：前期 mock，联调通过后改 live，业务代码无需改动。
 */
@ConfigurationProperties(prefix = "uniview")
public class UniviewProperties {

    /** mock=模拟数据；live=真实宇视 SDK / 国标 */
    private String dataSource = "mock";

    private Mock mock = new Mock();
    private Live live = new Live();

    public boolean isMock() {
        return !"live".equalsIgnoreCase(dataSource);
    }

    public String getDataSource() {
        return dataSource;
    }

    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
    }

    public Mock getMock() {
        return mock;
    }

    public void setMock(Mock mock) {
        this.mock = mock;
    }

    public Live getLive() {
        return live;
    }

    public void setLive(Live live) {
        this.live = live;
    }

    public static class Mock {
        /** 模拟预览流前缀（可指向 ZLM 演示流） */
        private String previewBaseUrl = "http://127.0.0.1:8080/live";
        /** 模拟设备定义文件（classpath） */
        private String devicesResource = "mock/uniview-devices.json";

        public String getPreviewBaseUrl() {
            return previewBaseUrl;
        }

        public void setPreviewBaseUrl(String previewBaseUrl) {
            this.previewBaseUrl = previewBaseUrl;
        }

        public String getDevicesResource() {
            return devicesResource;
        }

        public void setDevicesResource(String devicesResource) {
            this.devicesResource = devicesResource;
        }
    }

    public static class Live {
        private String deviceHost = "";
        private int devicePort = 80;
        private String username = "admin";
        private String password = "";
        /** LAPI 或 NetSDK，联调时填写 */
        private String apiType = "lapi";

        public String getDeviceHost() {
            return deviceHost;
        }

        public void setDeviceHost(String deviceHost) {
            this.deviceHost = deviceHost;
        }

        public int getDevicePort() {
            return devicePort;
        }

        public void setDevicePort(int devicePort) {
            this.devicePort = devicePort;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getApiType() {
            return apiType;
        }

        public void setApiType(String apiType) {
            this.apiType = apiType;
        }
    }
}
