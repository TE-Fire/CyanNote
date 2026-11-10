package com.tefire.mono.store;

import com.tefire.mono.common.Json;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import static com.tefire.mono.common.Json.map;

/**
 * 演示数据全部驻留内存：重启即重置，不提供持久化与并发一致性保证。
 * 所有写操作加 synchronized，只为演示期间不出现半更新状态。
 */
@Component
public class DataStore {

    public static final int PAGE_SIZE = 10;
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    public static final String BASE = "http://127.0.0.1:8000";

    public final Map<Long, Map<String, Object>> users = new ConcurrentHashMap<>();
    public final Map<Long, Map<String, Object>> notes = new ConcurrentHashMap<>();
    public final Map<Long, Map<String, Object>> comments = new ConcurrentHashMap<>();
    public final List<Map<String, Object>> channels = new CopyOnWriteArrayList<>();
    public final List<Map<String, Object>> topics = new CopyOnWriteArrayList<>();

    /** token -> userId */
    public final Map<String, Long> tokens = new ConcurrentHashMap<>();
    /** phone -> 已下发的验证码，演示环境直接随响应返回给前端展示 */
    public final Map<String, String> codes = new ConcurrentHashMap<>();
    /** userId -> 关注的 userId 集合 */
    public final Map<Long, Set<Long>> follows = new ConcurrentHashMap<>();
    /** noteId -> 点赞用户 */
    public final Map<Long, Set<Long>> noteLikers = new ConcurrentHashMap<>();
    /** noteId -> 收藏用户 */
    public final Map<Long, Set<Long>> noteCollectors = new ConcurrentHashMap<>();
    /** commentId -> 点赞用户 */
    public final Map<Long, Set<Long>> commentLikers = new ConcurrentHashMap<>();

    private final AtomicLong seq = new AtomicLong(3000);

    public DataStore() {
        seedChannels();
        seedTopics();
        seedUsers();
        seedNotes();
        seedComments();
    }

    public long nextId() {
        return seq.incrementAndGet();
    }

    // ---------------------------------------------------------------- 认证

    public synchronized String issueToken(long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        tokens.put(token, userId);
        return token;
    }

    /** 生成 6 位随机验证码并暂存，供前端直接展示。 */
    public synchronized String issueCode(String phone) {
        String code = String.format("%06d", java.util.concurrent.ThreadLocalRandom.current().nextInt(1_000_000));
        codes.put(phone, code);
        return code;
    }

    /** 已下发过验证码的必须匹配；未点过获取验证码的放行，便于演示直接登录。 */
    public boolean verifyCode(String phone, String code) {
        String expected = codes.get(phone);
        return expected == null || expected.equals(code);
    }

    /** 验证码登录：手机号不存在则建号，前端拿不到密码体系。 */
    public synchronized Map<String, Object> loginByPhone(String phone) {
        for (Map<String, Object> u : users.values()) {
            if (phone.equals(u.get("phone"))) {
                return u;
            }
        }
        long id = nextId();
        Map<String, Object> u = map(
                "userId", id,
                "phone", phone,
                "nickname", "青笺用户" + String.valueOf(phone).substring(Math.max(0, phone.length() - 4)),
                "xiaohashuId", "cyan_" + id,
                "avatar", BASE + "/img/avatar/" + id + ".svg",
                "introduction", "还没有填写简介",
                "sex", 0,
                "birthday", null,
                "age", 0
        );
        users.put(id, u);
        return u;
    }

    // ---------------------------------------------------------------- 视图

    /** 用户资料：统计字段实时算，避免种子数据与关系表对不上。 */
    public Map<String, Object> userView(long userId) {
        Map<String, Object> u = users.get(userId);
        if (u == null) {
            return null;
        }
        Map<String, Object> v = new LinkedHashMap<>(u);
        // 手机号只在登录环节使用，不对外返回
        v.remove("phone");
        long noteTotal = notes.values().stream()
                .filter(n -> Long.valueOf(userId).equals(Json.asLong(n.get("creatorId"))))
                .count();
        long fans = follows.values().stream().filter(s -> s.contains(userId)).count();
        long following = follows.getOrDefault(userId, Set.of()).size();
        long likeAndCollect = notes.values().stream()
                .filter(n -> Long.valueOf(userId).equals(Json.asLong(n.get("creatorId"))))
                .mapToLong(n -> Json.asLong(n.get("likeTotal")) + Json.asLong(n.get("collectTotal")))
                .sum();
        v.put("noteTotal", noteTotal);
        v.put("fansTotal", fans);
        v.put("followingTotal", following);
        v.put("likeAndCollectTotal", likeAndCollect);
        return v;
    }

