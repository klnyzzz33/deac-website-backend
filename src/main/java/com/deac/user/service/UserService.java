package com.deac.user.service;

import com.deac.user.persistence.entity.User;

import javax.mail.MessagingException;

public interface UserService {

    String signIn(String username, String password);

    String signUp(User user);

    String whoAmI(String token);

    String refresh();

    String signOut();

    boolean hasAdminPrivileges();

    String getCurrentUsername();

    String recoverPassword(String email) throws MessagingException;

    String resetPassword(String token, String password);

}
