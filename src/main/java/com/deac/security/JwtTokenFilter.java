package com.deac.security;

import com.deac.exception.MyException;
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
public class JwtTokenFilter extends OncePerRequestFilter {

    private static final String[] excludedEndPoints = new String[]{"/api/user/login",
            "/api/user/register",
            "/api/user/forgot",
            "/api/user/reset",
            "/api/user/verify"};

    private final JwtTokenProvider jwtTokenProvider;

    private final ObjectMapper objectMapper;

    public JwtTokenFilter(JwtTokenProvider jwtTokenProvider, ObjectMapper objectMapper) {
        this.jwtTokenProvider = jwtTokenProvider;
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
                throw new MyException("Expired cookies", HttpStatus.UNAUTHORIZED);
            }

            if (!httpServletRequest.getRequestURI().equals("/api/user/refresh") && !httpServletRequest.getRequestURI().equals("/api/user/logout")) {
                Optional<String> accessCookie = Arrays.stream(httpServletRequest.getCookies()).filter(cookie -> cookie.getName().equals("access-token")).map(Cookie::getValue).findFirst();
                if (accessCookie.isEmpty()) {
                    throw new MyException("Expired access cookie", HttpStatus.UNAUTHORIZED);
                }
                try {
                    jwtTokenProvider.validateToken(accessCookie.get());
                } catch (ExpiredJwtException e) {
                    throw new MyException("Expired access token", HttpStatus.UNAUTHORIZED);
                } catch (JwtException | IllegalArgumentException e) {
                    SecurityContextHolder.clearContext();
                    throw new MyException("Invalid access token", HttpStatus.UNAUTHORIZED);
                }
                if (!jwtTokenProvider.getUsernameFromToken(accessCookie.get()).equals(authentication.getName()) || !jwtTokenProvider.getTypeFromToken(accessCookie.get()).equals("access-token")) {
                    SecurityContextHolder.clearContext();
                    throw new MyException("Invalid access token", HttpStatus.UNAUTHORIZED);
                }
            }
        } catch (MyException e) {
            httpServletResponse.setStatus(e.getHttpStatus().value());
            httpServletResponse.getWriter().write(objectMapper.writeValueAsString(e.getMessage()));
            return;
        }

        filterChain.doFilter(httpServletRequest, httpServletResponse);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return Arrays.stream(excludedEndPoints).anyMatch(e -> new AntPathMatcher().match(e, request.getRequestURI()));
    }

}
