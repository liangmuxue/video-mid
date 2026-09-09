package com.jizhi.videomid.auth;

import com.jizhi.videomid.auth.dto.LoginRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String TOKEN_KEY_PREFIX = "auth:token:";

    private final SysUserRepository userRepository;
    private final JwtService jwtService;
    private final StringRedisTemplate redis;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(SysUserRepository userRepository,
                       JwtService jwtService,
                       StringRedisTemplate redis) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.redis = redis;
    }

    public Map<String, Object> login(LoginRequest request) {
        SysUser user = userRepository.findByUsername(request.getUsername().trim())
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new IllegalArgumentException("账号已禁用");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        String token = jwtService.createToken(user.getId(), user.getUsername());
        // Redis 仅作会话缓存/注销；连不上时降级为纯 JWT，不影响登录
        cacheToken(token, user.getId());
        try {
            userRepository.updateLastLogin(user.getId(), LocalDateTime.now());
        } catch (Exception e) {
            log.warn("update last_login_at failed: {}", e.getMessage());
        }

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("tokenType", "Bearer");
        data.put("expiresIn", jwtService.getTtlSeconds());
        data.put("user", toUserView(user));
        return data;
    }

    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            redis.delete(TOKEN_KEY_PREFIX + token);
        } catch (Exception e) {
            log.warn("Redis logout failed: {}", e.getMessage());
        }
    }

    public Map<String, Object> currentUser(Long userId) {
        SysUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        return toUserView(user);
    }

    /**
     * 校验 token：优先看 Redis；Redis 不可用或没有记录时，回退校验 JWT 本身。
     */
    public Long resolveUserId(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            String cached = redis.opsForValue().get(TOKEN_KEY_PREFIX + token);
            if (cached != null) {
                return Long.parseLong(cached);
            }
            // Redis 有连接但 key 不存在：可能已主动注销；仍允许 JWT 有效期内访问
            // （若需要“强制注销立刻失效”，需保证 Redis 可用）
        } catch (Exception e) {
            log.debug("Redis token lookup skipped: {}", e.getMessage());
        }
        try {
            var claims = jwtService.parse(token);
            return Long.parseLong(claims.getSubject());
        } catch (Exception e) {
            return null;
        }
    }

    public BCryptPasswordEncoder passwordEncoder() {
        return passwordEncoder;
    }

    private void cacheToken(String token, Long userId) {
        try {
            redis.opsForValue().set(
                    TOKEN_KEY_PREFIX + token,
                    String.valueOf(userId),
                    Duration.ofSeconds(jwtService.getTtlSeconds())
            );
        } catch (Exception e) {
            log.warn("Redis unavailable, login continues with JWT only: {}", e.getMessage());
        }
    }

    private Map<String, Object> toUserView(SysUser user) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", user.getId());
        m.put("username", user.getUsername());
        m.put("nickname", user.getNickname());
        m.put("status", user.getStatus());
        m.put("lastLoginAt", user.getLastLoginAt());
        return m;
    }
}
