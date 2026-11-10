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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 关注关系：关注是幂等的，重复调用不会把计数加两次。 */
@RestController
@RequestMapping("/relation/relation")
public class RelationController {

    private final DataStore store;

    public RelationController(DataStore store) {
        this.store = store;
    }

    @PostMapping("/follow")
    public Map<String, Object> follow(@RequestBody(required = false) Map<String, Object> body,
                                      HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        Long target = body == null ? null : Json.asLong(body.get("followUserId"));
        if (uid == null) {
            return R.fail("请先登录");
        }
        if (target == null || !store.users.containsKey(target)) {
            return R.fail("用户不存在");
        }
        if (uid.equals(target)) {
            return R.fail("不能关注自己");
        }
        store.follows.computeIfAbsent(uid, k -> ConcurrentHashMap.newKeySet()).add(target);
        return R.ok(true);
    }

    @PostMapping("/unfollow")
    public Map<String, Object> unfollow(@RequestBody(required = false) Map<String, Object> body,
                                        HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        Long target = body == null ? null : Json.asLong(body.get("unfollowUserId"));
        if (uid == null) {
            return R.fail("请先登录");
        }
        Set<Long> set = store.follows.get(uid);
        if (set != null && target != null) {
            set.remove(target);
        }
        return R.ok(true);
    }

    @PostMapping("/following/list")
    public Map<String, Object> following(@RequestBody(required = false) Map<String, Object> body,
                                         HttpServletRequest request) {
        Long userId = body == null ? null : Json.asLong(body.get("userId"));
        Long viewer = AuthInterceptor.uid(request);
        if (userId == null) {
            userId = viewer;
        }
        if (userId == null) {
            return R.fail("缺少 userId");
        }
        int pageNo = Json.pageNo(body == null ? null : body.get("pageNo"));
        List<Map<String, Object>> all = new ArrayList<>();
        for (Long target : store.follows.getOrDefault(userId, Set.of())) {
            Map<String, Object> view = relationView(target, viewer);
            if (view != null) {
                all.add(view);
            }
        }
        all.sort((a, b) -> Long.compare(Json.asLong(a.get("userId")), Json.asLong(b.get("userId"))));
        return R.page(Json.slice(all, pageNo, DataStore.PAGE_SIZE), pageNo, DataStore.PAGE_SIZE, all.size());
    }

    @PostMapping("/fans/list")
    public Map<String, Object> fans(@RequestBody(required = false) Map<String, Object> body,
                                    HttpServletRequest request) {
        Long userId = body == null ? null : Json.asLong(body.get("userId"));
        Long viewer = AuthInterceptor.uid(request);
        if (userId == null) {
            userId = viewer;
        }
        if (userId == null) {
            return R.fail("缺少 userId");
        }
        int pageNo = Json.pageNo(body == null ? null : body.get("pageNo"));
        List<Map<String, Object>> all = new ArrayList<>();
        long target = userId;
        store.follows.forEach((from, targets) -> {
            if (targets.contains(target)) {
                Map<String, Object> view = relationView(from, viewer);
                if (view != null) {
                    all.add(view);
                }
            }
        });
        all.sort((a, b) -> Long.compare(Json.asLong(a.get("userId")), Json.asLong(b.get("userId"))));
        return R.page(Json.slice(all, pageNo, DataStore.PAGE_SIZE), pageNo, DataStore.PAGE_SIZE, all.size());
    }

    /** 列表里的 isLiked 表示「当前登录用户是否已关注他」，前端据此切换按钮文案。 */
    private Map<String, Object> relationView(long targetId, Long viewerId) {
        Map<String, Object> view = store.userView(targetId);
        if (view == null) {
            return null;
        }
        view.put("isLiked", viewerId != null && store.isFollowing(viewerId, targetId));
        return view;
    }
}
