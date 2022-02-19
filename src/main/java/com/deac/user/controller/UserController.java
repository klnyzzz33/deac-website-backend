package com.deac.user.controller;

import com.deac.user.model.LoginDto;
import com.deac.user.model.RegisterDto;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
public class UserController {

    @PostMapping("/api/login")
    public String login(@Valid @RequestBody LoginDto loginDto) {
        return "Login";
    }

    @PostMapping("/api/register")
    public String register(@Valid @RequestBody RegisterDto registerDto) {
        return "Register";
    }

}