    public boolean isFollowing(long followerId, long targetId) {
        return follows.getOrDefault(followerId, Set.of()).contains(targetId);
    }

    public boolean isNoteLiked(long noteId, long userId) {
        return noteLikers.getOrDefault(noteId, Set.of()).contains(userId);
    }

    public boolean isNoteCollected(long noteId, long userId) {
        return noteCollectors.getOrDefault(noteId, Set.of()).contains(userId);
    }

    public List<Map<String, Object>> notesByCreator(long creatorId) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> n : notes.values()) {
            if (Long.valueOf(creatorId).equals(Json.asLong(n.get("creatorId")))) {
                list.add(n);
            }
        }
        list.sort((a, b) -> Long.compare(Json.asLong(b.get("id")), Json.asLong(a.get("id"))));
        return list;
    }

    public List<Map<String, Object>> notesByLiked(long userId) {
        List<Map<String, Object>> list = new ArrayList<>();
        noteLikers.forEach((noteId, set) -> {
            if (set.contains(userId) && notes.containsKey(noteId)) {
                list.add(notes.get(noteId));
            }
        });
        list.sort((a, b) -> Long.compare(Json.asLong(b.get("id")), Json.asLong(a.get("id"))));
        return list;
    }

    public List<Map<String, Object>> notesByCollected(long userId) {
        List<Map<String, Object>> list = new ArrayList<>();
        noteCollectors.forEach((noteId, set) -> {
            if (set.contains(userId) && notes.containsKey(noteId)) {
                list.add(notes.get(noteId));
            }
        });
        list.sort((a, b) -> Long.compare(Json.asLong(b.get("id")), Json.asLong(a.get("id"))));
        return list;
    }

    public List<Map<String, Object>> commentsOfNote(long noteId) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> c : comments.values()) {
            if (Long.valueOf(noteId).equals(Json.asLong(c.get("noteId")))
                    && c.get("parentCommentId") == null) {
                list.add(c);
            }
        }
        list.sort((a, b) -> Long.compare(Json.asLong(a.get("commentId")), Json.asLong(b.get("commentId"))));
        return list;
    }

    public List<Map<String, Object>> childCommentsOf(long parentCommentId) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> c : comments.values()) {
            if (Long.valueOf(parentCommentId).equals(Json.asLong(c.get("parentCommentId")))) {
                list.add(c);
            }
        }
        list.sort((a, b) -> Long.compare(Json.asLong(a.get("commentId")), Json.asLong(b.get("commentId"))));
        return list;
    }

    // ---------------------------------------------------------------- 种子数据

    private void seedChannels() {
        String[] names = {"穿搭", "美食", "旅行", "摄影", "读书", "健身", "手作", "数码", "职场", "宠物"};
        for (int i = 0; i < names.length; i++) {
            channels.add(map("id", (long) (i + 1), "name", names[i]));
        }
    }

    private void seedTopics() {
        String[][] raw = {
                {"一人食", "128"}, {"城市散步", "96"}, {"胶片日记", "74"}, {"阳台种菜", "41"},
                {"自习室", "63"}, {"通勤穿搭", "152"}, {"周末去哪", "210"}, {"深夜书桌", "58"}
        };
        for (int i = 0; i < raw.length; i++) {
            topics.add(map("id", (long) (i + 1), "name", raw[i][0], "noteCount", Long.valueOf(raw[i][1])));
        }
    }

    private void seedUsers() {
        String[][] raw = {
                {"阿柚", "cyan_001", "在城市里找光斑，顺手记下来", "1"},
                {"林间有风", "cyan_002", "周末通常在山里，没信号", "1"},
                {"老陈的厨房", "cyan_003", "一个人也要好好吃饭", "0"},
                {"七号观测站", "cyan_004", "拍星星的人，偶尔拍月亮", "1"},
                {"空白纸张", "cyan_005", "正在学画画，画得不好但很努力", "0"},
                {"深夜搬砖", "cyan_006", "写后端，也写字", "1"}
        };
        for (int i = 0; i < raw.length; i++) {
            long id = i + 1;
            users.put(id, map(
                    "userId", id,
                    "phone", "1380000000" + (i + 1),
                    "nickname", raw[i][0],
                    "xiaohashuId", raw[i][1],
                    "avatar", BASE + "/img/avatar/" + id + ".svg",
                    "introduction", raw[i][2],
                    "sex", Integer.parseInt(raw[i][3]),
                    "birthday", "200" + (i % 3) + "-0" + (i + 1) + "-12",
                    "age", 20 + i
            ));
        }
        // 预置几条关注关系，让关注/粉丝列表打开就有内容
        follows.put(1L, ConcurrentHashMap.newKeySet());
        follows.get(1L).addAll(Set.of(2L, 3L, 4L));
        follows.put(2L, ConcurrentHashMap.newKeySet());
        follows.get(2L).add(1L);
        follows.put(4L, ConcurrentHashMap.newKeySet());
        follows.get(4L).addAll(Set.of(1L, 2L));
    }

    private void seedNotes() {
        // 标题 | 正文 | 频道 id | 话题 | 图片数
        String[][] raw = {
                {"秋日巷口的银杏", "午休溜出去，巷子尽头那棵树把光筛成一片一片。风一过就落一层，扫地的大爷说每年就这几天，让我赶紧拍。", "4", "城市散步,胶片日记", "3"},
                {"一个人住的第七个月", "今天终于把厨房收拾顺手了：常用的放左手边，锅具按频率排。做饭这件事，动线对了就不想点外卖。", "3", "一人食,深夜书桌", "2"},
                {"通勤四十分钟能读完多少", "地铁上读完半本短篇集。站着也能读，只要书够轻、句子够短。记了两页笔记，晚上再回过头看。", "5", "通勤穿搭,自习室", "2"},
                {"阳台上的第一茬小葱", "剪了三次还在长。种菜这件事的成就感，比想象中来得快。", "7", "阳台种菜", "2"},
                {"周末去了趟旧书店", "二楼靠窗那排全是九十年代的诗集，纸都黄了。老板说不卖，只是摆着。", "5", "城市散步", "3"},
                {"夜里的便利店", "加班到十一点，关东煮的汤还是热的。店员在补货，塑料筐拖地的声音很轻。", "3", "一人食,深夜书桌", "2"},
                {"把桌面收拾干净之后", "只留了显示器、键盘、一盏灯。写东西的时候，视线里少一样东西，注意力就多一点点。", "9", "深夜书桌", "2"},
                {"第一次拍银河", "开车三小时到郊外，等了两个小时云才散。参数试了七八组，最后这张是 ISO3200、f2.8、20 秒。", "4", "胶片日记", "3"},
                {"跑步半年记", "从三公里喘到十公里，配速没快多少，但心率降了。最大的变化是睡得着了。", "6", "周末去哪", "2"},
                {"旧毛衣改造的托特包", "袖子剪下来做提手，缝了两遍才平。第一次动手做东西，成就感是真的。", "7", "周末去哪", "3"},
                {"读书笔记怎么写才有用", "我的做法是：读完先合上书，凭记忆写三句；再翻回去补漏。记不住的，本来也不重要。", "5", "自习室,深夜书桌", "2"},
                {"换了个机械键盘", "轴体选了静音红，办公室不吵人。敲起来比想象中轻，手腕没那么累了。", "8", "通勤穿搭", "2"},
                {"清晨六点的菜市场", "比超市便宜，也比超市热闹。卖豆腐的阿姨认得熟客，会多给一块。", "3", "一人食,城市散步", "3"},
                {"把简历改了第七版", "删掉了所有「负责」，换成做了什么、结果如何。篇幅短了一半，反而更清楚。", "9", "自习室", "2"},
                {"楼下的猫换了地盘", "从车棚搬到了花坛边，大概是那里晒得久。蹲下来它也不躲，只是看着你。", "10", "城市散步", "2"},
                {"深秋的第一件外套", "驼色太长，最后选了灰绿。里面配白 T 就够，冷了再加薄毛衣。", "1", "通勤穿搭", "3"},
                {"写了一年的日记", "翻回去看，记的都是小事：今天吃了什么、和谁说了什么。但合起来就是这一年。", "5", "深夜书桌", "2"},
                {"给房间换了盏灯", "暖光 3000K，晚上不开顶灯。屋子一下子软下来。", "8", "深夜书桌", "2"},
                {"第一次做戚风", "塌了两次，第三次终于没回缩。关键是蛋白打到硬性发泡，翻拌别画圈。", "3", "一人食", "3"},
                {"爬山那天下了小雨", "雾把山腰盖住，只露出一点轮廓。走到半程雨停了，云在脚下走。", "2", "周末去哪", "3"},
                {"把收藏夹清了一遍", "存了两百多条没看的文章，删到三十条。剩下的这周看完，看不完就承认不会看。", "9", "自习室", "2"},
                {"周末的早市花摊", "十五块一把洋桔梗，插在玻璃瓶里能开一周。买花这件事，是给自己的一点仪式感。", "1", "周末去哪", "2"},
                {"重装了一次电脑", "只装了必要的软件，开机从四十秒到八秒。清爽是会上瘾的。", "8", "自习室", "2"},
                {"睡前十分钟拉伸", "坚持了一个月，腰不酸了。动作很简单，重点是每天都做。", "6", "深夜书桌", "2"}
        };
        long base = 1000;
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < raw.length; i++) {
            long noteId = base + i + 1;
            long creatorId = (i % 6) + 1;
            Map<String, Object> creator = users.get(creatorId);
            List<Map<String, Object>> topicList = new ArrayList<>();
            String[] tagNames = raw[i][3].split(",");
            for (String tag : tagNames) {
                for (Map<String, Object> t : topics) {
                    if (tag.equals(t.get("name"))) {
                        topicList.add(map("id", t.get("id"), "name", t.get("name")));
                    }
                }
            }
            List<String> imgs = new ArrayList<>();
            int imgCount = Integer.parseInt(raw[i][4]);
            for (int k = 1; k <= imgCount; k++) {
                imgs.add(BASE + "/img/photo/" + noteId + "-" + k + ".svg");
            }
            String time = now.minusDays(i).format(TS);
            notes.put(noteId, map(
                    "id", noteId,
                    "type", 0,
                    "title", raw[i][0],
                    "content", raw[i][1],
                    "channelId", Long.parseLong(raw[i][2]),
                    "cover", BASE + "/img/cover/" + noteId + ".svg",
                    "imgUris", imgs,
                    "videoUri", null,
                    "topics", topicList,
                    "creatorId", creatorId,
                    "creatorName", creator.get("nickname"),
                    "nickname", creator.get("nickname"),
                    "avatar", creator.get("avatar"),
                    "likeTotal", (long) (3 + (i * 7) % 46),
                    "collectTotal", (long) (1 + (i * 3) % 28),
                    "commentTotal", 0L,
                    "createTime", time,
                    "updateTime", time
            ));
            noteLikers.put(noteId, ConcurrentHashMap.newKeySet());
            noteCollectors.put(noteId, ConcurrentHashMap.newKeySet());
        }
    }

    private void seedComments() {
        Object[][] raw = {
                {1001L, 1L, "构图很好看，请问是用什么镜头拍的？", 12L},
                {1001L, 3L, "这条巷子我也路过过，原来秋天这么好看。", 5L},
                {1001L, 5L, "保存了，下次去打卡。", 2L},
                {1002L, 2L, "动线这个说法太对了，我做饭就是到处找东西。", 8L},
                {1002L, 4L, "求问锅具怎么收纳的？", 1L},
                {1003L, 6L, "地铁读书 +1，我一般带口袋本。", 4L},
                {1008L, 2L, "银河那张太震撼了，参数记下了。", 15L}
        };
        long cid = 5000;
        LocalDateTime now = LocalDateTime.now();
        for (Object[] row : raw) {
            long noteId = (Long) row[0];
            long userId = (Long) row[1];
            Map<String, Object> u = users.get(userId);
            long id = ++cid;
            String time = now.minusHours(cid % 40).format(TS);
            comments.put(id, map(
                    "commentId", id,
                    "noteId", noteId,
                    "parentCommentId", null,
                    "content", row[2],
                    "nickname", u.get("nickname"),
                    "avatar", u.get("avatar"),
                    "createTime", time,
                    "likeTotal", row[3],
                    "childCommentTotal", 0L,
                    "hasMoreChildComments", false,
                    "childComments", new ArrayList<>(),
                    "replyUserName", null
            ));
            commentLikers.put(id, ConcurrentHashMap.newKeySet());
            Map<String, Object> note = notes.get(noteId);
            if (note != null) {
                note.put("commentTotal", Json.asLong(note.get("commentTotal")) + 1);
            }
        }
        // 一条二级评论，用于验证评论区的二级列表
        long parent = 5001L;
        long childId = ++cid;
        Map<String, Object> parentComment = comments.get(parent);
        Map<String, Object> child = map(
                "commentId", childId,
                "noteId", parentComment.get("noteId"),
                "parentCommentId", parent,
                "content", "同问，蹲一个答案。",
                "nickname", users.get(4L).get("nickname"),
                "avatar", users.get(4L).get("avatar"),
                "createTime", LocalDateTime.now().minusMinutes(30).format(TS),
                "likeTotal", 0L,
                "childCommentTotal", 0L,
                "hasMoreChildComments", false,
                "childComments", new ArrayList<>(),
                "replyUserName", parentComment.get("nickname")
        );
        comments.put(childId, child);
        parentComment.put("childCommentTotal", 1L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) parentComment.get("childComments");
        children.add(child);
    }

    public static List<Map<String, Object>> immutable(List<Map<String, Object>> src) {
        return Collections.unmodifiableList(src);
    }
}
