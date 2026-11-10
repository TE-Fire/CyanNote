package com.tefire.mono.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** 兜底异常，保证前端始终拿到 JSON 而非 HTML 错误页。 */
@RestControllerAdvice
public class GlobalErrors {

    private static final Logger log = LoggerFactory.getLogger(GlobalErrors.class);

    @ExceptionHandler(Exception.class)
    public Map<String, Object> onException(Exception e) {
        log.warn("请求处理失败: {}", e.toString());
        return R.fail("服务异常：" + e.getClass().getSimpleName());
    }
}
