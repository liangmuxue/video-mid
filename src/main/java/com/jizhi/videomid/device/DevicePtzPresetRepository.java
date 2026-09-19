package com.jizhi.videomid.device;

import com.jizhi.videomid.util.TsUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class DevicePtzPresetRepository {

    private static final RowMapper<DevicePtzPreset> MAPPER = DevicePtzPresetRepository::mapRow;

    private final JdbcTemplate jdbc;

    public DevicePtzPresetRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<DevicePtzPreset> findByDeviceId(String deviceId) {
        return jdbc.query(
                "SELECT * FROM device_ptz_preset WHERE device_id = ? ORDER BY preset_index ASC",
                MAPPER, deviceId);
    }

    public Optional<DevicePtzPreset> find(String deviceId, int presetIndex) {
        List<DevicePtzPreset> list = jdbc.query(
                "SELECT * FROM device_ptz_preset WHERE device_id = ? AND preset_index = ? LIMIT 1",
                MAPPER, deviceId, presetIndex);
        return list.stream().findFirst();
    }

    public void insert(DevicePtzPreset p) {
        long now = TsUtil.nowMillis();
        jdbc.update(
                "INSERT INTO device_ptz_preset (device_id, preset_index, name, zoom, created_at, updated_at) VALUES (?,?,?,?,?,?)",
                p.getDeviceId(),
                p.getPresetIndex(),
                p.getName(),
                p.getZoom(),
                now,
                now
        );
    }

    public void update(DevicePtzPreset p) {
        jdbc.update(
                "UPDATE device_ptz_preset SET name = ?, zoom = ?, updated_at = ? WHERE device_id = ? AND preset_index = ?",
                p.getName(),
                p.getZoom(),
                TsUtil.nowMillis(),
                p.getDeviceId(),
                p.getPresetIndex()
        );
    }

    private static DevicePtzPreset mapRow(ResultSet rs, int n) throws SQLException {
        DevicePtzPreset p = new DevicePtzPreset();
        p.setId(rs.getLong("id"));
        p.setDeviceId(rs.getString("device_id"));
        p.setPresetIndex(rs.getInt("preset_index"));
        p.setName(rs.getString("name"));
        double zoom = rs.getDouble("zoom");
        p.setZoom(rs.wasNull() ? null : zoom);
        long c = rs.getLong("created_at");
        p.setCreatedAt(rs.wasNull() ? null : c);
        long u = rs.getLong("updated_at");
        p.setUpdatedAt(rs.wasNull() ? null : u);
        return p;
    }
}
