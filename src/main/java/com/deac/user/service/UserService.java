package com.deac.user.service;

import com.deac.user.persistence.entity.User;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.GrantedAuthority;

import javax.servlet.http.HttpServletResponse;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface UserService {

    Map<String, String> signIn(String username, String password, String refreshToken);

    String signUp(User user);

    Map<String, String> refresh(String refreshToken);

    String signOut(String refreshToken);

    String getCurrentUsername();

    Collection<? extends GrantedAuthority> getCurrentUserAuthorities();

    Integer getCurrentUserId();

    User getUserByUsername(String username);

    User getUserByUsernameOrEmail(String searchTerm);

    User getCurrentUser();

    void setEnabled(String username, boolean isEnabled);

    void banUsers(List<User> users);

    void saveUser(User user);

    String recoverPassword(String email);

    String resetPassword(String token, String password);

    String sendUsernameReminderEmail(String email);

    String verifyEmail(String token);

    void removeCookies(HttpServletResponse response);

    ResponseCookie setCookie(String name, String value, long age, boolean httpOnly, String path);

    String setLanguage(Language language);

    Language getCurrentUserLanguage();
}
