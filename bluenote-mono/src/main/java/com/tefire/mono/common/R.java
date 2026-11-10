package com.tefire.mono.common;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一响应，字段与 bluenote-common 的 Response / PageResponse 保持一致：
 * { success, message, errorCode, data }，分页响应在同一层追加分页字段。
 */
public final class R {

    private R() {
    }

    public static Map<String, Object> ok() {
        return ok(null);
    }

    public static Map<String, Object> ok(Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", "");
        body.put("errorCode", null);
        body.put("data", data);
        return body;
    }

    public static Map<String, Object> fail(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message);
        body.put("errorCode", "400");
        body.put("data", null);
        return body;
    }

    public static Map<String, Object> page(List<?> list, int pageNo, int pageSize, long totalCount) {
        Map<String, Object> body = ok(list);
        body.put("pageNo", pageNo);
        body.put("pageSize", pageSize);
        body.put("totalCount", totalCount);
        body.put("totalPage", pageSize <= 0 ? 0 : (totalCount + pageSize - 1) / pageSize);
        return body;
    }
}
