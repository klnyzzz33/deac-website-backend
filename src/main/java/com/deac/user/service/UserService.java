package com.deac.user.service;

import com.deac.user.persistence.entity.User;

public interface UserService {

    String signIn(String username, String password);

    String signUp(User user);

    boolean validateToken(String token);

    String refresh(String username);

    String signOut();

}
