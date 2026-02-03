package com.c2java.util;

import com.c2java.domain.User;
import com.c2java.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Spring Security 컨텍스트에서 현재 사용자 정보를 가져오는 유틸리티
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityUtil {

    private final UserRepository userRepository;

    /**
     * 현재 로그인한 사용자의 UUID 반환
     */
    public UUID getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("No authenticated user found");
                return null;
            }

            Object principal = authentication.getPrincipal();
            
            if (principal instanceof UserDetails) {
                String username = ((UserDetails) principal).getUsername();
                return userRepository.findByUsername(username)
                        .map(User::getId)
                        .orElse(null);
            }
            
            return null;
        } catch (Exception e) {
            log.error("Failed to get current user ID", e);
            return null;
        }
    }

    /**
     * 현재 로그인한 사용자의 username 반환
     */
    public String getCurrentUsername() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication == null || !authentication.isAuthenticated()) {
                return null;
            }

            Object principal = authentication.getPrincipal();
            
            if (principal instanceof UserDetails) {
                return ((UserDetails) principal).getUsername();
            }
            
            return null;
        } catch (Exception e) {
            log.error("Failed to get current username", e);
            return null;
        }
    }

    /**
     * 현재 사용자가 관리자인지 확인
     */
    public boolean isCurrentUserAdmin() {
        try {
            String username = getCurrentUsername();
            if (username == null) {
                return false;
            }
            
            return userRepository.findByUsername(username)
                    .map(user -> user.getRole() == User.UserRole.ADMIN)
                    .orElse(false);
        } catch (Exception e) {
            log.error("Failed to check admin role", e);
            return false;
        }
    }

    /**
     * 현재 사용자 엔티티 반환
     */
    public User getCurrentUser() {
        try {
            String username = getCurrentUsername();
            if (username == null) {
                return null;
            }
            
            return userRepository.findByUsername(username).orElse(null);
        } catch (Exception e) {
            log.error("Failed to get current user", e);
            return null;
        }
    }
}
