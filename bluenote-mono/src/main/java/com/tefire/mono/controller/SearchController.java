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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 搜索用内存过滤代替 ES，够演示就行，不追求分词与相关性排序。 */
@RestController
@RequestMapping("/search/search")
public class SearchController {

    private final DataStore store;

    public SearchController(DataStore store) {
        this.store = store;
    }

    @PostMapping("/note")
    public Map<String, Object> searchNote(@RequestBody(required = false) Map<String, Object> body) {
        String kw = keyword(body);
        int pageNo = Json.pageNo(body == null ? null : body.get("pageNo"));
        List<Map<String, Object>> all = new ArrayList<>();
        if (!kw.isEmpty()) {
            for (Map<String, Object> n : store.notes.values()) {
                if (contains(n.get("title"), kw) || contains(n.get("content"), kw)) {
                    all.add(n);
                }
            }
        }
        all.sort(Comparator.comparingLong((Map<String, Object> n) -> Json.asLong(n.get("likeTotal"))).reversed());
        return R.page(Json.slice(all, pageNo, DataStore.PAGE_SIZE), pageNo, DataStore.PAGE_SIZE, all.size());
    }

    @PostMapping("/user")
    public Map<String, Object> searchUser(@RequestBody(required = false) Map<String, Object> body,
                                          HttpServletRequest request) {
        String kw = keyword(body);
        Long viewer = AuthInterceptor.uid(request);
        int pageNo = Json.pageNo(body == null ? null : body.get("pageNo"));
        List<Map<String, Object>> all = new ArrayList<>();
        if (!kw.isEmpty()) {
            for (Map<String, Object> u : store.users.values()) {
                if (contains(u.get("nickname"), kw) || contains(u.get("xiaohashuId"), kw)
                        || contains(u.get("introduction"), kw)) {
                    long uid = Json.asLong(u.get("userId"));
                    Map<String, Object> view = store.userView(uid);
                    view.put("isLiked", viewer != null && store.isFollowing(viewer, uid));
                    all.add(view);
                }
            }
        }
        all.sort(Comparator.comparingLong((Map<String, Object> u) -> Json.asLong(u.get("fansTotal"))).reversed());
        return R.page(Json.slice(all, pageNo, DataStore.PAGE_SIZE), pageNo, DataStore.PAGE_SIZE, all.size());
    }

    private String keyword(Map<String, Object> body) {
        String kw = body == null ? null : Json.asString(body.get("keyword"));
        return kw == null ? "" : kw.trim().toLowerCase(Locale.ROOT);
    }

    private boolean contains(Object field, String kw) {
        return field != null && String.valueOf(field).toLowerCase(Locale.ROOT).contains(kw);
    }
}
