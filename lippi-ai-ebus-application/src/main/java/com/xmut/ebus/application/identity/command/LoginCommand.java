package com.xmut.ebus.application.identity.command;

/**
 * 登录写命令：账号（用户名或邮箱）+ 密码。
 */
public class LoginCommand {

    private String account;
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
