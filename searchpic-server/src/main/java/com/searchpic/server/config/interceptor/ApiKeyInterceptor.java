package com.searchpic.server.config.interceptor;

import com.searchpic.server.common.context.TenantContextHolder;
import com.searchpic.server.common.exception.BusinessException;
import com.searchpic.server.model.entity.Tenant;
import com.searchpic.server.repository.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyInterceptor implements HandlerInterceptor {

    private final TenantRepository tenantRepository;
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authHeader = request.getHeader("Authorization");

        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("Missing or invalid Authorization header");
            throw new BusinessException(401, "Unauthorized: Valid API Key is required");
        }

        String apiKey = authHeader.substring(BEARER_PREFIX.length()).trim();

        Tenant tenant = tenantRepository.findByApiKey(apiKey)
                .orElseThrow(() -> {
                    log.warn("Invalid API Key used: {}", apiKey);
                    return new BusinessException(401, "Unauthorized: Invalid API Key");
                });

        // Set the tenant ID in ThreadLocal Context for the current request
        TenantContextHolder.setTenantId(tenant.getTenantId());
        log.debug("Authenticated request for Tenant: {}", tenant.getTenantId());

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // Must clear ThreadLocal to prevent memory leaks in thread pools
        TenantContextHolder.clear();
    }
}
