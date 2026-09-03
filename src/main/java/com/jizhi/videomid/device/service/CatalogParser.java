package com.jizhi.videomid.device.service;

import com.jizhi.videomid.device.entity.DeviceEntity;
import com.jizhi.videomid.device.repository.DeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 解析宇视平台 Catalog 设备目录 XML，写入 MySQL 设备表。
 * 主码流、子码流通道信息一并入库。
 */
@Service
public class CatalogParser {

    private static final Logger log = LoggerFactory.getLogger(CatalogParser.class);

    private final DeviceRepository deviceRepository;

    public CatalogParser(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Transactional
    public int parseAndSave(String catalogXml, String platformId) {
        if (catalogXml == null || catalogXml.isBlank()) {
            return 0;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setNamespaceAware(false);
            Document doc = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(catalogXml.getBytes(StandardCharsets.UTF_8)));

            NodeList items = doc.getElementsByTagName("Item");
            List<DeviceEntity> parsed = new ArrayList<>();
            for (int i = 0; i < items.getLength(); i++) {
                Node node = items.item(i);
                if (node.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element item = (Element) node;
                DeviceEntity entity = mapItem(item, platformId);
                if (entity.getDeviceId() != null && !entity.getDeviceId().isBlank()) {
                    parsed.add(entity);
                }
            }

            // 关联主/子码流：同一父节点下，通道名或编码含 main/sub 或按常见国标规则配对
            linkMainSubStreams(parsed);

            int count = 0;
            for (DeviceEntity incoming : parsed) {
                upsert(incoming);
                count++;
            }
            log.info("Catalog parsed and saved {} devices for platform {}", count, platformId);
            return count;
        } catch (Exception e) {
            log.error("Failed to parse Catalog XML from platform {}", platformId, e);
            throw new IllegalStateException("Catalog parse failed: " + e.getMessage(), e);
        }
    }

    private DeviceEntity mapItem(Element item, String platformId) {
        DeviceEntity e = new DeviceEntity();
        e.setDeviceId(text(item, "DeviceID"));
        e.setName(text(item, "Name"));
        e.setManufacturer(text(item, "Manufacturer"));
        e.setModel(text(item, "Model"));
        e.setOwner(text(item, "Owner"));
        e.setCivilCode(text(item, "CivilCode"));
        e.setAddress(text(item, "Address"));
        e.setParentId(text(item, "ParentID"));
        e.setParental(intOrNull(text(item, "Parental")));
        e.setRegisterWay(intOrNull(text(item, "RegisterWay")));
        e.setSecrecy(intOrNull(text(item, "Secrecy")));
        e.setPtzType(intOrNull(text(item, "PTZType")));
        e.setPlatformId(platformId);

        String status = text(item, "Status");
        if (status != null) {
            e.setStatus("ON".equalsIgnoreCase(status) || "ONLINE".equalsIgnoreCase(status) ? "ON" : "OFF");
        }

        String longitude = text(item, "Longitude");
        String latitude = text(item, "Latitude");
        if (longitude != null && !longitude.isBlank()) {
            try {
                e.setLongitude(Double.parseDouble(longitude));
            } catch (NumberFormatException ignored) {
            }
        }
        if (latitude != null && !latitude.isBlank()) {
            try {
                e.setLatitude(Double.parseDouble(latitude));
            } catch (NumberFormatException ignored) {
            }
        }

        e.setDeviceType(resolveDeviceType(e));
        e.setStreamType(resolveStreamType(e));
        return e;
    }

    private String resolveDeviceType(DeviceEntity e) {
        String id = e.getDeviceId();
        if (id == null || id.length() < 20) {
            return "REGION";
        }
        // 国标编码第 11-13 位：200 中心信令服务器 / 215 业务分组 / 131/132 摄像机等
        String typeCode = id.substring(10, 13);
        return switch (typeCode) {
            case "200" -> "PLATFORM";
            case "215", "216" -> "REGION";
            case "131", "132", "134", "118" -> "CAMERA";
            default -> (e.getParental() != null && e.getParental() == 1) ? "REGION" : "CAMERA";
        };
    }

    private String resolveStreamType(DeviceEntity e) {
        String name = Optional.ofNullable(e.getName()).orElse("").toLowerCase(Locale.ROOT);
        String id = Optional.ofNullable(e.getDeviceId()).orElse("");
        if (name.contains("子码流") || name.contains("sub") || id.endsWith("02")) {
            return "sub";
        }
        if (name.contains("主码流") || name.contains("main") || id.endsWith("01")) {
            return "main";
        }
        return null;
    }

    /**
     * 将同一逻辑摄像头的主/子码流互相回填 mainChannelId / subChannelId。
     * 规则：相同 ParentID 下的 CAMERA；或编码仅末两位不同（01/02）。
     */
    private void linkMainSubStreams(List<DeviceEntity> devices) {
        Map<String, List<DeviceEntity>> byParent = new HashMap<>();
        for (DeviceEntity d : devices) {
            if (!"CAMERA".equals(d.getDeviceType())) {
                continue;
            }
            String key = d.getParentId() != null ? d.getParentId() : prefix20(d.getDeviceId());
            byParent.computeIfAbsent(key, k -> new ArrayList<>()).add(d);
        }
        for (List<DeviceEntity> group : byParent.values()) {
            DeviceEntity main = null;
            DeviceEntity sub = null;
            for (DeviceEntity d : group) {
                if ("main".equals(d.getStreamType())) {
                    main = d;
                } else if ("sub".equals(d.getStreamType())) {
                    sub = d;
                }
            }
            // 若未标记，按常见：同组两个通道时第一个主第二个子
            if (main == null && sub == null && group.size() >= 2) {
                main = group.get(0);
                sub = group.get(1);
                main.setStreamType("main");
                sub.setStreamType("sub");
            } else if (main == null && group.size() == 1) {
                // 单通道视为主码流，子码流同 ID（预览仍可用）
                main = group.get(0);
                main.setStreamType("main");
                main.setMainChannelId(main.getDeviceId());
                main.setSubChannelId(main.getDeviceId());
                continue;
            }
            if (main != null) {
                main.setMainChannelId(main.getDeviceId());
                if (sub != null) {
                    main.setSubChannelId(sub.getDeviceId());
                    sub.setMainChannelId(main.getDeviceId());
                    sub.setSubChannelId(sub.getDeviceId());
                } else {
                    main.setSubChannelId(main.getDeviceId());
                }
            }
            if (sub != null && main == null) {
                sub.setSubChannelId(sub.getDeviceId());
                sub.setMainChannelId(sub.getDeviceId());
            }
        }
    }

    private String prefix20(String deviceId) {
        if (deviceId == null) {
            return "";
        }
        return deviceId.length() >= 20 ? deviceId.substring(0, 20) : deviceId;
    }

    private void upsert(DeviceEntity incoming) {
        Optional<DeviceEntity> existing = deviceRepository.findByDeviceId(incoming.getDeviceId());
        if (existing.isPresent()) {
            DeviceEntity db = existing.get();
            db.setName(incoming.getName());
            db.setManufacturer(incoming.getManufacturer());
            db.setModel(incoming.getModel());
            db.setOwner(incoming.getOwner());
            db.setCivilCode(incoming.getCivilCode());
            db.setAddress(incoming.getAddress());
            db.setParentId(incoming.getParentId());
            db.setParental(incoming.getParental());
            db.setRegisterWay(incoming.getRegisterWay());
            db.setSecrecy(incoming.getSecrecy());
            db.setStatus(incoming.getStatus());
            db.setDeviceType(incoming.getDeviceType());
            db.setStreamType(incoming.getStreamType());
            db.setMainChannelId(incoming.getMainChannelId());
            db.setSubChannelId(incoming.getSubChannelId());
            db.setPtzType(incoming.getPtzType());
            db.setPlatformId(incoming.getPlatformId());
            db.setLongitude(incoming.getLongitude());
            db.setLatitude(incoming.getLatitude());
            deviceRepository.save(db);
        } else {
            deviceRepository.save(incoming);
        }
    }

    private static String text(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        if (list.getLength() == 0) {
            return null;
        }
        String v = list.item(0).getTextContent();
        return v == null ? null : v.trim();
    }

    private static Integer intOrNull(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
