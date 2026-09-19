package com.jizhi.videomid.api;

import com.jizhi.videomid.auth.dto.ApiResponse;
import com.jizhi.videomid.config.Gb28181Properties;
import com.jizhi.videomid.gb28181.Gb28181CatalogPort;
import com.jizhi.videomid.gb28181.Gb28181PlayService;
import com.jizhi.videomid.gb28181.Gb28181SyncService;
import com.jizhi.videomid.gb28181.live.Gb28181DbCatalogSeeder;
import com.jizhi.videomid.gb28181.live.Gb28181DeviceRegistry;
import com.jizhi.videomid.gb28181.live.LiveGb28181CatalogAdapter;
import com.jizhi.videomid.sip.live.Gb28181InviteSessionStore;
import com.jizhi.videomid.sip.Gb28181SipPort;
import com.jizhi.videomid.sip.live.Gb28181SipOutboundClient;
import com.jizhi.videomid.sip.live.Gb28181SipSessionStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/gb28181")
public class Gb28181Controller {

    private final Gb28181Properties props;
    private final Gb28181CatalogPort catalogPort;
    private final Gb28181SyncService syncService;
    private final Gb28181PlayService playService;
    private final Gb28181SipPort sipPort;
    private final Gb28181DeviceRegistry deviceRegistry;
    private final Gb28181DbCatalogSeeder dbCatalogSeeder;
    private final ObjectProvider<Gb28181SipSessionStore> sessionStore;
    private final ObjectProvider<Gb28181SipOutboundClient> outboundClient;
    private final ObjectProvider<Gb28181InviteSessionStore> inviteSessionStore;

