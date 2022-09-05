package com.deac.user.service;

import com.deac.user.persistence.entity.User;

import javax.mail.MessagingException;
import java.util.List;
import java.util.Map;

public interface UserService {

    Map<String, String> signIn(String username, String password);

    String signUp(User user);

    Map<String, String> refresh(String refreshToken);

    String signOut();

    Integer getCurrentUserId();

    String getCurrentUsername();

    String getUser(Integer userId);

    String recoverPassword(String email) throws MessagingException;

    String resetPassword(String token, String password);

    String verifyEmail(String token);

}
