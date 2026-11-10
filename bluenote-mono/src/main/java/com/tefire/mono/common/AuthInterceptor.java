package com.tefire.mono.common;

import com.tefire.mono.store.DataStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Set;

/**
 * Token 鉴权。只拦写操作，读接口允许匿名浏览——与前端未登录也能看发现页一致。
 * 未登录必须返回 HTTP 401，前端 axios 拦截器是按状态码判断登录态的。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Set<String> PROTECTED = Set.of(
            "/auth/logout",
            "/user/user/update",
            "/note/note/publish",
            "/note/note/like",
            "/note/note/unlike",
            "/note/note/collect",
            "/note/note/uncollect",
            "/relation/relation/follow",
            "/comment/comment/publish",
            "/comment/comment/like",
            "/comment/comment/unlike"
    );

    private final DataStore store;

    public AuthInterceptor(DataStore store) {
        this.store = store;
    }

    public static Long uid(HttpServletRequest request) {
        Object v = request.getAttribute("uid");
        return v instanceof Long l ? l : null;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        Long uid = resolve(request);
        if (uid != null) {
            request.setAttribute("uid", uid);
        }
        if (uid == null && PROTECTED.contains(request.getRequestURI())) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"请先登录\",\"errorCode\":\"401\",\"data\":null}");
            return false;
        }
        return true;
    }

    private Long resolve(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || header.isBlank()) {
            return null;
        }
        String token = header.startsWith("Bearer ") ? header.substring(7) : header;
        return store.tokens.get(token.trim());
    }
}
