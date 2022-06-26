package com.deac.user.controller;

import com.deac.user.exception.MyException;
import com.deac.user.model.LoginDto;
import com.deac.user.model.RegisterDto;
import com.deac.user.model.ResponseMessage;
import com.deac.user.persistence.entity.User;
import com.deac.user.service.UserService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/api/login")
    public ResponseMessage login(@Valid @RequestBody LoginDto loginDto, HttpServletResponse response) {
        String token = userService.signIn(loginDto.getUsername(), loginDto.getPassword());
        Cookie cookie = new Cookie("jwt", token);
        setCookie(cookie, 300);
        response.addCookie(cookie);
        return new ResponseMessage(token);
    }

    @PostMapping("/api/register")
    public ResponseMessage register(@Valid @RequestBody RegisterDto registerDto) {
        return new ResponseMessage(userService.signUp(modelMapper.map(registerDto, User.class)));
    }

    @GetMapping("/api/current_user")
    public ResponseMessage whoAmI(HttpServletRequest request) {
        if (request.getCookies() == null) {
            throw new MyException("Expired cookie", HttpStatus.UNAUTHORIZED);
        }
        Optional<String> jwt = Arrays.stream(request.getCookies()).filter(cookie -> cookie.getName().equals("jwt")).map(Cookie::getValue).findFirst();
        if (jwt.isEmpty()) {
            throw new MyException("Expired cookie", HttpStatus.UNAUTHORIZED);
        }
        return new ResponseMessage(userService.whoAmI(jwt.get()));
    }

    @GetMapping("/api/refresh")
    public ResponseMessage refresh(HttpServletResponse response) {
        String token = userService.refresh();
        Cookie cookie = new Cookie("jwt", token);
        setCookie(cookie, 300);
        response.addCookie(cookie);
        return new ResponseMessage(token);
    }

    @GetMapping("/api/logout")
    public ResponseMessage logout(HttpServletResponse response) {
        Cookie cookie = new Cookie("jwt", null);
        setCookie(cookie, 0);
        response.addCookie(cookie);
        return new ResponseMessage(userService.signOut());
    }

    private void setCookie(Cookie cookie, int age) {
        cookie.setMaxAge(age);
        cookie.setSecure(false);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
    }

    @GetMapping("/api/dashboard")
    public String getDashboard() {
        return null;
    }

    private boolean isValidToken(HttpServletRequest request) {
        Optional<String> jwt = Arrays.stream(request.getCookies()).filter(cookie -> cookie.getName().equals("jwt")).map(Cookie::getValue).findFirst();
        if (jwt.isEmpty()) {
            throw new MyException("Expired cookie", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return userService.validateToken(jwt.get());
    }

}
