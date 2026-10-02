package com.secretvault.common.ratelimit;

import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.redis.RedisKeyBuilder;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

/**
 * Aspect that intercepts {@link RateLimited} methods and enforces Redis distributed rate limits.
 */
@Aspect
@Component
public class RateLimitAspect {

    private final DistributedRateLimiter rateLimiter;
    private final RedisKeyBuilder keyBuilder;

    public RateLimitAspect(DistributedRateLimiter rateLimiter, RedisKeyBuilder keyBuilder) {
        this.rateLimiter = rateLimiter;
        this.keyBuilder = keyBuilder;
    }

    @Around("@annotation(rateLimited)")
    public Object enforceRateLimit(ProceedingJoinPoint joinPoint, RateLimited rateLimited) throws Throwable {
        String identifier = resolveIdentifier(rateLimited.type());
        String key = keyBuilder.rateLimitKey(rateLimited.category(), identifier);

        rateLimiter.checkRateLimitOrThrow(
                key,
                rateLimited.limit(),
                Duration.ofSeconds(rateLimited.windowSeconds()),
                rateLimited.message()
        );

        return joinPoint.proceed();
    }

    private String resolveIdentifier(RateLimitIdentifierType type) {
        HttpServletRequest request = getHttpServletRequest();
        String clientIp = extractClientIp(request);
        String userId = extractUserId();

        return switch (type) {
            case IP -> "ip_" + clientIp;
            case USER_ID -> "user_" + (userId != null ? userId : clientIp);
            case IP_AND_USER -> "ip_" + clientIp + "_user_" + (userId != null ? userId : "anon");
            case WORKSPACE_ID -> "ws_" + extractWorkspaceId(request, clientIp);
            case GLOBAL -> "global";
        };
    }

    private HttpServletRequest getHttpServletRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    private String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return "127.0.0.1";
        }
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr : "127.0.0.1";
    }

    private String extractUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getId().toString();
        }
        return null;
    }

    private String extractWorkspaceId(HttpServletRequest request, String fallback) {
        if (request != null) {
            String wsHeader = request.getHeader("X-Workspace-ID");
            if (wsHeader != null && !wsHeader.isBlank()) {
                return wsHeader.trim();
            }
            String uri = request.getRequestURI();
            int idx = uri.indexOf("/workspaces/");
            if (idx != -1) {
                String sub = uri.substring(idx + "/workspaces/".length());
                int nextSlash = sub.indexOf('/');
                return nextSlash != -1 ? sub.substring(0, nextSlash) : sub;
            }
        }
        return fallback;
    }
}
