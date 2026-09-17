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
import java.util.List;
import java.util.Optional;

@Repository
public class DeviceStreamRepository {

    private static final RowMapper<DeviceStream> MAPPER = (rs, n) -> {
        DeviceStream s = new DeviceStream();
        s.setId(rs.getLong("id"));
        s.setDeviceId(rs.getString("device_id"));
        s.setStreamType(rs.getString("stream_type"));
        s.setChannelId(rs.getString("channel_id"));
        s.setStreamUrl(rs.getString("stream_url"));
        s.setStreamName(rs.getString("stream_name"));
        s.setStatus(rs.getString("status"));
        s.setSortNo(rs.getInt("sort_no"));
        try {
            s.setLiveEnabled(rs.getInt("live_enabled") == 1);
        } catch (Exception e) {
            s.setLiveEnabled(false);
        }
        s.setCreatedAt(readMillis(rs, "created_at"));
        s.setUpdatedAt(readMillis(rs, "updated_at"));
        return s;
    };

    private final JdbcTemplate jdbc;

    public DeviceStreamRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<DeviceStream> findByDeviceId(String deviceId) {
        return jdbc.query(
                "SELECT * FROM device_stream WHERE device_id = ? ORDER BY sort_no ASC, stream_type ASC",
                MAPPER, deviceId);
    }

    public List<DeviceStream> findAll() {
        return jdbc.query("SELECT * FROM device_stream ORDER BY device_id ASC, stream_type ASC", MAPPER);
    }

    public Optional<DeviceStream> findById(Long id) {
        List<DeviceStream> list = jdbc.query("SELECT * FROM device_stream WHERE id = ? LIMIT 1", MAPPER, id);
        return list.stream().findFirst();
    }

    public Optional<DeviceStream> findByDeviceIdAndType(String deviceId, String streamType) {
        List<DeviceStream> list = jdbc.query(
                "SELECT * FROM device_stream WHERE device_id = ? AND stream_type = ? LIMIT 1",
                MAPPER, deviceId, streamType);
        return list.stream().findFirst();
    }

    public Optional<DeviceStream> findLiveByDeviceId(String deviceId) {
        List<DeviceStream> list = jdbc.query(
                "SELECT * FROM device_stream WHERE device_id = ? AND live_enabled = 1 LIMIT 1",
                MAPPER, deviceId);
        return list.stream().findFirst();
    }

    public long insert(DeviceStream s) {
        long now = TsUtil.nowMillis();
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO device_stream (device_id, stream_type, channel_id, stream_url, stream_name, status, sort_no, live_enabled, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, s.getDeviceId());
            ps.setString(2, s.getStreamType());
            ps.setString(3, blankToNull(s.getChannelId()));
            ps.setString(4, s.getStreamUrl());
            ps.setString(5, s.getStreamName());
            ps.setString(6, s.getStatus() == null ? "OFF" : s.getStatus());
            ps.setInt(7, s.getSortNo() == null ? 0 : s.getSortNo());
            ps.setInt(8, Boolean.TRUE.equals(s.getLiveEnabled()) ? 1 : 0);
            ps.setLong(9, now);
            ps.setLong(10, now);
            return ps;
        }, kh);
        Number key = kh.getKey();
        s.setCreatedAt(now);
        s.setUpdatedAt(now);
        return key == null ? 0L : key.longValue();
    }

    public int update(DeviceStream s) {
        long now = TsUtil.nowMillis();
        s.setUpdatedAt(now);
        return jdbc.update(
                "UPDATE device_stream SET channel_id=?, stream_url=?, stream_name=?, status=?, sort_no=?, live_enabled=?, updated_at=? WHERE id=?",
                blankToNull(s.getChannelId()), s.getStreamUrl(), s.getStreamName(),
                s.getStatus(), s.getSortNo() == null ? 0 : s.getSortNo(),
                Boolean.TRUE.equals(s.getLiveEnabled()) ? 1 : 0,
                now, s.getId());
    }

    public int clearLiveByDeviceId(String deviceId) {
        return jdbc.update("UPDATE device_stream SET live_enabled = 0, updated_at = ? WHERE device_id = ?",
                TsUtil.nowMillis(), deviceId);
    }

    public int setLive(Long id, boolean enabled) {
        return jdbc.update("UPDATE device_stream SET live_enabled = ?, updated_at = ? WHERE id = ?",
                enabled ? 1 : 0, TsUtil.nowMillis(), id);
    }

    public int deleteById(Long id) {
        return jdbc.update("DELETE FROM device_stream WHERE id = ?", id);
    }

    public int deleteByDeviceId(String deviceId) {
        return jdbc.update("DELETE FROM device_stream WHERE device_id = ?", deviceId);
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v;
    }

    private static Long readMillis(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }
}
