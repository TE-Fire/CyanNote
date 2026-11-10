package com.tefire.mono.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 演示数据的小工具：构造有序 Map、内存分页。用 Map 承载返回体是为了少写 DTO，
 * 字段随前端需要随时增补；真实微服务仍用强类型 VO。
 */
public final class Json {

    private Json() {
    }

    /** 按 key/value 顺序构造 LinkedHashMap，允许 value 为 null。 */
    public static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    /** 内存分页，pageNo 从 1 开始，越界返回空列表。 */
    public static <T> List<T> slice(List<T> src, int pageNo, int pageSize) {
        if (src == null || src.isEmpty() || pageSize <= 0) {
            return List.of();
        }
        int from = Math.max(0, (pageNo - 1) * pageSize);
        if (from >= src.size()) {
            return List.of();
        }
        int to = Math.min(src.size(), from + pageSize);
        return new ArrayList<>(src.subList(from, to));
    }

    public static int pageNo(Object raw) {
        return raw instanceof Number n ? Math.max(1, n.intValue()) : 1;
    }

    public static Long asLong(Object raw) {
        if (raw instanceof Number n) {
            return n.longValue();
        }
        if (raw instanceof String s && !s.isBlank()) {
            try {
                return Long.valueOf(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public static String asString(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }
}
