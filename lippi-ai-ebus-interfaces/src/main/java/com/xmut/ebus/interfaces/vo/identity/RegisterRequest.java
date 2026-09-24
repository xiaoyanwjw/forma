package com.xmut.ebus.interfaces.vo.identity;

import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 注册 HTTP 入参。
 */
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 64, message = "用户名长度需在 2～64 之间")
    @Pattern(regexp = "^[^@]+$", message = "用户名不能包含 @")
    private String username;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 255, message = "邮箱过长")
    private String email;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 72, message = "密码长度需在 6～72 之间")
    private String password;

    /** 须为 true；缺省/false 拒绝。不落库。 */
    @AssertTrue(message = "须先确认已知悉：AI 生成内容须人工复核后再上架，Adam 不承诺销售效果")
    private boolean agreedToAiDisclaimer;

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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isAgreedToAiDisclaimer() {
        return agreedToAiDisclaimer;
    }

    public void setAgreedToAiDisclaimer(boolean agreedToAiDisclaimer) {
        this.agreedToAiDisclaimer = agreedToAiDisclaimer;
    }
}
