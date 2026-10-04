package com.secretvault.oidc.controller;

import com.secretvault.common.dto.ApiResponse;
import com.secretvault.oidc.dto.OidcDtos;
import com.secretvault.oidc.service.OidcTokenExchangeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/auth/oidc", "/api/v1/oidc/auth"})
public class OidcAuthController {

    private final OidcTokenExchangeService exchangeService;

    public OidcAuthController(OidcTokenExchangeService exchangeService) {
        this.exchangeService = exchangeService;
    }

    @PostMapping({"/token", "/exchange"})
    public ResponseEntity<ApiResponse<OidcDtos.OidcTokenResponse>> exchangeOidcToken(
            @Valid @RequestBody OidcDtos.OidcTokenExchangeRequest request,
            HttpServletRequest httpRequest
    ) {
        String sourceIp = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        OidcDtos.OidcTokenResponse response = exchangeService.exchangeToken(request, sourceIp, userAgent);
        return ResponseEntity.ok(ApiResponse.success(response, "OIDC workload authenticated successfully"));
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
