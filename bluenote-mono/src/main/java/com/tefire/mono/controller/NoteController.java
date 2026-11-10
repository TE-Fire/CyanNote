package com.tefire.mono.controller;

import com.tefire.mono.common.AuthInterceptor;
import com.tefire.mono.common.Json;
import com.tefire.mono.common.R;
import com.tefire.mono.store.DataStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.tefire.mono.common.Json.map;

/**
 * 笔记读写。路径保留前端的多层前缀（/note/note/*、/note/discover/*），
 * 前端因此不用改一行代码就能连上。
 */
@RestController
public class NoteController {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final DataStore store;

    public NoteController(DataStore store) {
        this.store = store;
    }

    @PostMapping("/note/note/publish")
    public Map<String, Object> publish(@RequestBody(required = false) Map<String, Object> body,
                                       HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        if (uid == null) {
            return R.fail("请先登录");
        }
        if (body == null) {
            return R.fail("请求参数缺失");
        }
        Map<String, Object> creator = store.users.get(uid);
        long noteId = store.nextId();

        List<String> imgs = new ArrayList<>();
        if (body.get("imgUris") instanceof List<?> list) {
            list.forEach(v -> imgs.add(String.valueOf(v)));
        }
        String videoUri = Json.asString(body.get("videoUri"));
        String cover = !imgs.isEmpty() ? imgs.get(0)
                : (videoUri != null && !videoUri.isBlank() ? videoUri : DataStore.BASE + "/img/cover/" + noteId + ".svg");

        List<Map<String, Object>> topics = new ArrayList<>();
        if (body.get("topics") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    topics.add(map("id", m.get("id"), "name", m.get("name")));
                }
            }
        }

        int type = body.get("type") instanceof Number n ? n.intValue() : 0;
        String now = LocalDateTime.now().format(TS);
        Map<String, Object> note = map(
                "id", noteId,
                "type", type,
                "title", Json.asString(body.get("title")),
                "content", Json.asString(body.get("content")),
                "channelId", Json.asLong(body.get("channelId")),
                "cover", cover,
                "imgUris", imgs,
                "videoUri", videoUri,
                "topics", topics,
                "creatorId", uid,
                "creatorName", creator == null ? "我" : creator.get("nickname"),
                "nickname", creator == null ? "我" : creator.get("nickname"),
                "avatar", creator == null ? null : creator.get("avatar"),
                "likeTotal", 0L,
                "collectTotal", 0L,
                "commentTotal", 0L,
                "createTime", now,
                "updateTime", now
        );
        store.notes.put(noteId, note);
        store.noteLikers.put(noteId, ConcurrentHashMap.newKeySet());
        store.noteCollectors.put(noteId, ConcurrentHashMap.newKeySet());
        return R.ok(noteId);
    }

    @PostMapping("/note/note/detail")
    public Map<String, Object> detail(@RequestBody(required = false) Map<String, Object> body) {
        Long noteId = body == null ? null : Json.asLong(body.get("id"));
        Map<String, Object> note = noteId == null ? null : store.notes.get(noteId);
        return note == null ? R.fail("笔记不存在") : R.ok(note);
    }

    @PostMapping("/note/discover/note/list")
    public Map<String, Object> discover(@RequestBody(required = false) Map<String, Object> body) {
        Long channelId = body == null ? null : Json.asLong(body.get("channelId"));
        int pageNo = Json.pageNo(body == null ? null : body.get("pageNo"));

        List<Map<String, Object>> all = new ArrayList<>(store.notes.values());
        if (channelId != null && channelId > 0) {
            all.removeIf(n -> !channelId.equals(Json.asLong(n.get("channelId"))));
        }
        all.sort(Comparator.comparingLong((Map<String, Object> n) -> Json.asLong(n.get("id"))).reversed());
        return R.page(Json.slice(all, pageNo, DataStore.PAGE_SIZE), pageNo, DataStore.PAGE_SIZE, all.size());
    }

    /** type：1 发布的笔记、2 收藏、3 赞过。 */
    @PostMapping("/note/profile/note/list")
    public Map<String, Object> profileNotes(@RequestBody(required = false) Map<String, Object> body) {
        if (body == null) {
            return R.fail("请求参数缺失");
        }
        Long userId = Json.asLong(body.get("userId"));
        if (userId == null) {
            return R.fail("缺少 userId");
        }
        int type = body.get("type") instanceof Number n ? n.intValue() : 1;
        int pageNo = Json.pageNo(body.get("pageNo"));

        List<Map<String, Object>> all = switch (type) {
            case 2 -> store.notesByCollected(userId);
            case 3 -> store.notesByLiked(userId);
            default -> store.notesByCreator(userId);
        };
        return R.page(Json.slice(all, pageNo, DataStore.PAGE_SIZE), pageNo, DataStore.PAGE_SIZE, all.size());
    }

    @PostMapping("/note/note/isLikedAndCollectedData")
    public Map<String, Object> likedAndCollected(@RequestBody(required = false) Map<String, Object> body,
                                                 HttpServletRequest request) {
        Long noteId = body == null ? null : Json.asLong(body.get("noteId"));
        Long uid = AuthInterceptor.uid(request);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("isLiked", noteId != null && uid != null && store.isNoteLiked(noteId, uid));
        data.put("isCollected", noteId != null && uid != null && store.isNoteCollected(noteId, uid));
        data.put("likeTotal", noteId == null || store.notes.get(noteId) == null ? 0 : store.notes.get(noteId).get("likeTotal"));
        data.put("collectTotal", noteId == null || store.notes.get(noteId) == null ? 0 : store.notes.get(noteId).get("collectTotal"));
        return R.ok(data);
    }

    @PostMapping("/note/note/like")
    public Map<String, Object> like(@RequestBody(required = false) Map<String, Object> body,
                                    HttpServletRequest request) {
        return toggleLike(body, request, true);
    }

    @PostMapping("/note/note/unlike")
    public Map<String, Object> unlike(@RequestBody(required = false) Map<String, Object> body,
                                      HttpServletRequest request) {
        return toggleLike(body, request, false);
    }

    @PostMapping("/note/note/collect")
    public Map<String, Object> collect(@RequestBody(required = false) Map<String, Object> body,
                                       HttpServletRequest request) {
        return toggleCollect(body, request, true);
    }

    @PostMapping("/note/note/uncollect")
    public Map<String, Object> uncollect(@RequestBody(required = false) Map<String, Object> body,
                                         HttpServletRequest request) {
        return toggleCollect(body, request, false);
    }

    private Map<String, Object> toggleLike(Map<String, Object> body, HttpServletRequest request, boolean add) {
        Long uid = AuthInterceptor.uid(request);
        Long noteId = body == null ? null : Json.asLong(body.get("id"));
        Map<String, Object> note = noteId == null ? null : store.notes.get(noteId);
        if (uid == null || note == null) {
            return R.fail("笔记不存在或未登录");
        }
        Set<Long> likers = store.noteLikers.computeIfAbsent(noteId, k -> ConcurrentHashMap.newKeySet());
        boolean changed = add ? likers.add(uid) : likers.remove(uid);
        if (changed) {
            long total = Json.asLong(note.get("likeTotal")) + (add ? 1 : -1);
            note.put("likeTotal", Math.max(0, total));
        }
        return R.ok(store.noteLikers.get(noteId).size());
    }

    private Map<String, Object> toggleCollect(Map<String, Object> body, HttpServletRequest request, boolean add) {
        Long uid = AuthInterceptor.uid(request);
        Long noteId = body == null ? null : Json.asLong(body.get("id"));
        Map<String, Object> note = noteId == null ? null : store.notes.get(noteId);
        if (uid == null || note == null) {
            return R.fail("笔记不存在或未登录");
        }
        Set<Long> collectors = store.noteCollectors.computeIfAbsent(noteId, k -> ConcurrentHashMap.newKeySet());
        boolean changed = add ? collectors.add(uid) : collectors.remove(uid);
        if (changed) {
            long total = Json.asLong(note.get("collectTotal")) + (add ? 1 : -1);
            note.put("collectTotal", Math.max(0, total));
        }
        return R.ok(store.noteCollectors.get(noteId).size());
    }
}
