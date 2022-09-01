package com.deac.user.controller;

import com.deac.exception.MyException;
import com.deac.user.model.LoginDto;
import com.deac.user.model.RegisterDto;
import com.deac.user.model.ResetDto;
import com.deac.response.ResponseMessage;
import com.deac.user.persistence.entity.User;
import com.deac.user.service.UserService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import javax.mail.MessagingException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.util.Arrays;
import java.util.List;
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
        List<String> tokens = userService.signIn(loginDto.getUsername(), loginDto.getPassword());
        ResponseCookie accessCookie = setCookie("access-token", tokens.get(0), 300, true);
        ResponseCookie refreshCookie = setCookie("refresh-token", tokens.get(1), 604800, true);
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        return new ResponseMessage("Successfully logged in");
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

    @PostMapping("/api/user/refresh")
    public ResponseMessage refresh(HttpServletRequest request, HttpServletResponse response) {
        Optional<String> refreshCookie = Arrays.stream(request.getCookies()).filter(cookie -> cookie.getName().equals("refresh-token")).map(Cookie::getValue).findFirst();
        try {
            if (refreshCookie.isEmpty()) {
                userService.signOut();
                throw new MyException("Expired refresh cookie", HttpStatus.UNAUTHORIZED);
            }
            List<String> tokens = userService.refresh(refreshCookie.get());
            ResponseCookie newAccessCookie = setCookie("access-token", tokens.get(0), 300, true);
            ResponseCookie newRefreshCookie = setCookie("refresh-token", tokens.get(1), 604800, true);
            response.addHeader(HttpHeaders.SET_COOKIE, newAccessCookie.toString());
            response.addHeader(HttpHeaders.SET_COOKIE, newRefreshCookie.toString());
            return new ResponseMessage("Successfully refreshed session");
        } catch (MyException e) {
            ResponseCookie newAccessCookie = setCookie("access-token", "", 0, true);
            ResponseCookie newRefreshCookie = setCookie("refresh-token", "", 0, true);
            response.addHeader(HttpHeaders.SET_COOKIE, newAccessCookie.toString());
            response.addHeader(HttpHeaders.SET_COOKIE, newRefreshCookie.toString());
            throw e;
        }
    }

    @GetMapping("/api/user/logout")
    public ResponseMessage logout(HttpServletResponse response) {
        String responseString = userService.signOut();
        ResponseCookie accessCookie = setCookie("access-token", "", 0, true);
        ResponseCookie refreshCookie = setCookie("refresh-token", "", 0, true);
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        return new ResponseMessage(responseString);
    }

    private ResponseCookie setCookie(String name, String value, int age, boolean httpOnly) {
        return ResponseCookie
                .from(name, value)
                .maxAge(age)
                .httpOnly(httpOnly)
                .sameSite("Strict")
                .secure(false)
                .path("/")
                .build();
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
