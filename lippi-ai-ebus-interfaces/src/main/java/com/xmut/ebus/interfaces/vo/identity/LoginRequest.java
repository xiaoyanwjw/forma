package com.xmut.ebus.interfaces.vo.identity;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 登录 HTTP 入参：账号可为用户名或邮箱。
 */
public class LoginRequest {

    @NotBlank(message = "账号不能为空")
    @Size(max = 255, message = "账号过长")
    private String account;

    @NotBlank(message = "密码不能为空")
    @Size(max = 72, message = "密码过长")
    private String password;

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
