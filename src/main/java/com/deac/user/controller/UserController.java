package com.deac.user.controller;

import com.deac.user.exception.MyException;
import com.deac.user.model.LoginDto;
import com.deac.user.model.RegisterDto;
import com.deac.user.model.ResetDto;
import com.deac.user.model.ResponseMessage;
import com.deac.user.persistence.entity.User;
import com.deac.user.service.UserService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import javax.mail.MessagingException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.util.Arrays;
import java.util.Optional;

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
        setCookie(cookie, 600);
        response.addCookie(cookie);
        return new ResponseMessage(token);
    }

    @PostMapping("/api/user/register")
    public ResponseMessage register(@Valid @RequestBody RegisterDto registerDto) {
        return new ResponseMessage(userService.signUp(modelMapper.map(registerDto, User.class)));
    }

    @GetMapping("/api/user/current_user")
    public ResponseMessage getCurrentUser() {
        return new ResponseMessage(userService.getCurrentUsername());
    }

    @GetMapping("/api/user/refresh")
    public ResponseMessage refresh(HttpServletResponse response) {
        String token = userService.refresh();
        Cookie cookie = new Cookie("jwt", token);
        setCookie(cookie, 600);
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

    /*private boolean isValidToken(HttpServletRequest request) {
        Optional<String> jwt = Arrays.stream(request.getCookies()).filter(cookie -> cookie.getName().equals("jwt")).map(Cookie::getValue).findFirst();
        if (jwt.isEmpty()) {
            throw new MyException("Expired cookie", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return userService.validateToken(jwt.get());
    }*/

    @PostMapping("/api/user/forgot")
    public ResponseMessage sendPasswordRecoveryLink(@RequestBody String email) throws MessagingException {
        return new ResponseMessage(userService.recoverPassword(email));
    }

    @PostMapping("/api/user/reset")
    public ResponseMessage changePassword(@RequestBody ResetDto resetDto) {
        return new ResponseMessage(userService.resetPassword(resetDto.getToken(), resetDto.getPassword()));
    }

}
