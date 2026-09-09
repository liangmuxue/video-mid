package com.jizhi.videomid.device;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class DeviceRepository {

    private static final RowMapper<Device> MAPPER = (rs, n) -> {
        Device d = new Device();
        d.setId(rs.getLong("id"));
        d.setDeviceId(rs.getString("device_id"));
        d.setName(rs.getString("name"));
        d.setPlatformId(rs.getString("platform_id"));
        d.setStatus(rs.getString("status"));
        d.setManufacturer(rs.getString("manufacturer"));
        d.setModel(rs.getString("model"));
        d.setAddress(rs.getString("address"));
        d.setPtzType(rs.getInt("ptz_type"));
        d.setGatewayId(rs.getString("gateway_id"));
        double lon = rs.getDouble("longitude");
        if (!rs.wasNull()) d.setLongitude(lon);
        double lat = rs.getDouble("latitude");
        if (!rs.wasNull()) d.setLatitude(lat);
        Timestamp c = rs.getTimestamp("created_at");
        if (c != null) d.setCreatedAt(c.toLocalDateTime());
        Timestamp u = rs.getTimestamp("updated_at");
        if (u != null) d.setUpdatedAt(u.toLocalDateTime());
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
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO device (device_id, name, platform_id, status, manufacturer, model, address, ptz_type, gateway_id, longitude, latitude) " +
                            "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, d.getDeviceId());
            ps.setString(2, d.getName());
            ps.setString(3, d.getPlatformId());
            ps.setString(4, d.getStatus() == null ? "OFF" : d.getStatus());
            ps.setString(5, d.getManufacturer());
            ps.setString(6, d.getModel());
            ps.setString(7, d.getAddress());
            ps.setInt(8, d.getPtzType() == null ? 0 : d.getPtzType());
            ps.setString(9, d.getGatewayId());
            if (d.getLongitude() == null) ps.setObject(10, null); else ps.setDouble(10, d.getLongitude());
            if (d.getLatitude() == null) ps.setObject(11, null); else ps.setDouble(11, d.getLatitude());
            return ps;
        }, kh);
        Number key = kh.getKey();
        return key == null ? 0L : key.longValue();
    }

    public int update(Device d) {
        return jdbc.update(
                "UPDATE device SET name=?, platform_id=?, status=?, manufacturer=?, model=?, address=?, ptz_type=?, gateway_id=?, longitude=?, latitude=? WHERE id=?",
                d.getName(), d.getPlatformId(), d.getStatus(), d.getManufacturer(), d.getModel(), d.getAddress(),
                d.getPtzType() == null ? 0 : d.getPtzType(), d.getGatewayId(), d.getLongitude(), d.getLatitude(), d.getId());
    }

    public int deleteById(Long id) {
        return jdbc.update("DELETE FROM device WHERE id = ?", id);
    }
}
