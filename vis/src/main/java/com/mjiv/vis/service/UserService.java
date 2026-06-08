package com.mjiv.vis.service;

import com.mjiv.vis.repository.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.mjiv.vis.entity.User;

@Service
public class UserService {
    @Autowired
    private UserRepository userrepository;

    public User findbyUserName(String username){
        return userrepository.findByUsername(username);
    }
}
