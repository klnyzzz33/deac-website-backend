package com.deac.user.controller;

import com.deac.exception.MyException;
import com.deac.user.dto.LoginDto;
import com.deac.user.dto.RegisterDto;
import com.deac.user.dto.ResetDto;
import com.deac.response.ResponseMessage;
import com.deac.user.persistence.entity.User;
import com.deac.user.service.Language;
import com.deac.user.service.UserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.util.Arrays;
import java.util.Map;

@RestController
public class UserController {

    private final UserService userService;

    private final ModelMapper modelMapper;

    private final ObjectMapper objectMapper;

    @Value("${jwt.access.validity}")
    private long accessCookieAge;

    @Value("${jwt.refresh.absolute.validity}")
    private long refreshCookieAge;

    @Autowired
    public UserController(UserService userService, ModelMapper modelMapper, ObjectMapper objectMapper) {
        this.userService = userService;
        this.modelMapper = modelMapper;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/api/user/auth/login")
    public ResponseMessage login(@Valid @RequestBody LoginDto loginDto, HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = request.getCookies() != null ? Arrays.stream(request.getCookies())
                .filter(cookie -> cookie.getName().equals("refresh-token"))
                .map(Cookie::getValue)
                .findFirst()
                .orElse("") : "";
        Map<String, String> values = userService.signIn(loginDto.getUsername(), loginDto.getPassword(), refreshToken);
        ResponseCookie accessCookie = userService.setCookie("access-token", values.get("accessToken"), accessCookieAge, true, "/");
        ResponseCookie refreshCookie = userService.setCookie("refresh-token", values.get("refreshToken"), refreshCookieAge, true, "/api/user/auth");
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        return new ResponseMessage(values.get("authorities"));
    }

    @PostMapping("/api/user/register")
    public ResponseMessage register(@Valid @RequestBody RegisterDto registerDto) {
        return new ResponseMessage(userService.signUp(modelMapper.map(registerDto, User.class)));
    }

    @PostMapping("/api/user/verify")
    public ResponseMessage verifyEmail(@RequestBody String token) {
        return new ResponseMessage(userService.verifyEmail(token));
    }

    @GetMapping("/api/user/current_user_name")
    public ResponseMessage getCurrentUserName() {
        return new ResponseMessage(userService.getCurrentUsername());
    }

    @GetMapping("/api/user/current_user_authorities")
    public ResponseMessage getCurrentUserAuthorities() {
        try {
            return new ResponseMessage(objectMapper.writeValueAsString(userService.getCurrentUserAuthorities()));
        } catch (JsonProcessingException e) {
            throw new MyException("Could not get authorities", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/api/user/auth/refresh")
    public ResponseMessage refresh(HttpServletRequest request, HttpServletResponse response) {
        try {
            String refreshToken = Arrays.stream(request.getCookies())
                    .filter(cookie -> cookie.getName().equals("refresh-token"))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElseThrow(() -> new MyException("Expired refresh cookie", HttpStatus.UNAUTHORIZED));
            Map<String, String> values = userService.refresh(refreshToken);
            ResponseCookie newAccessCookie = userService.setCookie("access-token", values.get("accessToken"), accessCookieAge, true, "/");
            ResponseCookie newRefreshCookie = userService.setCookie("refresh-token", values.get("refreshToken"), refreshCookieAge, true, "/api/user/auth");
            response.addHeader(HttpHeaders.SET_COOKIE, newAccessCookie.toString());
            response.addHeader(HttpHeaders.SET_COOKIE, newRefreshCookie.toString());
            return new ResponseMessage(values.get("authorities"));
        } catch (MyException e) {
            userService.removeCookies(response);
            throw e;
        }
    }

    @GetMapping("/api/user/auth/logout")
    public ResponseMessage logout(HttpServletRequest request, HttpServletResponse response) {
        try {
            String refreshToken = request.getCookies() != null ? Arrays.stream(request.getCookies())
                    .filter(cookie -> cookie.getName().equals("refresh-token"))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse("") : "";
            String responseString = userService.signOut(refreshToken);
            userService.removeCookies(response);
            return new ResponseMessage(responseString);
        } catch (MyException e) {
            userService.removeCookies(response);
            throw e;
        }
    }

    @PostMapping("/api/user/forgot_password")
    public ResponseMessage sendPasswordRecoveryLink(@RequestBody String email) {
        return new ResponseMessage(userService.recoverPassword(email));
    }

    @PostMapping("/api/user/reset_password")
    public ResponseMessage changePassword(@Valid @RequestBody ResetDto resetDto) {
        return new ResponseMessage(userService.resetPassword(resetDto.getToken(), resetDto.getPassword()));
    }

    @PostMapping("/api/user/forgot_username")
    public ResponseMessage sendUsernameReminderEmail(@RequestBody String email) {
        return new ResponseMessage(userService.sendUsernameReminderEmail(email));
    }

    @PostMapping("/api/user/language/set")
    public ResponseMessage setLanguage(@RequestBody String language) {
        try {
            Language converted = Language.valueOf(language.toUpperCase());
            return new ResponseMessage(userService.setLanguage(converted));
        } catch (IllegalArgumentException e) {
            throw new MyException("Unsupported language", HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/api/user/language")
    public ResponseMessage getLanguage() {
        return new ResponseMessage(userService.getCurrentUserLanguage().name());
    }

}
