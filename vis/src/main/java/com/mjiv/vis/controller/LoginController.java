package com.mjiv.vis.controller;

import com.mjiv.vis.dto.LoginDTO;
import com.mjiv.vis.dto.RegisterDTO;
import com.mjiv.vis.entity.User;
import com.mjiv.vis.repository.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/login")
@CrossOrigin
public class LoginController {

    @Autowired
    private UserRepository userRepository;

    // 注册
    @PostMapping("/register")
    public Map<String, Object> register(
            @RequestBody RegisterDTO dto
    ) {

        Map<String, Object> result =
                new HashMap<>();

        // 判断用户名是否已存在
        User existUser =
                userRepository.findByUsername(
                        dto.getUsername()
                );

        if (existUser != null) {

            result.put("success", false);

            result.put(
                    "message",
                    "Username already exists"
            );

            return result;
        }

        // 创建用户
        User user = new User();

        user.setUsername(dto.getUsername());

        user.setPassword(dto.getPassword());

        userRepository.save(user);

        result.put("success", true);

        result.put(
                "message",
                "Register success"
        );

        return result;
    }

    // 登录
    @PostMapping("/login")
    public Map<String, Object> login(
            @RequestBody LoginDTO dto
    ) {

        Map<String, Object> result =
                new HashMap<>();

        User user =
                userRepository.findByUsername(
                        dto.getUsername()
                );

        // 用户不存在
        if (user == null) {

            result.put("success", false);

            result.put(
                    "message",
                    "User not found"
            );

            return result;
        }

        // 密码错误
        if (
                !user.getPassword()
                        .equals(dto.getPassword())
        ) {

            result.put("success", false);

            result.put(
                    "message",
                    "Wrong password"
            );

            return result;
        }

        // 登录成功
        result.put("success", true);

        result.put(
                "message",
                "Login success"
        );

        result.put(
                "username",
                user.getUsername()
        );

        result.put(
                "userid",
                user.getId()
        );

        return result;
    }
}
