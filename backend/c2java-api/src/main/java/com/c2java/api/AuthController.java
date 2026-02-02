package com.c2java.api;

import com.c2java.dto.AuthRequest;
import com.c2java.dto.AuthResponse;
import com.c2java.dto.RegisterRequest;
import com.c2java.dto.UserDto;
import com.c2java.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * 인증 API 컨트롤러
 */
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "인증 API")
public class AuthController {

    private final AuthService authService;

    /**
     * 로그인
     */
    @PostMapping("/login")
    @Operation(summary = "로그인", description = "사용자명과 비밀번호로 로그인합니다.")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        log.info("Login attempt for user: {}", request.getUsername());
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 회원가입
     */
    @PostMapping("/register")
    @Operation(summary = "회원가입", description = "새 사용자를 등록합니다.")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Registration attempt for user: {}", request.getUsername());
        AuthResponse response = authService.register(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 현재 사용자 정보 조회
     */
    @GetMapping("/me")
    @Operation(summary = "내 정보", description = "현재 로그인한 사용자 정보를 조회합니다.")
    public ResponseEntity<UserDto> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        UserDto user = authService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(user);
    }

    /**
     * 토큰 갱신
     */
    @PostMapping("/refresh")
    @Operation(summary = "토큰 갱신", description = "새 액세스 토큰을 발급합니다.")
    public ResponseEntity<AuthResponse> refreshToken(@AuthenticationPrincipal UserDetails userDetails) {
        AuthResponse response = authService.refreshToken(userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    /**
     * 비밀번호 변경
     */
    @PutMapping("/password")
    @Operation(summary = "비밀번호 변경", description = "비밀번호를 변경합니다.")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody PasswordChangeRequest request) {
        authService.changePassword(userDetails.getUsername(), request.oldPassword(), request.newPassword());
        return ResponseEntity.ok().build();
    }

    /**
     * 비밀번호 변경 요청 DTO
     */
    public record PasswordChangeRequest(String oldPassword, String newPassword) {}
}
