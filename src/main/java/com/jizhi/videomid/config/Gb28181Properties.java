package com.jizhi.videomid.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gb28181")
public class Gb28181Properties {

    private boolean enabled = true;
    /** mock=模拟下级；live=真实 SIP 上级 */
    private String dataSource = "mock";
    /** 业务端直播优先走国标 INVITE（mock 阶段演示国标出流） */
    private boolean preferForBizLive = true;
    private Upper upper = new Upper();
    private Media media = new Media();
    private Mock mock = new Mock();
    private Live live = new Live();

    public boolean isMock() {
        return !"live".equalsIgnoreCase(dataSource);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDataSource() {
        return dataSource;
    }

    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
    }

    public boolean isPreferForBizLive() {
        return preferForBizLive;
    }

    public void setPreferForBizLive(boolean preferForBizLive) {
        this.preferForBizLive = preferForBizLive;
    }

    public Upper getUpper() {
        return upper;
    }

    public void setUpper(Upper upper) {
        this.upper = upper;
    }

    public Media getMedia() {
        return media;
    }

    public void setMedia(Media media) {
        this.media = media;
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

    public static class Upper {
        private String sipId = "34020000002000000001";
        private String sipDomain = "3402000000";
        private String sipIp = "127.0.0.1";
        private int sipPort = 5060;
        private String password = "12345678";
        private int registerExpires = 3600;
        private int heartbeatInterval = 30;

        public String getSipId() { return sipId; }
        public void setSipId(String sipId) { this.sipId = sipId; }
        public String getSipDomain() { return sipDomain; }
        public void setSipDomain(String sipDomain) { this.sipDomain = sipDomain; }
        public String getSipIp() { return sipIp; }
        public void setSipIp(String sipIp) { this.sipIp = sipIp; }
        public int getSipPort() { return sipPort; }
        public void setSipPort(int sipPort) { this.sipPort = sipPort; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public int getRegisterExpires() { return registerExpires; }
        public void setRegisterExpires(int registerExpires) { this.registerExpires = registerExpires; }
        public int getHeartbeatInterval() { return heartbeatInterval; }
        public void setHeartbeatInterval(int heartbeatInterval) { this.heartbeatInterval = heartbeatInterval; }
    }

    public static class Media {
        private int rtpPortMin = 30000;
        private int rtpPortMax = 30500;
        private String transport = "UDP";

        public int getRtpPortMin() { return rtpPortMin; }
        public void setRtpPortMin(int rtpPortMin) { this.rtpPortMin = rtpPortMin; }
        public int getRtpPortMax() { return rtpPortMax; }
        public void setRtpPortMax(int rtpPortMax) { this.rtpPortMax = rtpPortMax; }
        public String getTransport() { return transport; }
        public void setTransport(String transport) { this.transport = transport; }
    }

    public static class Mock {
        private boolean autoRegister = true;
        private String previewBaseUrl = "http://127.0.0.1:8080/live";

        public boolean isAutoRegister() { return autoRegister; }
        public void setAutoRegister(boolean autoRegister) { this.autoRegister = autoRegister; }
        public String getPreviewBaseUrl() { return previewBaseUrl; }
        public void setPreviewBaseUrl(String previewBaseUrl) { this.previewBaseUrl = previewBaseUrl; }
    }

    public static class Live {
        /** SIP 未接入前，从 MySQL 填充 Catalog 注册表 */
        private boolean seedFromDb = true;
        /** INVITE 阶段若 ZLM 尚无 RTP，回退 DB 中 stream_url 演示地址 */
        private boolean fallbackToDbStream = true;
        /** ZLM GB28181 RTP 应用名 */
        private String zlmApp = "rtp";
        /** 启动 JAIN-SIP UDP 监听（上级平台） */
        private boolean enableSipStack = true;
        /** INVITE 点播时向下级发 SIP INVITE */
        private boolean sendInviteOnPlay = true;
        /** Catalog 查询：REGISTER 后自动 MESSAGE Query Catalog */
        private boolean autoCatalogQuery = true;
        /** SDP / 媒体本地 IP（不配则用 upper.sip-ip） */
        private String localIp = "";
        /** SIP 监听绑定地址（0.0.0.0 表示全部网卡） */
        private String bindIp = "0.0.0.0";
        /** REGISTER Digest 鉴权（国标下级需带 Authorization） */
        private boolean enableDigestAuth = true;

        public boolean isSeedFromDb() { return seedFromDb; }
        public void setSeedFromDb(boolean seedFromDb) { this.seedFromDb = seedFromDb; }
        public boolean isFallbackToDbStream() { return fallbackToDbStream; }
        public void setFallbackToDbStream(boolean fallbackToDbStream) { this.fallbackToDbStream = fallbackToDbStream; }
        public String getZlmApp() { return zlmApp; }
        public void setZlmApp(String zlmApp) { this.zlmApp = zlmApp; }
        public boolean isEnableSipStack() { return enableSipStack; }
        public void setEnableSipStack(boolean enableSipStack) { this.enableSipStack = enableSipStack; }
        public boolean isSendInviteOnPlay() { return sendInviteOnPlay; }
        public void setSendInviteOnPlay(boolean sendInviteOnPlay) { this.sendInviteOnPlay = sendInviteOnPlay; }
        public boolean isAutoCatalogQuery() { return autoCatalogQuery; }
        public void setAutoCatalogQuery(boolean autoCatalogQuery) { this.autoCatalogQuery = autoCatalogQuery; }
        public String getLocalIp() { return localIp; }
        public void setLocalIp(String localIp) { this.localIp = localIp; }
        public String getBindIp() { return bindIp; }
        public void setBindIp(String bindIp) { this.bindIp = bindIp; }
        public boolean isEnableDigestAuth() { return enableDigestAuth; }
        public void setEnableDigestAuth(boolean enableDigestAuth) { this.enableDigestAuth = enableDigestAuth; }
    }
}
