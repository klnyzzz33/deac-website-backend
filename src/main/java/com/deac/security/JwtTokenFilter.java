package com.deac.security;

import com.deac.exception.MyException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class JwtTokenFilter extends OncePerRequestFilter {

    private static final String[] excludedEndPoints = new String[]{"/api/user/login",
            "/api/user/register",
            "/api/user/forgot",
            "/api/user/reset",
            "/api/user/verify",
            "/api/user/refresh"};

    private final JwtTokenProvider jwtTokenProvider;

    private final ObjectMapper objectMapper;

    public JwtTokenFilter(JwtTokenProvider jwtTokenProvider, ObjectMapper objectMapper) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, FilterChain filterChain) throws IOException, ServletException {
        try {
            if (httpServletRequest.getCookies() == null) {
                throw new MyException("Expired access cookie", HttpStatus.UNAUTHORIZED);
            }
            Optional<String> accessCookie = Arrays.stream(httpServletRequest.getCookies()).filter(cookie -> cookie.getName().equals("access-token")).map(Cookie::getValue).findFirst();
            if (accessCookie.isEmpty()) {
                throw new MyException("Expired access cookie", HttpStatus.UNAUTHORIZED);
            }
            try {
                String accessToken = accessCookie.get();
                jwtTokenProvider.validateToken(accessToken);
                String username = jwtTokenProvider.getUsernameFromToken(accessToken);
                List<SimpleGrantedAuthority> roles = jwtTokenProvider.getRolesFromToken(accessToken)
                        .stream().map(role -> new SimpleGrantedAuthority(role.toString())).collect(Collectors.toList());
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(username, null, roles);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (ExpiredJwtException e) {
                SecurityContextHolder.clearContext();
                throw new MyException("Expired access token", HttpStatus.UNAUTHORIZED);
            } catch (JwtException | IllegalArgumentException e) {
                SecurityContextHolder.clearContext();
                throw new MyException("Invalid access token", HttpStatus.UNAUTHORIZED);
            }
            if (!jwtTokenProvider.getTypeFromToken(accessCookie.get()).equals("access-token")) {
                SecurityContextHolder.clearContext();
                throw new MyException("Invalid access token", HttpStatus.UNAUTHORIZED);
            }
            filterChain.doFilter(httpServletRequest, httpServletResponse);
        } catch (MyException e) {
            httpServletResponse.setStatus(e.getHttpStatus().value());
            httpServletResponse.getWriter().write(objectMapper.writeValueAsString(e.getMessage()));
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return Arrays.stream(excludedEndPoints).anyMatch(e -> new AntPathMatcher().match(e, request.getRequestURI()));
    }

}
