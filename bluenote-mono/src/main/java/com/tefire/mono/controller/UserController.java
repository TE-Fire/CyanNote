package com.tefire.mono.controller;

import com.tefire.mono.common.AuthInterceptor;
import com.tefire.mono.common.Json;
import com.tefire.mono.common.R;
import com.tefire.mono.store.DataStore;
import com.tefire.mono.store.FileStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/** 用户资料：未传 userId 时按登录态取当前用户，供登录后刷新自身资料使用。 */
@RestController
@RequestMapping("/user/user")
public class UserController {

    private final DataStore store;
    private final FileStore files;

    public UserController(DataStore store, FileStore files) {
        this.store = store;
        this.files = files;
    }

    @PostMapping("/profile")
    public Map<String, Object> profile(@RequestBody(required = false) Map<String, Object> body,
                                       HttpServletRequest request) {
        Long userId = body == null ? null : Json.asLong(body.get("userId"));
        if (userId == null) {
            userId = AuthInterceptor.uid(request);
        }
        if (userId == null) {
            return R.fail("未指定用户");
        }
        Map<String, Object> view = store.userView(userId);
        return view == null ? R.fail("用户不存在") : R.ok(view);
    }

    @PostMapping(value = "/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> update(@RequestParam Map<String, String> form,
                                      @RequestPart(value = "avatar", required = false) MultipartFile avatar,
                                      HttpServletRequest request) throws IOException {
        Long uid = AuthInterceptor.uid(request);
        if (uid == null) {
            return R.fail("请先登录");
        }
        Map<String, Object> user = store.users.get(uid);
        if (user == null) {
            return R.fail("用户不存在");
        }
        putIfPresent(form, user, "nickname");
        putIfPresent(form, user, "xiaohashuId");
        putIfPresent(form, user, "introduction");
        putIfPresent(form, user, "birthday");
        String sex = form.get("sex");
        if (sex != null && !sex.isBlank()) {
            try {
                user.put("sex", Integer.parseInt(sex.trim()));
            } catch (NumberFormatException ignored) {
                // 非法性别值忽略，不阻断其他字段更新
            }
        }
        if (avatar != null && !avatar.isEmpty()) {
            user.put("avatar", files.save(avatar));
        }
        return R.ok(store.userView(uid));
    }

    private void putIfPresent(Map<String, String> form, Map<String, Object> user, String key) {
        String value = form.get(key);
        if (value != null && !value.isBlank()) {
            user.put(key, value.trim());
        }
    }
}
