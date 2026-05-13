package com.chat.common.result;

/**
 * 统一响应结果
 */
public class Result<T> {

    private Integer code;   // 状态码
    private String message; // 提示信息
    private T data;         // 响应数据

    private Result() {}

    /**
     * 无数据成功响应
     */
    public static <T> Result<T> ok() {
        return ok(null);
    }

    /**
     * 带数据成功响应
     */
    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = 200;
        r.message = "success";
        r.data = data;
        return r;
    }

    /**
     * 默认失败响应（状态码500）
     */
    public static <T> Result<T> fail(String message) {
        return fail(500, message);
    }

    /**
     * 带状态码失败响应
     */
    public static <T> Result<T> fail(Integer code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }

    public Integer getCode() { return code; }
    public void setCode(Integer code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
}