    public Gb28181Controller(Gb28181Properties props,
                               Gb28181CatalogPort catalogPort,
                               Gb28181SyncService syncService,
                               Gb28181PlayService playService,
                               Gb28181SipPort sipPort,
                               Gb28181DeviceRegistry deviceRegistry,
                               Gb28181DbCatalogSeeder dbCatalogSeeder,
                               ObjectProvider<Gb28181SipSessionStore> sessionStore,
                               ObjectProvider<Gb28181SipOutboundClient> outboundClient,
                               ObjectProvider<Gb28181InviteSessionStore> inviteSessionStore) {
        this.props = props;
        this.catalogPort = catalogPort;
        this.syncService = syncService;
        this.playService = playService;
        this.sipPort = sipPort;
        this.deviceRegistry = deviceRegistry;
        this.dbCatalogSeeder = dbCatalogSeeder;
        this.sessionStore = sessionStore;
        this.outboundClient = outboundClient;
        this.inviteSessionStore = inviteSessionStore;
    }

    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", props.isEnabled());
        m.put("dataSource", props.isMock() ? "mock" : "live");
        m.put("mock", props.isMock());
        m.put("preferForBizLive", props.isPreferForBizLive());
        m.put("upper", props.getUpper());
        m.put("media", props.getMedia());
        m.put("mockOptions", props.getMock());
        m.put("liveOptions", props.getLive());
        return ApiResponse.ok(m);
    }

    /** live 模式：当前 SIP 注册表（开发期可来自 DB 种子） */
    @GetMapping("/registry")
    public ApiResponse<List<Map<String, Object>>> registry() {
        return ApiResponse.ok(deviceRegistry.listCatalog());
    }

    /** live 模式：从 MySQL 重新填充 Catalog 注册表 */
    @PostMapping("/registry/reload-from-db")
    public ApiResponse<Map<String, Object>> reloadRegistryFromDb() {
        if (props.isMock()) {
            throw new IllegalStateException("mock 模式无需 registry，Catalog 来自 resources/mock/uniview-devices.json");
        }
        int count = dbCatalogSeeder.seedFromDb();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("devices", count);
        m.put("note", "已从 MySQL 重新填充 live Catalog 注册表");
        return ApiResponse.ok(m);
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sipListening", props.isMock());
        m.put("registeredDevices", catalogPort.listCatalog().size());
        m.put("mode", props.isMock() ? "模拟下级平台" : "真实 SIP 上级");
        m.put("preferForBizLive", props.isPreferForBizLive());
        m.put("sip", sipPort.status());
        m.put("note", props.isMock()
                ? "mock 模式自动注册模拟设备，Catalog 与 resources/mock/uniview-devices.json 一致"
                : "live 模式：JAIN-SIP 上级，REGISTER → Catalog → INVITE + ZLM RTP");
        return ApiResponse.ok(m);
    }

    @GetMapping("/sip/status")
    public ApiResponse<Map<String, Object>> sipStatus() {
        return ApiResponse.ok(sipPort.status());
    }

    /** live：当前 SIP REGISTER 会话列表 */
    @GetMapping("/sip/sessions")
    public ApiResponse<List<Map<String, Object>>> sipSessions() {
        Gb28181SipSessionStore store = sessionStore.getIfAvailable();
        if (store == null) {
            throw new IllegalStateException("仅 live 模式可用");
        }
        return ApiResponse.ok(store.listActive());
    }

    /** live：向已注册下级发送 Catalog Query */
    @PostMapping("/sip/catalog-query/{deviceId}")
    public ApiResponse<Map<String, Object>> sipCatalogQuery(@PathVariable String deviceId) {
        Gb28181SipOutboundClient client = outboundClient.getIfAvailable();
        if (client == null || !client.isReady()) {
            throw new IllegalStateException("SIP 栈未就绪");
        }
        client.sendCatalogQuery(deviceId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("deviceId", deviceId);
        m.put("sent", true);
        return ApiResponse.ok(m);
    }

    /** 按设备业务 ID 国标点播（与业务端 /live 相同逻辑） */
    @PostMapping("/devices/{deviceId}/play")
    public ApiResponse<Map<String, Object>> playDevice(@PathVariable String deviceId) {
        return ApiResponse.ok(playService.startLive(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("无法国标点播: " + deviceId)));
    }

    /** 模拟下级 Catalog（JSON） */
    @GetMapping("/catalog")
    public ApiResponse<List<Map<String, Object>>> catalog() {
        return ApiResponse.ok(catalogPort.listCatalog());
    }

    /** 模拟下级 Catalog（国标 XML） */
    @GetMapping(value = "/catalog.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String catalogXml() {
        return catalogPort.buildCatalogXml();
    }

    /** 业务端对齐视图：Catalog + DB 状态 */
    @GetMapping("/biz-devices")
    public ApiResponse<List<Map<String, Object>>> bizDevices() {
        return ApiResponse.ok(syncService.bizAlignedDevices());
    }

    /** 校验 Catalog 与 MySQL 是否一致 */
    @GetMapping("/sync-check")
    public ApiResponse<Map<String, Object>> syncCheck() {
        return ApiResponse.ok(syncService.verify());
    }

    /** 模拟国标 INVITE 点播（mock 返回演示 playUrl，live 接 RTP） */
    @PostMapping("/channels/{channelId}/invite")
    public ApiResponse<Map<String, Object>> invite(@PathVariable String channelId) {
        return ApiResponse.ok(catalogPort.invite(channelId));
    }

    /** live：停止点播（SIP BYE + 关闭 ZLM RTP） */
    @PostMapping("/channels/{channelId}/bye")
    public ApiResponse<Map<String, Object>> stopInvite(@PathVariable String channelId) {
        if (props.isMock()) {
            throw new IllegalStateException("mock 模式无需 BYE");
        }
        if (!(catalogPort instanceof LiveGb28181CatalogAdapter live)) {
            throw new IllegalStateException("live 适配器不可用");
        }
        return ApiResponse.ok(live.stopInvite(channelId));
    }

    /** live：当前 INVITE 点播会话 */
    @GetMapping("/invite/sessions")
    public ApiResponse<List<Map<String, Object>>> inviteSessions() {
        Gb28181InviteSessionStore store = inviteSessionStore.getIfAvailable();
        if (store == null) {
            throw new IllegalStateException("仅 live 模式可用");
        }
        return ApiResponse.ok(store.listAll());
    }
}
