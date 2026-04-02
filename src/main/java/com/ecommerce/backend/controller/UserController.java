package com.ecommerce.backend.controller;

import com.ecommerce.backend.dto.LoginRequestDTO;
import com.ecommerce.backend.dto.UserRequestDTO;
import com.ecommerce.backend.dto.UserResponseDTO;
import com.ecommerce.backend.entity.User;
import com.ecommerce.backend.service.UserService;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import com.ecommerce.backend.dto.LoginRequestDTO;
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public UserResponseDTO register(@Valid @RequestBody UserRequestDTO request) {
        return userService.registerUser(request);
    }



    @PostMapping("/login")
    public String login(@RequestBody LoginRequestDTO request) {
        return userService.login(request);
    }
}
