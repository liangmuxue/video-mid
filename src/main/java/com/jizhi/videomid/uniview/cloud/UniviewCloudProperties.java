package com.jizhi.videomid.uniview.cloud;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/** 宇视云开放平台。不读取摄像机本机账号。 */
@ConfigurationProperties(prefix = "uniview-cloud")
public class UniviewCloudProperties {

    private String baseUrl = "https://ezcloud.uniview.com";
    private String appId = "";
    private String secretKey = "";
    /** 0 主码流，1 辅码流，2 第三流。 */
    private int quality = 1;
    /** 这些设备名称走主码流，其余仍用 {@link #quality}。 */
    private List<String> mainStreamNames = new ArrayList<>();
    /** 2 HLS，3 RTMP，4 FLV。 */
    private int protocol = 4;

    public boolean configured() {
        return appId != null && !appId.isBlank() && secretKey != null && !secretKey.isBlank();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public int getQuality() {
        return quality;
    }

    public void setQuality(int quality) {
        this.quality = quality;
    }

    public List<String> getMainStreamNames() {
        return mainStreamNames;
    }

    public void setMainStreamNames(List<String> mainStreamNames) {
        this.mainStreamNames = mainStreamNames == null ? new ArrayList<>() : mainStreamNames;
    }

    /** 名单内的设备用主码流，其余用 {@link #quality}。 */
    public int qualityFor(String deviceName) {
        if (deviceName != null && mainStreamNames != null) {
            String name = deviceName.trim();
            for (String item : mainStreamNames) {
                if (item != null && name.equals(item.trim())) {
                    return 0;
                }
            }
        }
        return quality;
    }

    public int getProtocol() {
        return protocol;
    }

    public void setProtocol(int protocol) {
        this.protocol = protocol;
    }
}
