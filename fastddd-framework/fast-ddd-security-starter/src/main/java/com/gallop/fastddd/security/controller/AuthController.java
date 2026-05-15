package com.gallop.fastddd.security.controller;

import com.gallop.fastddd.security.audit.AuditLog;
import com.gallop.fastddd.security.channel.AuthChannel;
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
import com.gallop.fastddd.security.strategy.AuthStrategy;
import com.gallop.fastddd.security.strategy.AuthStrategyRegistry;
import com.gallop.fastddd.security.strategy.AuthenticationRequest;
import com.gallop.fastddd.common.i18n.Messages;
import com.gallop.fastddd.web.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final SecurityIdentityService identityService;
    private final SecurityPermissionService permissionService;
    private final AuthStrategyRegistry strategyRegistry;

    public AuthController(JwtTokenProvider jwtTokenProvider,
                          JwtProperties jwtProperties,
                          SecurityIdentityService identityService,
                          SecurityPermissionService permissionService,
                          AuthStrategyRegistry strategyRegistry) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtProperties = jwtProperties;
        this.identityService = identityService;
        this.permissionService = permissionService;
        this.strategyRegistry = strategyRegistry;
    }

    @PostMapping("/login")
    @AuditLog(value = "用户登录", type = AuditLog.OperationType.LOGIN)
    public ApiResponse<LoginResponse> login(@RequestBody AuthenticationRequest request,
                                             HttpServletRequest httpRequest) {
        String strategyType = resolveStrategyType(request);
        log.info("用户登录请求: {}, 策略: {}", request.getUsername(), strategyType);

        try {
            // 1. 策略路由
            AuthStrategy strategy = strategyRegistry.getStrategy(strategyType);

            if (strategy == null) {
                return ApiResponse.error(400, Messages.get("security.auth.unsupported", strategyType));
            }

            // 2. 执行认证
            Authentication authentication = strategy.authenticate(request);

            // 3. 获取用户详情
            String authenticatedUsername = authentication.getName();
            SecurityUserDetails userDetails = authentication.getPrincipal() instanceof SecurityUserDetails details
                    ? details
                    : identityService.loadUserByUsername(authenticatedUsername);
            if (userDetails == null) {
                return ApiResponse.error(401, Messages.get("security.auth.user-not-found"));
            }

            // 4. 生成 Token（优先使用请求中的 channel，回退到配置默认值）
            AuthChannel channel = request.getChannel() != null ? request.getChannel() : jwtProperties.getChannel();
            String accessToken = jwtTokenProvider.generateAccessToken(authentication, channel);
            String refreshToken = jwtTokenProvider.generateRefreshToken(authentication, channel);

            // 5. 获取用户信息
            SecurityUserInfo user = identityService.getUserInfo(userDetails.getUserId());
            if (user == null) {
                return ApiResponse.error(401, Messages.get("security.auth.user-info-not-found"));
            }

            // 6. 提取权限
            List<String> permissions = permissionService.getPermissionsByUserId(userDetails.getUserId());

            log.info("用户 {} 登录成功, 通道: {}", authenticatedUsername, channel);

            LoginResponse response = LoginResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(jwtProperties.getExpiration().getSeconds())
                    .user(user)
                    .permissions(permissions)
                    .build();

            return ApiResponse.success(response, Messages.get("security.auth.login.success"));

        } catch (Exception e) {
            log.error("登录失败, 策略: {}: {}", strategyType, e.getMessage(), e);
            return ApiResponse.error(401, Messages.get("security.auth.login.failed"));
        }
    }

    @PostMapping("/logout")
    @AuditLog(value = "用户登出", type = AuditLog.OperationType.LOGOUT)
    public ApiResponse<String> logout() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication != null ? authentication.getName() : "unknown";

        log.info("用户 {} 登出", username);
        SecurityContextHolder.clearContext();
        return ApiResponse.success(null, Messages.get("security.auth.logout.success"));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            return ApiResponse.error(401, Messages.get("security.auth.refresh.invalid"));
        }

        try {
            String username = jwtTokenProvider.getUsernameFromToken(refreshToken);

            SecurityUserDetails userDetails = identityService.loadUserByUsername(username);
            if (userDetails == null) {
                return ApiResponse.error(401, Messages.get("security.auth.user-not-found"));
            }

            List<String> permissions = permissionService.getPermissionsByUserId(userDetails.getUserId());

            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    permissions.stream()
                            .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                            .collect(Collectors.toList())
            );

            AuthChannel channel = jwtTokenProvider.getChannelFromToken(refreshToken);
            if (channel == null) {
                channel = jwtProperties.getChannel();
            }
            String newAccessToken = jwtTokenProvider.generateAccessToken(authentication, channel);

            log.info("用户 {} Token刷新成功", username);

            TokenResponse response = TokenResponse.builder()
                    .accessToken(newAccessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(jwtProperties.getExpiration().getSeconds())
                    .build();

            return ApiResponse.success(response, Messages.get("security.auth.refresh.success"));

        } catch (Exception e) {
            log.error("Token刷新失败: {}", e.getMessage());
            return ApiResponse.error(401, Messages.get("security.auth.refresh.failed"));
        }
    }

    @GetMapping("/userinfo")
    public ApiResponse<UserInfoResponse> getUserInfo() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 优先从 token principal 获取 userId（新 Token）
        String userId = null;
        String username = authentication.getName();

        if (authentication.getPrincipal() instanceof SecurityUserDetails) {
            SecurityUserDetails principal = (SecurityUserDetails) authentication.getPrincipal();
            userId = principal.getUserId();
        }

        // 旧 Token 无 userId，回退到 loadUserByUsername
        if (userId == null) {
            SecurityUserDetails userDetails = identityService.loadUserByUsername(username);
            if (userDetails == null) {
                return ApiResponse.error(404, Messages.get("security.auth.user-not-found"));
            }
            userId = userDetails.getUserId();
        }

        log.debug("获取用户信息: {}, userId: {}", username, userId);

        SecurityUserInfo user = identityService.getUserInfo(userId);
        if (user == null) {
            return ApiResponse.error(404, Messages.get("security.auth.user-info-not-found"));
        }

        List<String> roles = permissionService.getRolesByUserId(userId);
        List<String> permissions = permissionService.getPermissionsByUserId(userId);

        UserInfoResponse response = UserInfoResponse.builder()
                .user(user)
                .permissions(permissions)
                .roles(roles)
                .build();

        return ApiResponse.success(response);
    }

    /**
     * 根据请求中的显式 type 选择认证策略。
     */
    private String resolveStrategyType(AuthenticationRequest request) {
        if (request.getType() != null && !request.getType().isBlank()) {
            return request.getType();
        }
        return "password";
    }
}
