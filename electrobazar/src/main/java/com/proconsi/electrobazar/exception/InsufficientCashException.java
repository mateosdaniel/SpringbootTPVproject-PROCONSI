package com.proconsi.electrobazar.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a cash register operation (like a withdrawal or return)
 * cannot be completed because there is not enough cash in the drawer.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InsufficientCashException extends RuntimeException {
    public InsufficientCashException(String message) {
        super(message);
    }
}
