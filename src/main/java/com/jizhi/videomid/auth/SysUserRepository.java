package com.jizhi.videomid.auth;

import com.jizhi.videomid.util.TsUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class SysUserRepository {

    private static final RowMapper<SysUser> MAPPER = new RowMapper<>() {
        @Override
        public SysUser mapRow(ResultSet rs, int rowNum) throws SQLException {
            SysUser u = new SysUser();
            u.setId(rs.getLong("id"));
            u.setUsername(rs.getString("username"));
            u.setPassword(rs.getString("password"));
            u.setNickname(rs.getString("nickname"));
            u.setStatus(rs.getInt("status"));
            u.setLastLoginAt(readMillis(rs, "last_login_at"));
            u.setCreatedAt(readMillis(rs, "created_at"));
            u.setUpdatedAt(readMillis(rs, "updated_at"));
            return u;
        }
    };

    private final JdbcTemplate jdbc;

    public SysUserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<SysUser> findByUsername(String username) {
        List<SysUser> list = jdbc.query(
                "SELECT * FROM sys_user WHERE username = ? LIMIT 1",
                MAPPER,
                username
        );
        return list.stream().findFirst();
    }

    public Optional<SysUser> findById(Long id) {
        List<SysUser> list = jdbc.query(
                "SELECT * FROM sys_user WHERE id = ? LIMIT 1",
                MAPPER,
                id
        );
        return list.stream().findFirst();
    }

    public long count() {
        Long n = jdbc.queryForObject("SELECT COUNT(1) FROM sys_user", Long.class);
        return n == null ? 0 : n;
    }

    public void insert(SysUser user) {
        long now = TsUtil.nowMillis();
        jdbc.update(
                "INSERT INTO sys_user (username, password, nickname, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                user.getUsername(),
                user.getPassword(),
                user.getNickname(),
                user.getStatus(),
                now,
                now
        );
    }

    public void updateLastLogin(Long userId, long millis) {
        jdbc.update(
                "UPDATE sys_user SET last_login_at = ?, updated_at = ? WHERE id = ?",
                millis,
                TsUtil.nowMillis(),
                userId
        );
    }

    private static Long readMillis(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }
}
