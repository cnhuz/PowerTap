package com.stwpower.powertap.domain;

import org.jetbrains.annotations.NotNull;

public class MyResponse {
    @NotNull
    private Integer code;
    @NotNull private String message;
    @NotNull private Object data;

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}