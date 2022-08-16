package com.deac.exception;

import org.springframework.http.HttpStatus;

public class MyException extends RuntimeException {

    private final String message;
    private final HttpStatus httpStatus;

    public MyException(String message, HttpStatus httpStatus) {
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

}
