package com.mjiv.vis.dto;

public class FileDTO {
    private String filename;
    private String content;
    public void setFilename(String filename){
        this.filename = filename;
    }
    public String getFilename(){
        return this.filename;
    }
    public void setContent(String content){
        this.content = content;
    }
    public String getContent(){
        return this.content;
    }
}
