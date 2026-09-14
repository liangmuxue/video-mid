package com.jizhi.videomid.device;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

@Repository
public class DeviceFolderRepository {

    private static final RowMapper<DeviceFolder> MAPPER = (rs, n) -> {
        DeviceFolder f = new DeviceFolder();
        f.setId(rs.getLong("id"));
        long pid = rs.getLong("parent_id");
        if (!rs.wasNull()) f.setParentId(pid);
        f.setName(rs.getString("name"));
        f.setSortNo(rs.getInt("sort_no"));
        f.setPath(rs.getString("path"));
        Timestamp c = rs.getTimestamp("created_at");
        if (c != null) f.setCreatedAt(c.toLocalDateTime());
        Timestamp u = rs.getTimestamp("updated_at");
        if (u != null) f.setUpdatedAt(u.toLocalDateTime());
        return f;
    };

    private final JdbcTemplate jdbc;

    public DeviceFolderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<DeviceFolder> findAll() {
        return jdbc.query(
                "SELECT * FROM device_folder ORDER BY sort_no ASC, id ASC",
                MAPPER);
    }

    public Optional<DeviceFolder> findById(Long id) {
        List<DeviceFolder> list = jdbc.query(
                "SELECT * FROM device_folder WHERE id = ? LIMIT 1", MAPPER, id);
        return list.stream().findFirst();
    }

    public List<DeviceFolder> findByParentId(Long parentId) {
        if (parentId == null) {
            return jdbc.query(
                    "SELECT * FROM device_folder WHERE parent_id IS NULL ORDER BY sort_no ASC, id ASC",
                    MAPPER);
        }
        return jdbc.query(
                "SELECT * FROM device_folder WHERE parent_id = ? ORDER BY sort_no ASC, id ASC",
                MAPPER, parentId);
    }

    public long countChildren(Long parentId) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(1) FROM device_folder WHERE parent_id = ?",
                Long.class, parentId);
        return n == null ? 0L : n;
    }

    public long insert(DeviceFolder f) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO device_folder (parent_id, name, sort_no, path) VALUES (?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            if (f.getParentId() == null) {
                ps.setNull(1, Types.BIGINT);
            } else {
                ps.setLong(1, f.getParentId());
            }
            ps.setString(2, f.getName());
            ps.setInt(3, f.getSortNo() == null ? 0 : f.getSortNo());
            ps.setString(4, f.getPath());
            return ps;
        }, kh);
        Number key = kh.getKey();
        return key == null ? 0L : key.longValue();
    }

    public int update(DeviceFolder f) {
        return jdbc.update(
                "UPDATE device_folder SET parent_id=?, name=?, sort_no=?, path=? WHERE id=?",
                f.getParentId(), f.getName(),
                f.getSortNo() == null ? 0 : f.getSortNo(),
                f.getPath(), f.getId());
    }

    public int updatePath(Long id, String path) {
        return jdbc.update("UPDATE device_folder SET path=? WHERE id=?", path, id);
    }

    public int deleteById(Long id) {
        return jdbc.update("DELETE FROM device_folder WHERE id = ?", id);
    }

    /** path 前缀匹配：含自身与全部子孙 */
    public List<DeviceFolder> findByPathPrefix(String pathPrefix) {
        return jdbc.query(
                "SELECT * FROM device_folder WHERE path LIKE ? ORDER BY path ASC",
                MAPPER, pathPrefix + "%");
    }
}
