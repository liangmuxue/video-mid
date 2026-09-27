package com.jizhi.videomid.uniview.nvr;

import com.jizhi.videomid.util.TsUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Repository
public class RecordDeviceRepository {
    private final JdbcTemplate jdbc;
    private final RowMapper<RecordDevice> mapper = (rs, rowNum) -> {
        RecordDevice d = new RecordDevice();
        d.setId(rs.getLong("id"));
        d.setName(rs.getString("name"));
        d.setHost(rs.getString("host"));
        d.setPort(rs.getInt("port"));
        d.setUsername(rs.getString("username"));
        d.setPassword(rs.getString("password"));
        d.setCreatedAt(rs.getLong("created_at"));
        d.setUpdatedAt(rs.getLong("updated_at"));
        return d;
    };

    public RecordDeviceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        ensureTable();
    }

    private void ensureTable() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS record_device (
                  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                  name VARCHAR(128) NOT NULL COMMENT '录像设备名称',
                  host VARCHAR(128) NOT NULL COMMENT 'NVR 地址',
                  port INT NOT NULL COMMENT 'NVR 端口，登录协议为 ONVIF',
                  username VARCHAR(64) NOT NULL COMMENT 'ONVIF 用户名',
                  password VARCHAR(128) NOT NULL COMMENT 'ONVIF 密码',
                  created_at BIGINT NOT NULL COMMENT '创建时间（毫秒时间戳）',
                  updated_at BIGINT NOT NULL COMMENT '更新时间（毫秒时间戳）',
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_record_device_login (host, port)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='宇视录像设备'
                """);
    }

    public List<RecordDevice> findAll() {
        return jdbc.query("SELECT * FROM record_device ORDER BY id DESC", mapper);
    }

    public Optional<RecordDevice> findById(Long id) {
        List<RecordDevice> list = jdbc.query("SELECT * FROM record_device WHERE id = ? LIMIT 1", mapper, id);
        return list.stream().findFirst();
    }

    public Optional<RecordDevice> findOtherByLogin(String host, int port, Long excludeId) {
        String sql = "SELECT * FROM record_device WHERE host = ? AND port = ?";
        List<RecordDevice> list = excludeId == null
                ? jdbc.query(sql, mapper, host, port)
                : jdbc.query(sql + " AND id <> ?", mapper, host, port, excludeId);
        return list.stream().findFirst();
    }

    public long insert(RecordDevice d) {
        long now = TsUtil.nowMillis();
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO record_device (name, host, port, username, password, created_at, updated_at) VALUES (?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, d.getName());
            ps.setString(2, d.getHost());
            ps.setInt(3, d.getPort());
            ps.setString(4, d.getUsername());
            ps.setString(5, d.getPassword());
            ps.setLong(6, now);
            ps.setLong(7, now);
            return ps;
        }, kh);
        d.setCreatedAt(now);
        d.setUpdatedAt(now);
        Number key = kh.getKey();
        return key == null ? 0L : key.longValue();
    }

    public int update(RecordDevice d) {
        long now = TsUtil.nowMillis();
        d.setUpdatedAt(now);
        return jdbc.update(
                "UPDATE record_device SET name=?, host=?, port=?, username=?, password=?, updated_at=? WHERE id=?",
                d.getName(), d.getHost(), d.getPort(), d.getUsername(), d.getPassword(), now, d.getId());
    }

    public int deleteById(Long id) {
        return jdbc.update("DELETE FROM record_device WHERE id = ?", id);
    }
}
