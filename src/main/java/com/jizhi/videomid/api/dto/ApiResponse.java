package com.jizhi.videomid.api.dto;

import java.util.HashMap;
import java.util.Map;

public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;

    public ApiResponse() {
    }

    public ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "success", data);
    }

    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(-1, message, null);
    }

    public static ApiResponse<Map<String, Object>> accepted(Map<String, Object> data) {
        Map<String, Object> body = data == null ? new HashMap<>() : new HashMap<>(data);
        body.putIfAbsent("status", "accepted");
        return new ApiResponse<>(0, "accepted", body);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
