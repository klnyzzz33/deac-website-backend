package com.deac.user.controller;

import com.deac.user.model.LoginDto;
import com.deac.user.model.RegisterDto;
import com.deac.user.persistence.entity.User;
import com.deac.user.service.UserService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
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

    @PostMapping("/api/login")
    public String login(@Valid @RequestBody LoginDto loginDto) {
        return userService.signIn(loginDto.getUsername(), loginDto.getPassword());
    }

    @PostMapping("/api/register")
    @CrossOrigin(origins = "http://localhost:4200")
    public String register(@Valid @RequestBody RegisterDto registerDto) {
        return userService.signUp(modelMapper.map(registerDto, User.class));
    }

    @PostMapping("/api/current_user")
    public String whoAmI() {
        return userService.whoAmI();
    }

    @PostMapping("/api/logout")
    public String logout() {
        return userService.signOut();
    }

}
