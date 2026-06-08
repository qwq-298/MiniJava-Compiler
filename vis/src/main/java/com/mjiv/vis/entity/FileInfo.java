package com.mjiv.vis.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "file_info")
public class FileInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 属于哪个用户
    private Long userId;

    // 文件名
    private String filename;

    // 实际文件路径
    private String filepath;

    // 创建时间
    private LocalDateTime createTime;

    // 更新时间
    private LocalDateTime updateTime;

    // =========================
    // getter setter
    // =========================

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getFilepath() {
        return filepath;
    }

    public void setFilepath(String filepath) {
        this.filepath = filepath;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(
            LocalDateTime createTime
    ) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(
            LocalDateTime updateTime
    ) {
        this.updateTime = updateTime;
    }
}