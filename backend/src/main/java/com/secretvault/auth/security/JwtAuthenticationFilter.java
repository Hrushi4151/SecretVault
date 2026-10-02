package com.secretvault.auth.security;

import com.secretvault.auth.entity.User;
import com.secretvault.auth.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Filter that intercepts incoming HTTP requests, extracts the JWT Bearer token,
 * validates the signature, and establishes the authenticated security context.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final com.secretvault.machine.service.MachineSessionService machineSessionService;

    public JwtAuthenticationFilter(
            JwtTokenProvider tokenProvider,
            UserRepository userRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.secretvault.machine.service.MachineSessionService machineSessionService
    ) {
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
        this.machineSessionService = machineSessionService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String bearerToken = getJwtFromRequest(request);

        if (StringUtils.hasText(bearerToken)) {
            if (bearerToken.startsWith("sv_machine_") && machineSessionService != null) {
                // Machine Token Authentication Flow
                Optional<com.secretvault.machine.service.MachineSessionService.MachineSessionContext> contextOpt =
                        machineSessionService.validateToken(bearerToken);
                if (contextOpt.isPresent()) {
                    com.secretvault.machine.entity.MachineIdentity machine = contextOpt.get().machineIdentity();
                    UserPrincipal principal = UserPrincipal.createMachine(machine);

                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            principal.getAuthorities()
                    );
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } else if (tokenProvider.validateToken(bearerToken)) {
                // Standard Human User JWT Authentication Flow
                UUID userId = tokenProvider.getUserIdFromToken(bearerToken);

                Optional<User> userOptional = userRepository.findById(userId);
                if (userOptional.isPresent()) {
                    User user = userOptional.get();
                    UserPrincipal principal = UserPrincipal.create(user);

                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            principal.getAuthorities()
                    );
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
