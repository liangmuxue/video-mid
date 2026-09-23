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
        d.setCreatedAt(readMillis(rs, "created_at"));
        d.setUpdatedAt(readMillis(rs, "updated_at"));
        return d;
    };

    private final JdbcTemplate jdbc;

    public DeviceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Device> findAll() {
        return jdbc.query("SELECT * FROM device ORDER BY id DESC", MAPPER);
    }

    public Optional<Device> findById(Long id) {
        List<Device> list = jdbc.query("SELECT * FROM device WHERE id = ? LIMIT 1", MAPPER, id);
        return list.stream().findFirst();
    }

    public Optional<Device> findByDeviceId(String deviceId) {
        List<Device> list = jdbc.query("SELECT * FROM device WHERE device_id = ? LIMIT 1", MAPPER, deviceId);
        return list.stream().findFirst();
    }

    public long insert(Device d) {
        long now = TsUtil.nowMillis();
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO device (device_id, name, platform_id, folder_id, status, manufacturer, model, address, ptz_type, gateway_id, longitude, latitude, host, port, username, password, access_channel, access_status, access_error, created_at, updated_at) " +
                            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
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
            ps.setString(13, d.getHost());
            if (d.getPort() == null) ps.setNull(14, Types.INTEGER); else ps.setInt(14, d.getPort());
            ps.setString(15, d.getUsername());
            ps.setString(16, d.getPassword());
            ps.setString(17, d.getAccessChannel());
            ps.setString(18, d.getAccessStatus() == null || d.getAccessStatus().isBlank() ? "unknown" : d.getAccessStatus());
            ps.setString(19, d.getAccessError());
            ps.setLong(20, now);
            ps.setLong(21, now);
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
                "UPDATE device SET name=?, platform_id=?, folder_id=?, status=?, manufacturer=?, model=?, address=?, ptz_type=?, gateway_id=?, longitude=?, latitude=?, host=?, port=?, username=?, password=?, access_channel=?, access_status=?, access_error=?, updated_at=? WHERE id=?",
                d.getName(), d.getPlatformId(), d.getFolderId(), d.getStatus(), d.getManufacturer(), d.getModel(), d.getAddress(),
                d.getPtzType() == null ? 0 : d.getPtzType(), d.getGatewayId(), d.getLongitude(), d.getLatitude(),
                d.getHost(), d.getPort(), d.getUsername(), d.getPassword(), d.getAccessChannel(),
                d.getAccessStatus() == null || d.getAccessStatus().isBlank() ? "unknown" : d.getAccessStatus(),
                d.getAccessError(), now, d.getId());
    }

    public int updateAccess(Long id, String status, String error) {
        return jdbc.update(
                "UPDATE device SET access_status=?, access_error=?, updated_at=? WHERE id=?",
                status == null || status.isBlank() ? "unknown" : status,
                error,
                TsUtil.nowMillis(),
                id);
    }

    public Optional<Device> findOtherByLogin(String host, int port, String channel, Long excludeId) {
        String sql = "SELECT * FROM device WHERE host = ? AND port = ? AND access_channel = ?";
        List<Device> list = excludeId == null
                ? jdbc.query(sql, MAPPER, host, port, channel)
                : jdbc.query(sql + " AND id <> ?", MAPPER, host, port, channel, excludeId);
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
        return jdbc.update("DELETE FROM device WHERE id = ?", id);
    }

    private static Long readMillis(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }
}
