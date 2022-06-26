package com.deac.user.service;

import com.deac.user.persistence.entity.User;

import javax.servlet.http.HttpServletRequest;

public interface UserService {

    String signIn(String username, String password);

    String signUp(User user);

    String whoAmI(String token);

    boolean validateToken(String token);

    String refresh();

    String signOut();

}
