package com.jizhi.videomid.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 首次启动自动创建默认管理员：admin / admin123
 */
@Component
public class AdminUserInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserInitializer.class);

    private final SysUserRepository userRepository;
    private final AuthService authService;

    public AdminUserInitializer(SysUserRepository userRepository, AuthService authService) {
        this.userRepository = userRepository;
        this.authService = authService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        SysUser admin = new SysUser();
        admin.setUsername("admin");
        admin.setPassword(authService.passwordEncoder().encode("admin123"));
        admin.setNickname("管理员");
        admin.setStatus(1);
        userRepository.insert(admin);
        log.info("Seeded default admin user: admin / admin123");
    }
}
