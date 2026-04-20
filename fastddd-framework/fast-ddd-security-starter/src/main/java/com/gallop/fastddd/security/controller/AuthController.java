package com.gallop.fastddd.security.controller;

import com.gallop.fastddd.security.audit.AuditLog;
import com.gallop.fastddd.security.dto.LoginRequest;
import com.gallop.fastddd.security.dto.LoginResponse;
import com.gallop.fastddd.security.dto.RefreshRequest;
import com.gallop.fastddd.security.dto.TokenResponse;
import com.gallop.fastddd.security.dto.UserInfoResponse;
import com.gallop.fastddd.security.jwt.JwtProperties;
import com.gallop.fastddd.security.jwt.JwtTokenProvider;
import com.gallop.fastddd.security.spi.SecurityIdentityService;
import com.gallop.fastddd.security.spi.SecurityPermissionService;
import com.gallop.fastddd.security.spi.SecurityUserDetails;
import com.gallop.fastddd.security.spi.SecurityUserInfo;
import com.gallop.fastddd.web.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final SecurityIdentityService identityService;
    private final SecurityPermissionService permissionService;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtTokenProvider jwtTokenProvider,
                          JwtProperties jwtProperties,
                          SecurityIdentityService identityService,
                          SecurityPermissionService permissionService) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtProperties = jwtProperties;
        this.identityService = identityService;
        this.permissionService = permissionService;
    }

    @PostMapping("/login")
    @AuditLog(value = "用户登录", type = AuditLog.OperationType.LOGIN)
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                             HttpServletRequest httpRequest) {
        log.info("用户登录请求: {}", request.getUsername());

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            SecurityUserDetails userDetails = identityService.loadUserByUsername(request.getUsername());
            if (userDetails == null) {
                return ApiResponse.error(401, "用户不存在");
            }

            String accessToken = jwtTokenProvider.generateAccessToken(authentication);
            String refreshToken = jwtTokenProvider.generateRefreshToken(authentication);

            SecurityUserInfo user = identityService.getUserInfo(userDetails.getUserId());
            if (user == null) {
                return ApiResponse.error(401, "用户信息不存在");
            }

            // Extract permissions from the authenticated principal (already queried by UserDetailsService)
            List<String> permissions = authentication.getAuthorities().stream()
                    .map(auth -> auth.getAuthority())
                    .filter(auth -> !auth.startsWith("ROLE_"))
                    .collect(Collectors.toList());

            log.info("用户 {} 登录成功", request.getUsername());

            LoginResponse response = LoginResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(jwtProperties.getExpiration().getSeconds())
                    .user(user)
                    .permissions(permissions)
                    .build();

            return ApiResponse.success(response, "登录成功");

        } catch (Exception e) {
            log.error("用户 {} 登录失败: {}", request.getUsername(), e.getMessage(), e);
            return ApiResponse.error(401, "用户名或密码错误");
        }
    }

    @PostMapping("/logout")
    @AuditLog(value = "用户登出", type = AuditLog.OperationType.LOGOUT)
    public ApiResponse<String> logout() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication != null ? authentication.getName() : "unknown";

        log.info("用户 {} 登出", username);
        SecurityContextHolder.clearContext();
        return ApiResponse.success(null, "登出成功");
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            return ApiResponse.error(401, "刷新Token无效或已过期");
        }

        try {
            String username = jwtTokenProvider.getUsernameFromToken(refreshToken);

            SecurityUserDetails userDetails = identityService.loadUserByUsername(username);
            if (userDetails == null) {
                return ApiResponse.error(401, "用户不存在");
            }

            // Use permissions from SPI for refresh (no UserDetailsService involved here)
            List<String> permissions = permissionService.getPermissionsByUserId(userDetails.getUserId());

            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    username,
                    null,
                    permissions.stream()
                            .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                            .collect(Collectors.toList())
            );

            String newAccessToken = jwtTokenProvider.generateAccessToken(authentication);

            log.info("用户 {} Token刷新成功", username);

            TokenResponse response = TokenResponse.builder()
                    .accessToken(newAccessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(jwtProperties.getExpiration().getSeconds())
                    .build();

            return ApiResponse.success(response, "Token刷新成功");

        } catch (Exception e) {
            log.error("Token刷新失败: {}", e.getMessage());
            return ApiResponse.error(401, "Token刷新失败: " + e.getMessage());
        }
    }

    @GetMapping("/userinfo")
    public ApiResponse<UserInfoResponse> getUserInfo() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        log.debug("获取用户信息: {}", username);

        SecurityUserDetails userDetails = identityService.loadUserByUsername(username);
        if (userDetails == null) {
            return ApiResponse.error(404, "用户不存在");
        }

        SecurityUserInfo user = identityService.getUserInfo(userDetails.getUserId());
        if (user == null) {
            return ApiResponse.error(404, "用户信息不存在");
        }

        List<String> roles = permissionService.getRolesByUserId(userDetails.getUserId());
        List<String> permissions = permissionService.getPermissionsByUserId(userDetails.getUserId());

        UserInfoResponse response = UserInfoResponse.builder()
                .user(user)
                .permissions(permissions)
                .roles(roles)
                .build();

        return ApiResponse.success(response);
    }
}
