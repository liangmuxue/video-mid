package com.jizhi.videomid.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
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
            Timestamp lastLogin = rs.getTimestamp("last_login_at");
            if (lastLogin != null) {
                u.setLastLoginAt(lastLogin.toLocalDateTime());
            }
            Timestamp created = rs.getTimestamp("created_at");
            if (created != null) {
                u.setCreatedAt(created.toLocalDateTime());
            }
            Timestamp updated = rs.getTimestamp("updated_at");
            if (updated != null) {
                u.setUpdatedAt(updated.toLocalDateTime());
            }
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
        jdbc.update(
                "INSERT INTO sys_user (username, password, nickname, status) VALUES (?, ?, ?, ?)",
                user.getUsername(),
                user.getPassword(),
                user.getNickname(),
                user.getStatus()
        );
    }

    public void updateLastLogin(Long userId, LocalDateTime time) {
        jdbc.update(
                "UPDATE sys_user SET last_login_at = ? WHERE id = ?",
                Timestamp.valueOf(time),
                userId
        );
    }
}
