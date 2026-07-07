package com.wordflip.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求：account 自动识别邮箱或手机号。
 */
public class LoginRequest {

    @NotBlank
    private String account;

    @NotBlank
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
