package com.xmut.forma.interfaces.vo.identity;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 账户改密 PUT 入参：当前密码 + 新密码（无确认栏）。
 */
public class ChangeAccountPasswordRequest {

    @NotBlank(message = "当前密码不能为空")
    @Size(max = 72, message = "密码过长")
    private String oldPassword;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 72, message = "密码长度需在 6～72 之间")
    private String newPassword;

    public String getOldPassword() {
        return oldPassword;
    }

    public void setOldPassword(String oldPassword) {
        this.oldPassword = oldPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
