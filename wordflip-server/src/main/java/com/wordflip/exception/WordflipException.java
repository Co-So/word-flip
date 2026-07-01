package com.wordflip.exception;

public class WordflipException extends RuntimeException {

    private final String code;

    public WordflipException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
