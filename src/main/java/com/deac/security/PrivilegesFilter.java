package com.deac.security;

import com.deac.exception.MyException;
import com.deac.user.persistence.entity.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class PrivilegesFilter extends OncePerRequestFilter {

    private static final String filteredEndPoint = "/api/admin/**";

    private final ObjectMapper objectMapper;

    private final AntPathMatcher antPathMatcher;

    public PrivilegesFilter(ObjectMapper objectMapper, AntPathMatcher antPathMatcher) {
        this.objectMapper = objectMapper;
        this.antPathMatcher = antPathMatcher;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, FilterChain filterChain) throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (!(authentication instanceof AnonymousAuthenticationToken)) {
                if (authentication.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals(Role.ADMIN.getAuthority()))) {
                    filterChain.doFilter(httpServletRequest, httpServletResponse);
                } else {
                    throw new MyException("Insufficient permissions", HttpStatus.UNAUTHORIZED);
                }
            } else {
                throw new MyException("You are not logged in", HttpStatus.UNAUTHORIZED);
            }
        } catch (MyException e) {
            SecurityContextHolder.clearContext();
            httpServletResponse.setStatus(e.getHttpStatus().value());
            httpServletResponse.getWriter().write(objectMapper.writeValueAsString(e.getMessage()));
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest httpServletRequest) {
        return !antPathMatcher.match(filteredEndPoint, httpServletRequest.getRequestURI());
    }

}
