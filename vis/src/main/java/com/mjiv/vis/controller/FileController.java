package com.mjiv.vis.controller;

import com.mjiv.vis.dto.SaveFileDTO;

import com.mjiv.vis.entity.FileInfo;

import com.mjiv.vis.service.FileService;

import com.mjiv.vis.dto.FileDTO;

import com.mjiv.vis.run.Runresult;

import com.mjiv.vis.interpreter.Interpreter;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/file")

@CrossOrigin
public class FileController {

    @Autowired
    private FileService fileService;

    /*
        保存文件
     */
    @PostMapping("/save")
    public FileInfo save(
            @RequestBody
            SaveFileDTO dto

    ) throws Exception {

        return fileService.saveFile(
                dto.getUserId(),

                dto.getFilename(),

                dto.getContent()

        );

    }

    /*
        获取文件列表
     */
    @GetMapping("/list")
    public List<FileInfo> list(

            @RequestParam
            Long userId

    ) {

        return fileService
                .getFiles(userId);

    }

    /*
        打开文件
     */
    @GetMapping("/open")
    public String open(

            @RequestParam
            Long fileId

    ) throws Exception {

        return fileService
                .readFile(fileId);

    }

    /*
        删除文件
     */
    @DeleteMapping("/delete")
    public String delete(

            @RequestParam
            Long fileId

    ) throws Exception {

        fileService.deleteFile(fileId);

        return "success";
    }

    @PostMapping("/upload")
    public FileInfo upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") Long userId) throws Exception {

        String filename = file.getOriginalFilename();

        String content = new String(
                file.getBytes());

        return fileService.saveFile(
                userId,
                filename,
                content);
    }

    @PostMapping("/run-all")
    public Runresult runAll(@RequestBody List<FileDTO> files){
        Runresult finalResult = new Runresult();
        finalResult.result = "";
        for(FileDTO file: files){
            Interpreter interpreter = new Interpreter();
            finalResult.result += "=====" + file.getFilename() +"=====\n";
            Runresult r = interpreter.run(file.getContent());
            finalResult.result +=r.result+"\n\n";
        }
        return finalResult;
    }
    
}
