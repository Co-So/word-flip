package com.wordflip.util;

/**
 * 登录账号类型识别：邮箱或 E.164 手机号。
 */
public final class AccountUtil {

    private AccountUtil() {
    }

    /** 是否按邮箱解析 account */
    public static boolean isEmailAccount(String account) {
        return account != null && account.contains("@");
    }

    /** 是否按 E.164 手机号解析 account */
    public static boolean isPhoneAccount(String account) {
        return account != null && account.startsWith("+");
    }
}
