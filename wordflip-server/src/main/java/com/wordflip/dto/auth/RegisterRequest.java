package com.wordflip.dto.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求：邮箱或手机号二选一（对齐 openapi RegisterRequest oneOf）。
 */
public class RegisterRequest {

    @Email
    private String email;

    @Pattern(regexp = "^\\+[1-9]\\d{1,14}$", message = "手机号须为 E.164 格式")
    private String phone;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, message = "密码至少 8 位")
    private String password;

    @AssertTrue(message = "邮箱或手机号须二选一")
    public boolean isAccountValid() {
        boolean hasEmail = email != null && !email.isBlank();
        boolean hasPhone = phone != null && !phone.isBlank();
        return hasEmail ^ hasPhone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
