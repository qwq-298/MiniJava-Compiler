package com.mjiv.vis.repository;

import com.mjiv.vis.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByUsername(String username);//根据方法名自动生成SQL 
}
