package com.mjiv.vis.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.web.bind.annotation.*;

import com.mjiv.vis.service.UserService;
import com.mjiv.vis.entity.User;

@RestController
@RequestMapping("/api/user")
@CrossOrigin
public class UserController {
    @Autowired
    private UserService userService;

    @GetMapping("/by-username")
    public Long getUserId(@RequestParam String username) {

        User user = userService.findbyUserName(username);

        if (user == null) {
            throw new RuntimeException("User not found");
        }

        return user.getId();
    }
}
