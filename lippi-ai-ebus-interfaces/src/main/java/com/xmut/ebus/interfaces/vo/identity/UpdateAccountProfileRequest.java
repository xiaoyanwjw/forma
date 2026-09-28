package com.xmut.ebus.interfaces.vo.identity;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 账户资料 PATCH 入参：仅可改 username（邮箱只读）。
 */
public class UpdateAccountProfileRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 64, message = "用户名长度需在 2～64 之间")
    @Pattern(regexp = "^[^@]+$", message = "用户名不能包含 @")
    private String username;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
