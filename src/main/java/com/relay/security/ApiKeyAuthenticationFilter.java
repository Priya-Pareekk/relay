package com.relay.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.relay.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

@Slf4j
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    @Value("${relay.security.api-key:relay-secret-api-key}")
    private String configuredApiKey;

    @Value("${relay.security.api-key-header:X-API-Key}")
    private String apiKeyHeader;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Only enforce API key authentication on /api/** endpoints
        if (!path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String requestApiKey = request.getHeader(apiKeyHeader);

        if (requestApiKey == null || requestApiKey.isBlank()) {
            log.warn("Missing {} header for request to {}", apiKeyHeader, path);
            sendUnauthorizedResponse(response, path, "Missing " + apiKeyHeader + " header");
            return;
        }

        if (!configuredApiKey.equals(requestApiKey)) {
            log.warn("Invalid {} header for request to {}", apiKeyHeader, path);
            sendUnauthorizedResponse(response, path, "Invalid " + apiKeyHeader + " header");
            return;
        }

        // Authenticate request in SecurityContext
        ApiKeyAuthenticationToken authentication = new ApiKeyAuthenticationToken(
                requestApiKey,
                AuthorityUtils.createAuthorityList("ROLE_API_USER", "ROLE_USER")
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private void sendUnauthorizedResponse(HttpServletResponse response, String path, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpStatus.UNAUTHORIZED.value())
                .error(HttpStatus.UNAUTHORIZED.getReasonPhrase())
                .message(message)
                .path(path)
                .timestamp(Instant.now())
                .build();

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
