package com.xmut.ebus.application.identity.dto;

/**
 * 账户资料（GET|PATCH /api/v1/account/profile）。邮箱只读；username 可改。
 */
public class AccountProfileDTO {

    private String userId;
    private String username;
    private String email;

    public AccountProfileDTO() {
    }

    public AccountProfileDTO(String userId, String username, String email) {
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
