package com.deac.security.jwt.refreshtoken;

import com.deac.exception.MyException;
import com.deac.user.persistence.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
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
import java.util.Optional;

public class RefreshTokenFilter extends OncePerRequestFilter {

    private static final String filteredEndPoint = "/api/user/auth/refresh";

    private final UserRepository userRepository;

    private final RefreshTokenProvider refreshTokenProvider;

    private final ObjectMapper objectMapper;

    private final AntPathMatcher antPathMatcher;

    public RefreshTokenFilter(UserRepository userRepository, RefreshTokenProvider refreshTokenProvider, ObjectMapper objectMapper, AntPathMatcher antPathMatcher) {
        this.userRepository = userRepository;
        this.refreshTokenProvider = refreshTokenProvider;
        this.objectMapper = objectMapper;
        this.antPathMatcher = antPathMatcher;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, FilterChain filterChain) throws ServletException, IOException {
        try {
            if (httpServletRequest.getCookies() == null) {
                throw new MyException("Expired refresh cookie", HttpStatus.UNAUTHORIZED);
            }
            Optional<String> refreshCookie = Arrays.stream(httpServletRequest.getCookies()).filter(cookie -> cookie.getName().equals("refresh-token")).map(Cookie::getValue).findFirst();
            if (refreshCookie.isEmpty()) {
                throw new MyException("Expired refresh cookie", HttpStatus.UNAUTHORIZED);
            }
            try {
                String refreshToken = refreshCookie.get();
                String username = refreshTokenProvider.validateToken(refreshToken);
                if (!userRepository.existsByUsername(username)) {
                    refreshTokenProvider.invalidateUserTokens(username);
                    throw new MyException("Invalid refresh token", HttpStatus.UNAUTHORIZED);
                }
                refreshTokenProvider.authorize(refreshToken);
                filterChain.doFilter(httpServletRequest, httpServletResponse);
            } catch (ExpiredJwtException e) {
                throw new MyException("Expired refresh token", HttpStatus.UNAUTHORIZED);
            } catch (JwtException | IllegalArgumentException e) {
                throw new MyException("Invalid refresh token", HttpStatus.UNAUTHORIZED);
            } catch (DataAccessException e) {
                throw new MyException("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } catch (MyException e) {
            SecurityContextHolder.clearContext();
            refreshTokenProvider.removeCookies(httpServletResponse);
            httpServletResponse.setStatus(e.getHttpStatus().value());
            httpServletResponse.getWriter().write(objectMapper.writeValueAsString(e.getMessage()));
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest httpServletRequest) {
        return !antPathMatcher.match(filteredEndPoint, httpServletRequest.getRequestURI());
    }

}
