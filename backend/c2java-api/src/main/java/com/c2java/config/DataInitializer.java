package com.c2java.config;

import com.c2java.domain.User;
import com.c2java.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 초기 데이터 생성
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        log.info("DataInitializer running... User count: {}", userRepository.count());
        if (userRepository.count() == 0) {
            log.info("Creating initial users...");
            
            // 관리자 계정
            User admin = User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin123"))
                    .email("admin@c2java.local")
                    .displayName("시스템 관리자")
                    .role(User.UserRole.ADMIN)
                    .isActive(true)
                    .build();
            userRepository.save(admin);
            
            // 테스트 사용자
            User user = User.builder()
                    .username("user")
                    .password(passwordEncoder.encode("user123"))
                    .email("user@c2java.local")
                    .displayName("테스트 사용자")
                    .role(User.UserRole.USER)
                    .isActive(true)
                    .build();
            userRepository.save(user);
            
            log.info("Initial users created: admin, user");
        }
    }
}
