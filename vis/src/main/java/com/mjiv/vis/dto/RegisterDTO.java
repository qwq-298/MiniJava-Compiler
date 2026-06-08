package com.mjiv.vis.dto;

public class RegisterDTO {

    private String username;

    private String password;

    public RegisterDTO() {

    }

    public String getUsername() {
        return username;
    }

    public void setUsername(
            String username
    ) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(
            String password
    ) {
        this.password = password;
    }
}
