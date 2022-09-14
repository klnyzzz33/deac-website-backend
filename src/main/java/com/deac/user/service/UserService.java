package com.deac.user.service;

import com.deac.user.persistence.entity.User;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.GrantedAuthority;

import javax.mail.MessagingException;
import javax.servlet.http.HttpServletResponse;
import java.util.Collection;
import java.util.Map;

public interface UserService {

    Map<String, String> signIn(String username, String password, String refreshToken);

    String signUp(User user);

    Map<String, String> refresh(String refreshToken);

    String signOut();

    String getCurrentUsername();

    Collection<? extends GrantedAuthority> getCurrentAuthorities();

    Integer getCurrentUserId();

    String getUser(Integer userId);

    void setEnabled(String username, boolean isEnabled);

    String recoverPassword(String email) throws MessagingException;

    String resetPassword(String token, String password);

    String verifyEmail(String token);

    void removeCookies(HttpServletResponse response);

    ResponseCookie setCookie(String name, String value, long age, boolean httpOnly, String path);

}
