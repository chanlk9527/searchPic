package com.searchpic.server.common.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Standard API response wrapper")
public class Result<T> {
    @Schema(description = "Business status code", example = "200")
    private int code;

    @Schema(description = "Response message", example = "Success")
    private String message;

    @Schema(description = "Response payload")
    private T data;

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("Success");
        result.setData(data);
        return result;
    }

    public static <T> Result<T> error(int code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
}
