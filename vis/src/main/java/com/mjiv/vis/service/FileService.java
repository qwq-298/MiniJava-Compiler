package com.mjiv.vis.service;

import com.mjiv.vis.entity.FileInfo;
import com.mjiv.vis.repository.FileRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;

import java.nio.file.Files;
import java.nio.file.Paths;

import java.time.LocalDateTime;

import java.util.List;

@Service
public class FileService {

    @Autowired
    private FileRepository fileRepository;

    // 根目录
    private final String ROOT = "storage/";

    /*
        保存文件
     */
    public FileInfo saveFile(
            Long userId,
            String filename,
            String content
    ) throws Exception {

        // 用户目录
        String userDir =
                ROOT + userId + "/";

        File dir =
                new File(userDir);

        // 不存在则创建
        if(!dir.exists()) {

            dir.mkdirs();

        }

        // 最终文件路径
        String path =
                userDir + filename;

        // 写入文件
        Files.write(

                Paths.get(path),

                content.getBytes()

        );

        // 保存数据库记录
        FileInfo file =
                new FileInfo();

        file.setUserId(userId);

        file.setFilename(filename);

        file.setFilepath(path);

        file.setCreateTime(
                LocalDateTime.now()
        );

        file.setUpdateTime(
                LocalDateTime.now()
        );

        return fileRepository.save(file);
    }

    /*
        获取用户文件列表
     */
    public List<FileInfo> getFiles(
            Long userId
    ) {

        return fileRepository
                .findByUserId(userId);

    }

    /*
        读取文件
     */
    public String readFile(
            Long fileId
    ) throws Exception {

        FileInfo file =
                fileRepository.findById(fileId)
                        .orElseThrow();

        return Files.readString(

                Paths.get(
                        file.getFilepath()
                )

        );
    }

    /*
        删除文件
     */
    public void deleteFile(
            Long fileId
    ) throws Exception {

        FileInfo file =
                fileRepository.findById(fileId)
                        .orElseThrow();

        // 删除磁盘文件
        Files.deleteIfExists(

                Paths.get(
                        file.getFilepath()
                )

        );

        // 删除数据库记录
        fileRepository.deleteById(fileId);
    }

}
