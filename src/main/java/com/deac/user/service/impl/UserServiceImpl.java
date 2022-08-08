package com.deac.user.service.impl;

import com.deac.mail.EmailService;
import com.deac.user.exception.MyException;
import com.deac.user.persistence.entity.User;
import com.deac.user.persistence.repository.UserRepository;
import com.deac.user.security.TokenProvider;
import com.deac.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final AuthenticationManager authenticationManager;

    private final TokenProvider tokenProvider;

    private final EmailService emailService;

    @Autowired
    public UserServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           AuthenticationManager authenticationManager,
                           TokenProvider tokenProvider,
                           EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.emailService = emailService;
    }

    @Override
    public String signIn(String username, String password) {
        try {
            Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            emailService.sendMessage("kalny.zalan7@gmail.com", "test", "test");
            return tokenProvider.createToken(username, userRepository.findByUsername(username).getRoles());
        } catch (AuthenticationException e) {
            throw new MyException("Could not log in, invalid credentials", HttpStatus.UNAUTHORIZED);
        }
    }

    @Override
    public String signUp(User user) {
        if (userRepository.existsByUsername(user.getUsername()) || userRepository.existsByEmail(user.getEmail())) {
            throw new MyException("Could not register, username or email already exists", HttpStatus.CONFLICT);
        }

        try {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
            userRepository.save(user);
            return "Successfully registered with user " + user.getUsername();
        } catch (Exception e) {
            throw new MyException("Could not register, invalid data specified", HttpStatus.BAD_REQUEST);
        }
    }

    @Override
    public String whoAmI(String token) {
        if (!validateToken(token)) {
            throw new MyException("You are not logged in", HttpStatus.UNAUTHORIZED);
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof AnonymousAuthenticationToken)) {
            return authentication.getName();
        }
        throw new MyException("You are not logged in", HttpStatus.UNAUTHORIZED);
    }

    @Override
    public String refresh() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        return tokenProvider.createToken(username, userRepository.findByUsername(username).getRoles());
    }

    @Override
    public String signOut() {
        SecurityContextHolder.clearContext();
        return "Successfully logged out";
    }

    @Override
    public boolean validateToken(String token) {
        return tokenProvider.validateToken(token);
    }

}
