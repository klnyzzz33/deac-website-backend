package com.deac.user.controller;

import com.deac.user.model.LoginDto;
import com.deac.user.model.RegisterDto;
import com.deac.user.model.ResetDto;
import com.deac.response.ResponseMessage;
import com.deac.user.persistence.entity.User;
import com.deac.user.service.UserService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.mail.MessagingException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

@RestController
public class UserController {

    private final UserService userService;

    private final ModelMapper modelMapper;

    @Autowired
    public UserController(UserService userService, ModelMapper modelMapper) {
        this.userService = userService;
        this.modelMapper = modelMapper;
    }

    @PostMapping("/api/user/login")
    public ResponseMessage login(@Valid @RequestBody LoginDto loginDto, HttpServletResponse response) {
        String token = userService.signIn(loginDto.getUsername(), loginDto.getPassword());
        Cookie cookie = new Cookie("jwt", token);
        setCookie(cookie, 900);
        response.addCookie(cookie);
        return new ResponseMessage(token);
    }

    @PostMapping("/api/user/register")
    public ResponseMessage register(@Valid @RequestBody RegisterDto registerDto) {
        return new ResponseMessage(userService.signUp(modelMapper.map(registerDto, User.class)));
    }

    @PostMapping("/api/user/verify")
    public ResponseMessage verifyEmail(@RequestBody String token) {
        return new ResponseMessage(userService.verifyEmail(token));
    }

    @GetMapping("/api/user/current_user")
    public ResponseMessage getCurrentUser() {
        return new ResponseMessage(userService.getCurrentUsername());
    }

    @GetMapping("/api/user/refresh")
    public ResponseMessage refresh(HttpServletResponse response) {
        String token = userService.refresh();
        Cookie cookie = new Cookie("jwt", token);
        setCookie(cookie, 900);
        response.addCookie(cookie);
        return new ResponseMessage(token);
    }

    @GetMapping("/api/user/logout")
    public ResponseMessage logout(HttpServletResponse response) {
        String responseString = userService.signOut();
        Cookie cookie = new Cookie("jwt", null);
        setCookie(cookie, 0);
        response.addCookie(cookie);
        return new ResponseMessage(responseString);
    }

    private void setCookie(Cookie cookie, int age) {
        cookie.setMaxAge(age);
        cookie.setSecure(false);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
    }

    @PostMapping("/api/user/forgot")
    public ResponseMessage sendPasswordRecoveryLink(@RequestBody String email) throws MessagingException {
        return new ResponseMessage(userService.recoverPassword(email));
    }

    @PostMapping("/api/user/reset")
    public ResponseMessage changePassword(@Valid @RequestBody ResetDto resetDto) {
        return new ResponseMessage(userService.resetPassword(resetDto.getToken(), resetDto.getPassword()));
    }

}
