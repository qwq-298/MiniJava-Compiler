package com.mjiv.vis.dto;

public class SaveFileDTO {

    private Long userId;

    private String filename;

    private String content;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(
            Long userId
    ) {
        this.userId = userId;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(
            String filename
    ) {
        this.filename = filename;
    }

    public String getContent() {
        return content;
    }

    public void setContent(
            String content
    ) {
        this.content = content;
    }
}
