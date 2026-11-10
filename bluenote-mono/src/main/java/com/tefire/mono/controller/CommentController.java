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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.tefire.mono.common.Json.map;

/**
 * 评论：一级评论分页，二级评论按父评论分页。返回体是 PageResponse，
 * 前端靠 totalCount / totalPage 判断还有没有下一页。
 */
@RestController
@RequestMapping("/comment/comment")
public class CommentController {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int PAGE_SIZE = 10;

    private final DataStore store;

    public CommentController(DataStore store) {
        this.store = store;
    }

    @PostMapping("/list")
    public Map<String, Object> list(@RequestBody(required = false) Map<String, Object> body) {
        Long noteId = body == null ? null : Json.asLong(body.get("noteId"));
        if (noteId == null) {
            return R.fail("缺少 noteId");
        }
        int pageNo = Json.pageNo(body.get("pageNo"));
        List<Map<String, Object>> all = store.commentsOfNote(noteId);
        return R.page(Json.slice(all, pageNo, PAGE_SIZE), pageNo, PAGE_SIZE, all.size());
    }

    @PostMapping("/child/list")
    public Map<String, Object> childList(@RequestBody(required = false) Map<String, Object> body) {
        Long parentId = body == null ? null : Json.asLong(body.get("parentCommentId"));
        if (parentId == null) {
            return R.fail("缺少 parentCommentId");
        }
        int pageNo = Json.pageNo(body.get("pageNo"));
        List<Map<String, Object>> all = store.childCommentsOf(parentId);
        return R.page(Json.slice(all, pageNo, PAGE_SIZE), pageNo, PAGE_SIZE, all.size());
    }

    @PostMapping("/publish")
    public Map<String, Object> publish(@RequestBody(required = false) Map<String, Object> body,
                                       HttpServletRequest request) {
        Long uid = AuthInterceptor.uid(request);
        if (uid == null) {
            return R.fail("请先登录");
        }
        Long noteId = body == null ? null : Json.asLong(body.get("noteId"));
        String content = body == null ? null : Json.asString(body.get("content"));
        if (noteId == null || store.notes.get(noteId) == null) {
            return R.fail("笔记不存在");
        }
        if (content == null || content.isBlank()) {
            return R.fail("评论内容不能为空");
        }
        Long replyId = Json.asLong(body.get("replyCommentId"));
        Map<String, Object> parent = replyId == null ? null : store.comments.get(replyId);
        if (replyId != null && parent == null) {
            return R.fail("回复的评论不存在");
        }

        Map<String, Object> user = store.users.get(uid);
        long commentId = store.nextId();
        Map<String, Object> comment = map(
                "commentId", commentId,
                "noteId", noteId,
                "parentCommentId", replyId,
                "content", content.trim(),
                "nickname", user.get("nickname"),
                "avatar", user.get("avatar"),
                "createTime", LocalDateTime.now().format(TS),
                "likeTotal", 0L,
                "childCommentTotal", 0L,
                "hasMoreChildComments", false,
                "childComments", new ArrayList<>(),
                "imageUrl", Json.asString(body.get("imageUrl")),
                "replyUserName", parent == null ? null : parent.get("nickname")
        );
        store.comments.put(commentId, comment);
        store.commentLikers.put(commentId, ConcurrentHashMap.newKeySet());

        Map<String, Object> note = store.notes.get(noteId);
        note.put("commentTotal", Json.asLong(note.get("commentTotal")) + 1);
        if (parent != null) {
            parent.put("childCommentTotal", Json.asLong(parent.get("childCommentTotal")) + 1);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) parent.get("childComments");
            children.add(comment);
        }
        return R.ok(commentId);
    }

    @PostMapping("/like")
    public Map<String, Object> like(@RequestBody(required = false) Map<String, Object> body,
                                    HttpServletRequest request) {
        return toggle(body, request, true);
    }

    @PostMapping("/unlike")
    public Map<String, Object> unlike(@RequestBody(required = false) Map<String, Object> body,
                                      HttpServletRequest request) {
        return toggle(body, request, false);
    }

    private Map<String, Object> toggle(Map<String, Object> body, HttpServletRequest request, boolean add) {
        Long uid = AuthInterceptor.uid(request);
        Long commentId = body == null ? null : Json.asLong(body.get("commentId"));
        Map<String, Object> comment = commentId == null ? null : store.comments.get(commentId);
        if (uid == null || comment == null) {
            return R.fail("评论不存在或未登录");
        }
        Set<Long> likers = store.commentLikers.computeIfAbsent(commentId, k -> ConcurrentHashMap.newKeySet());
        boolean changed = add ? likers.add(uid) : likers.remove(uid);
        if (changed) {
            long total = Json.asLong(comment.get("likeTotal")) + (add ? 1 : -1);
            comment.put("likeTotal", Math.max(0, total));
        }
        return R.ok(comment.get("likeTotal"));
    }
}
