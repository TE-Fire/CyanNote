package com.tefire.mono.controller;

import com.tefire.mono.common.AuthInterceptor;
import com.tefire.mono.common.Json;
import com.tefire.mono.common.R;
import com.tefire.mono.store.DataStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 演示用登录：短信不真发，验证码由服务端随机生成后随响应返回，前端直接展示并填入。
 * 登录即注册，返回的 data 就是 token 字符串——前端直接塞进 store。
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final DataStore store;

    public AuthController(DataStore store) {
        this.store = store;
    }

    @PostMapping("/verification/code/send")
    public Map<String, Object> sendCode(@RequestBody(required = false) Map<String, Object> body) {
        String phone = body == null ? null : Json.asString(body.get("phone"));
        if (phone == null || phone.isBlank()) {
            return R.fail("手机号不能为空");
        }
        return R.ok(store.issueCode(phone.trim()));
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody(required = false) Map<String, Object> body) {
        if (body == null) {
            return R.fail("请求参数缺失");
        }
        String phone = Json.asString(body.get("phone"));
        String code = Json.asString(body.get("code"));
        if (phone == null || phone.isBlank()) {
            return R.fail("手机号不能为空");
        }
        if (code == null || code.isBlank()) {
            return R.fail("验证码不能为空");
        }
        if (!store.verifyCode(phone.trim(), code.trim())) {
            return R.fail("验证码错误");
        }
        Map<String, Object> user = store.loginByPhone(phone.trim());
        Long userId = Json.asLong(user.get("userId"));
        return R.ok(store.issueToken(userId));
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null) {
            store.tokens.remove(header.startsWith("Bearer ") ? header.substring(7).trim() : header.trim());
        }
        return R.ok();
    }
}
