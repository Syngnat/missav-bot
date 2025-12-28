package com.missav.bot.common.exception;

import com.missav.bot.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("参数异常: {}", e.getMessage());
        return Result.error(400, e.getMessage());
    }

    /**
     * 处理空指针异常
     */
    @ExceptionHandler(NullPointerException.class)
    public Result<Void> handleNullPointerException(NullPointerException e) {
        log.error("空指针异常", e);
        return Result.error(500, "系统内部错误");
    }

    /**
     * 处理运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常", e);
        return Result.error(500, e.getMessage());
    }

    /**
     * 处理静态资源未找到异常（降级为DEBUG，避免日志污染）
     */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public void handleNoResourceFound(org.springframework.web.servlet.resource.NoResourceFoundException e) {
        log.debug("静态资源未找到: {}", e.getResourcePath());
    }

    /**
     * 处理文件上传解析异常（降级为DEBUG，避免日志污染）
     */
    @ExceptionHandler(org.springframework.web.multipart.MultipartException.class)
    public void handleMultipartException(org.springframework.web.multipart.MultipartException e) {
        log.debug("文件上传解析失败（通常是 Telegram 发送的非文本消息）: {}", e.getMessage());
    }

    /**
     * 处理通用异常
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error(500, "系统内部错误");
    }
}
