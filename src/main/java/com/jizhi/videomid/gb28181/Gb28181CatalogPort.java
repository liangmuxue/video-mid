package com.jizhi.videomid.gb28181;

import java.util.List;
import java.util.Map;

public interface Gb28181CatalogPort {

    List<Map<String, Object>> listCatalog();

    Map<String, Object> getChannel(String channelId);

    /** 国标 Catalog 风格 XML（模拟下级应答） */
    String buildCatalogXml();

    /** 模拟 INVITE 出流，返回信令/媒体摘要（mock 阶段用 ZLM 演示 URL 代替 RTP） */
    Map<String, Object> invite(String channelId);
}
