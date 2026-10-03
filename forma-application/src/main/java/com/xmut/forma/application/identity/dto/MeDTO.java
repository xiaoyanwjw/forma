package com.xmut.forma.application.identity.dto;

/**
 * 当前登录用户摘要（GET /api/v1/me）。
 */
public class MeDTO {

    private String userId;
    private String username;
    private String email;

    public MeDTO() {
    }

    public MeDTO(String userId, String username, String email) {
        this.userId = userId;
        this.username = username;
        this.email = email;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
