package com.deac.user.service;

import com.deac.user.persistence.entity.User;

import javax.mail.MessagingException;
import java.util.List;

public interface UserService {

    List<String> signIn(String username, String password);

    String signUp(User user);

    String refresh(String refreshToken);

    String signOut();

    boolean hasAdminPrivileges();

    String getCurrentUsername();

    Integer getCurrentUserId(String username);

    String getUser(Integer userId);

    String recoverPassword(String email) throws MessagingException;

    String resetPassword(String token, String password);

    String verifyEmail(String token);

}
