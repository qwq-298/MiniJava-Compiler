package com.mjiv.vis.repository;

import com.mjiv.vis.entity.FileInfo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FileRepository
        extends JpaRepository<FileInfo, Long> {

    List<FileInfo> findByUserId(Long userId);

}
