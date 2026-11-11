package com.josemorales.billetera.common;

import org.springframework.http.HttpStatus;

/** Error de negocio con el código HTTP que debe ver el cliente. */
public class BusinessException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public BusinessException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }

    public static BusinessException notFound(String what) {
        return new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " no existe");
    }
    public static BusinessException badRequest(String code, String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, code, message);
    }
    public static BusinessException conflict(String code, String message) {
        return new BusinessException(HttpStatus.CONFLICT, code, message);
    }
}
