package com.deac.user.security;

import com.deac.user.exception.MyException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

@Component
public class TokenFilter extends OncePerRequestFilter {

    private static final String[] excludedEndPoints = new String[]{"/api/user/login", "/api/user/register", "/api/user/forgot", "/api/user/reset"};

    private final TokenProvider tokenProvider;

    private final ObjectMapper objectMapper;

    public TokenFilter(TokenProvider tokenProvider, ObjectMapper objectMapper) {
        this.tokenProvider = tokenProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, FilterChain filterChain) throws IOException, ServletException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
                throw new MyException("You are not logged in", HttpStatus.UNAUTHORIZED);
            }
            if (httpServletRequest.getCookies() == null) {
                SecurityContextHolder.clearContext();
                throw new MyException("Expired cookie", HttpStatus.UNAUTHORIZED);
            }
            Optional<String> jwt = Arrays.stream(httpServletRequest.getCookies()).filter(cookie -> cookie.getName().equals("jwt")).map(Cookie::getValue).findFirst();
            if (jwt.isEmpty()) {
                SecurityContextHolder.clearContext();
                throw new MyException("Expired cookie", HttpStatus.UNAUTHORIZED);
            }
            if (!httpServletRequest.getRequestURI().equals("/api/user/refresh") && !httpServletRequest.getRequestURI().equals("/api/user/logout")) {
                try {
                    tokenProvider.validateToken(jwt.get());
                } catch (ExpiredJwtException e) {
                    throw e;
                } catch (JwtException | IllegalArgumentException e) {
                    SecurityContextHolder.clearContext();
                    throw e;
                }
                if (!tokenProvider.getUsernameFromToken(jwt.get()).equals(authentication.getName())) {
                    SecurityContextHolder.clearContext();
                    throw new MyException("Invalid token", HttpStatus.UNAUTHORIZED);
                }
            }
        } catch (MyException e) {
            httpServletResponse.setStatus(e.getHttpStatus().value());
            httpServletResponse.getWriter().write(objectMapper.writeValueAsString(e));
            return;
        }

        filterChain.doFilter(httpServletRequest, httpServletResponse);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return Arrays.stream(excludedEndPoints).anyMatch(e -> new AntPathMatcher().match(e, request.getRequestURI()));
    }

}
