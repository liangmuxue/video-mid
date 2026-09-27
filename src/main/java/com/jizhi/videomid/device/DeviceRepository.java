package com.jizhi.videomid.device;

import com.jizhi.videomid.util.TsUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class DeviceRepository {

    private static final RowMapper<Device> MAPPER = (rs, n) -> {
        Device d = new Device();
        d.setId(rs.getLong("id"));
        d.setDeviceId(rs.getString("device_id"));
        d.setName(rs.getString("name"));
        d.setPlatformId(rs.getString("platform_id"));
        d.setVendor(rs.getString("vendor"));
        long folderId = rs.getLong("folder_id");
        if (!rs.wasNull()) d.setFolderId(folderId);
        d.setStatus(rs.getInt("status"));
        d.setManufacturer(rs.getString("manufacturer"));
        d.setModel(rs.getString("model"));
        d.setAddress(rs.getString("address"));
        d.setPtzType(rs.getInt("ptz_type"));
        d.setGatewayId(rs.getString("gateway_id"));
        double lon = rs.getDouble("longitude");
        if (!rs.wasNull()) d.setLongitude(lon);
        double lat = rs.getDouble("latitude");
        if (!rs.wasNull()) d.setLatitude(lat);
        d.setHost(rs.getString("host"));
        int port = rs.getInt("port");
        if (!rs.wasNull()) d.setPort(port);
        d.setUsername(rs.getString("username"));
        d.setPassword(rs.getString("password"));
        d.setAccessChannel(rs.getString("access_channel"));
        d.setAccessStatus(rs.getString("access_status"));
        d.setAccessError(rs.getString("access_error"));
        d.setLanIp(rs.getString("lan_ip"));
        long recordDeviceId = rs.getLong("record_device_id");
        if (!rs.wasNull()) d.setRecordDeviceId(recordDeviceId);
        int recordChannel = rs.getInt("record_channel");
        if (!rs.wasNull()) d.setRecordChannel(recordChannel);
        d.setRecordChannelName(rs.getString("record_channel_name"));
        d.setCreatedAt(readMillis(rs, "created_at"));
        d.setUpdatedAt(readMillis(rs, "updated_at"));
        return d;
    };

    private final JdbcTemplate jdbc;

    public DeviceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        ensureVendorSplit();
    }

    private static final String DEVICE_SELECT = """
            SELECT d.id, d.device_id, d.name, d.platform_id, d.folder_id, d.status, d.manufacturer, d.model,
                   d.address, d.ptz_type, d.gateway_id, d.longitude, d.latitude, d.vendor, d.created_at, d.updated_at,
                   u.host, u.port, u.username, u.password, u.access_channel, u.access_status, u.access_error,
                   u.lan_ip, u.record_device_id, u.record_channel, u.record_channel_name
            FROM device d
            LEFT JOIN uniview_device u ON u.device_pk = d.id
            """;

    /** 已有库补 vendor，把设备表上的宇视登录拷到 uniview_device，然后删掉这些旧列。 */
    private void ensureVendorSplit() {
        addColumnIfMissing("vendor", "VARCHAR(16) NOT NULL DEFAULT 'MOCK' COMMENT 'MOCK/UNIVIEW/HIKVISION'");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS uniview_device (
                  device_pk BIGINT NOT NULL COMMENT 'device.id',
                  host VARCHAR(128) DEFAULT NULL,
                  port INT DEFAULT NULL,
                  username VARCHAR(64) DEFAULT NULL,
                  password VARCHAR(128) DEFAULT NULL,
                  access_channel VARCHAR(32) DEFAULT NULL,
                  access_status VARCHAR(32) NOT NULL DEFAULT 'unknown',
                  access_error VARCHAR(512) DEFAULT NULL,
                  lan_ip VARCHAR(64) DEFAULT NULL,
                  record_device_id BIGINT DEFAULT NULL,
                  record_channel INT DEFAULT NULL,
                  record_channel_name VARCHAR(128) DEFAULT NULL,
                  PRIMARY KEY (device_pk),
                  UNIQUE KEY uk_uniview_login (host, port, access_channel),
                  UNIQUE KEY uk_uniview_record_channel (record_device_id, record_channel),
                  KEY idx_uniview_lan_ip (lan_ip)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='宇视设备登录与录像通道'
                """);
        if (columnExists("device", "host")) {
            copyUniviewColumns();
            dropLeftoverUniviewColumns();
        }
        jdbc.update("""
                UPDATE device d
                JOIN uniview_device u ON u.device_pk = d.id
                SET d.vendor = 'UNIVIEW'
                WHERE d.vendor IS NULL OR d.vendor = 'MOCK'
                """);
    }

    private void copyUniviewColumns() {
        boolean lan = columnExists("device", "lan_ip");
        boolean record = columnExists("device", "record_device_id");
        String extra = (lan ? ", lan_ip" : "")
                + (record ? ", record_device_id, record_channel, record_channel_name" : "");
        String where = "host IS NOT NULL"
                + (lan ? " OR lan_ip IS NOT NULL" : "")
                + (record ? " OR record_device_id IS NOT NULL" : "");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, host, port, username, password, access_channel, access_status, access_error"
                        + extra + " FROM device WHERE " + where);
        for (Map<String, Object> row : rows) {
            long id = ((Number) row.get("id")).longValue();
            Integer exists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM uniview_device WHERE device_pk = ?", Integer.class, id);
            if (exists != null && exists > 0) {
                continue;
            }
            jdbc.update("""
                    INSERT INTO uniview_device (device_pk, host, port, username, password, access_channel, access_status, access_error, lan_ip, record_device_id, record_channel, record_channel_name)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                    """,
                    id,
                    row.get("host"),
                    row.get("port"),
                    row.get("username"),
                    row.get("password"),
                    row.get("access_channel"),
                    row.get("access_status") == null ? "unknown" : row.get("access_status"),
                    row.get("access_error"),
                    row.get("lan_ip"),
                    row.get("record_device_id"),
                    row.get("record_channel"),
                    row.get("record_channel_name"));
        }
    }

    private void dropLeftoverUniviewColumns() {
        dropIndexIfExists("uk_device_login");
        dropIndexIfExists("uk_device_record_channel");
        dropIndexIfExists("idx_lan_ip");
        for (String column : new String[]{
                "host", "port", "username", "password", "access_channel", "access_status", "access_error",
                "lan_ip", "record_device_id", "record_channel", "record_channel_name"
        }) {
            if (columnExists("device", column)) {
                jdbc.execute("ALTER TABLE device DROP COLUMN " + column);
            }
        }
    }

    private void dropIndexIfExists(String index) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device' AND INDEX_NAME = ?",
                Integer.class, index);
        if (count != null && count > 0) {
            jdbc.execute("ALTER TABLE device DROP INDEX " + index);
        }
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class, table, column);
        return count != null && count > 0;
    }

    private void addColumnIfMissing(String column, String definition) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device' AND COLUMN_NAME = ?",
                Integer.class, column);
        if (count == null || count == 0) {
            jdbc.execute("ALTER TABLE device ADD COLUMN " + column + " " + definition);
        }
    }

    public List<Device> findAll() {
        return jdbc.query(DEVICE_SELECT + " ORDER BY d.id DESC", MAPPER);
    }

    public Optional<Device> findById(Long id) {
        List<Device> list = jdbc.query(DEVICE_SELECT + " WHERE d.id = ? LIMIT 1", MAPPER, id);
        return list.stream().findFirst();
    }

    public Optional<Device> findByDeviceId(String deviceId) {
        List<Device> list = jdbc.query(DEVICE_SELECT + " WHERE d.device_id = ? LIMIT 1", MAPPER, deviceId);
        return list.stream().findFirst();
    }

    public long insert(Device d) {
        long now = TsUtil.nowMillis();
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO device (device_id, name, platform_id, folder_id, status, manufacturer, model, address, ptz_type, gateway_id, longitude, latitude, vendor, created_at, updated_at) " +
                            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, d.getDeviceId());
            ps.setString(2, d.getName());
            ps.setString(3, d.getPlatformId());
            if (d.getFolderId() == null) ps.setNull(4, Types.BIGINT); else ps.setLong(4, d.getFolderId());
            ps.setInt(5, d.getStatus() == null ? DeviceStatus.DISABLED : d.getStatus());
            ps.setString(6, d.getManufacturer());
            ps.setString(7, d.getModel());
            ps.setString(8, d.getAddress());
            ps.setInt(9, d.getPtzType() == null ? 0 : d.getPtzType());
            ps.setString(10, d.getGatewayId());
            if (d.getLongitude() == null) ps.setObject(11, null); else ps.setDouble(11, d.getLongitude());
            if (d.getLatitude() == null) ps.setObject(12, null); else ps.setDouble(12, d.getLatitude());
            ps.setString(13, d.getVendor() == null || d.getVendor().isBlank() ? AccessVendor.MOCK.name() : d.getVendor());
            ps.setLong(14, now);
            ps.setLong(15, now);
            return ps;
        }, kh);
        Number key = kh.getKey();
        long id = key == null ? 0L : key.longValue();
        d.setCreatedAt(now);
        d.setUpdatedAt(now);
        return id;
    }

    public int update(Device d) {
        long now = TsUtil.nowMillis();
        d.setUpdatedAt(now);
        return jdbc.update(
                "UPDATE device SET name=?, platform_id=?, folder_id=?, status=?, manufacturer=?, model=?, address=?, ptz_type=?, gateway_id=?, longitude=?, latitude=?, vendor=?, updated_at=? WHERE id=?",
                d.getName(), d.getPlatformId(), d.getFolderId(), d.getStatus(), d.getManufacturer(), d.getModel(), d.getAddress(),
                d.getPtzType() == null ? 0 : d.getPtzType(), d.getGatewayId(), d.getLongitude(), d.getLatitude(),
                d.getVendor() == null || d.getVendor().isBlank() ? AccessVendor.MOCK.name() : d.getVendor(),
                now, d.getId());
    }

    public int updateAccess(Long id, String status, String error) {
        return jdbc.update(
                "UPDATE uniview_device SET access_status=?, access_error=? WHERE device_pk=?",
                status == null || status.isBlank() ? "unknown" : status,
                error,
                id);
    }

    /** 宇视设备写入 uniview_device；其他平台删掉对应行。 */
    public void saveUniview(Device d) {
        if (d.getId() == null) {
            return;
        }
        if (!AccessVendor.UNIVIEW.name().equals(d.getVendor())) {
            jdbc.update("DELETE FROM uniview_device WHERE device_pk = ?", d.getId());
            return;
        }
        jdbc.update("""
                INSERT INTO uniview_device (device_pk, host, port, username, password, access_channel, access_status, access_error, lan_ip, record_device_id, record_channel, record_channel_name)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE
                  host = VALUES(host), port = VALUES(port), username = VALUES(username), password = VALUES(password),
                  access_channel = VALUES(access_channel), access_status = VALUES(access_status), access_error = VALUES(access_error),
                  lan_ip = VALUES(lan_ip), record_device_id = VALUES(record_device_id),
                  record_channel = VALUES(record_channel), record_channel_name = VALUES(record_channel_name)
                """,
                d.getId(), d.getHost(), d.getPort(), d.getUsername(), d.getPassword(), d.getAccessChannel(),
                d.getAccessStatus() == null || d.getAccessStatus().isBlank() ? "unknown" : d.getAccessStatus(),
                d.getAccessError(), d.getLanIp(), d.getRecordDeviceId(), d.getRecordChannel(), d.getRecordChannelName());
    }

    public Optional<Device> findOtherByLogin(String host, int port, String channel, Long excludeId) {
        String sql = DEVICE_SELECT + " WHERE u.host = ? AND u.port = ? AND u.access_channel = ?";
        List<Device> list = excludeId == null
                ? jdbc.query(sql, MAPPER, host, port, channel)
                : jdbc.query(sql + " AND d.id <> ?", MAPPER, host, port, channel, excludeId);
        return list.stream().findFirst();
    }

    public int countByRecordDeviceId(Long recordDeviceId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM uniview_device WHERE record_device_id = ?", Integer.class, recordDeviceId);
        return count == null ? 0 : count;
    }

    public Optional<Device> findOtherByRecordChannel(Long recordDeviceId, int channel, Long excludeId) {
        String sql = DEVICE_SELECT + " WHERE u.record_device_id = ? AND u.record_channel = ?";
        List<Device> list = excludeId == null
                ? jdbc.query(sql, MAPPER, recordDeviceId, channel)
                : jdbc.query(sql + " AND d.id <> ?", MAPPER, recordDeviceId, channel, excludeId);
        return list.stream().findFirst();
    }

    public int updateStatus(Long id, int status) {
        return jdbc.update("UPDATE device SET status=?, updated_at=? WHERE id=?", status, TsUtil.nowMillis(), id);
    }

    public long countInFolder(Long folderId) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(1) FROM device WHERE folder_id = ?", Long.class, folderId);
        return n == null ? 0L : n;
    }

    public Map<Long, Integer> countByFolderId() {
        Map<Long, Integer> map = new HashMap<>();
        jdbc.query("SELECT folder_id, COUNT(1) AS cnt FROM device WHERE folder_id IS NOT NULL GROUP BY folder_id",
                rs -> {
                    map.put(rs.getLong("folder_id"), rs.getInt("cnt"));
                });
        return map;
    }

    public int deleteById(Long id) {
        jdbc.update("DELETE FROM uniview_device WHERE device_pk = ?", id);
        return jdbc.update("DELETE FROM device WHERE id = ?", id);
    }

    private static Long readMillis(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }
}
