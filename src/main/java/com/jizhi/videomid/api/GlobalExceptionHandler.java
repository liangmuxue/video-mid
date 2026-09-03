package com.jizhi.videomid.api;

import com.jizhi.videomid.api.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class, BindException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> badRequest(Exception e) {
        String msg = e.getMessage();
        if (e instanceof MethodArgumentNotValidException manv && manv.getBindingResult().getFieldError() != null) {
            msg = manv.getBindingResult().getFieldError().getDefaultMessage();
        }
        return ApiResponse.fail(msg);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> serverError(Exception e) {
        log.error("Unhandled error", e);
        return ApiResponse.fail(e.getMessage());
    }
}
