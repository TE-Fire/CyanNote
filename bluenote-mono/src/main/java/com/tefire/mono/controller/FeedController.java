package com.tefire.mono.controller;

import com.tefire.mono.common.Json;
import com.tefire.mono.common.R;
import com.tefire.mono.store.DataStore;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 频道与话题：发现页、发布页的选项数据。 */
@RestController
public class FeedController {

    private final DataStore store;

    public FeedController(DataStore store) {
        this.store = store;
    }

    @PostMapping("/note/channel/list")
    public Map<String, Object> channels() {
        return R.ok(store.channels);
    }

    @PostMapping("/note/topic/list")
    public Map<String, Object> topics(@RequestBody(required = false) Map<String, Object> body) {
        String keyword = body == null ? "" : Json.asString(body.get("keyword"));
        String kw = keyword == null ? "" : keyword.trim();
        List<Map<String, Object>> list = store.topics.stream()
                .filter(t -> kw.isEmpty() || String.valueOf(t.get("name")).contains(kw))
                .toList();
        return R.ok(list);
    }
}
