# 青笺 CyanNote

> 内容分享平台（仿小红书），后端基于 Spring Cloud Alibaba 微服务，含 Vue3 前端工程，另规划一个用于联调演示的单体版。

---

## 一、项目简介

青笺（CyanNote）是一个内容分享社交平台，支持发布图文 / 视频笔记、关注用户、点赞收藏与消息计数。后端按微服务拆分，前端为 Vue3 单页应用。

**代码目录与包名仍沿用 `bluenote` / `com.tefire`**：更名只作用于项目展示名称，未做仓库级重命名，避免打断既有提交历史与本地环境。

**当前状态**：微服务后端为学习型实现，功能覆盖主干链路（登录、笔记发布、点赞收藏、关注、计数），但接口未做全量实现；仓库内没有建表 SQL、Nacos 配置导出与容器编排，全量启动依赖 8 类中间件，本地资源难以承受。为此提供第七章的单体联调版：单 jar、零中间件，专门跑前端演示。

---

## 二、技术栈

| 分类 | 技术 | 版本 |
|------|------|------|
| 语言 | Java | 21 |
| 框架 | Spring Boot | 3.2.5 |
| 微服务 | Spring Cloud | 2023.0.0 |
| 微服务 | Spring Cloud Alibaba | 2023.0.1.0 |
| 注册 / 配置中心 | Nacos | 2.2.x |
| 网关 | Spring Cloud Gateway | - |
| 认证 | Sa-Token | 1.38.0 |
| ORM | MyBatis | 3.0.3 |
| 数据库 | MySQL | 8.0+ |
| 键值存储 | Cassandra | - |
| 对象存储 | MinIO | 8.2.1 |
| 缓存 | Redis（**需 Redis Stack**，用到布隆过滤器命令） | - |
| 本地缓存 | Caffeine | 3.1.8 |
| 消息队列 | RocketMQ | 5.3.1 |
| 计数聚合 | BufferTrigger | - |
| 定时任务 | XXL-JOB | 2.4.1 |
| 分布式 ID | 美团 Leaf（Segment / Snowflake，依赖 ZooKeeper） | - |
| HTTP 客户端 | OpenFeign | - |
| 连接池 | Druid | 1.2.23 |
| 工具类 | Hutool / Guava / Jackson | 5.8.26 / 33.0.0-jre / 2.16.1 |
| 前端 | Vue 3、Vite、Pinia、Vue Router、Tailwind CSS、Axios | 见 `xiaohashu-vue3/package.json` |

---

## 三、架构设计

### 3.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                  客户端（xiaohashu-vue3，Vue3 SPA）              │
└─────────────────────────────────────────────────────────────────┘
                              │  /api → 127.0.0.1:8000
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                   bluenote-gateway（网关层，8000）               │
│  • 路由转发（StripPrefix=1） • Sa-Token 鉴权 • 用户 ID 透传     │
└─────────────────────────────────────────────────────────────────┘
                              │
         ┌────────────────────┼────────────────────┬──────────────────┐
         ▼                    ▼                    ▼                  ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│ bluenote-auth   │  │ bluenote-user   │  │ bluenote-note   │  │bluenote-user-rel│
│   认证服务 8080  │  │   用户服务 8082  │  │   笔记服务 8086  │  │  用户关系 8087   │
│  • 登录注册     │  │  • 用户管理     │  │  • 笔记 CRUD    │  │  • 关注/取关     │
│  • 验证码       │  │  • 权限同步     │  │  • 点赞收藏     │  │  • 粉丝列表      │
│  • Sa-Token     │  │  • RBAC        │  │  • 多级缓存     │  │  • Redis ZSet   │
└─────────────────┘  └─────────────────┘  └─────────────────┘  └─────────────────┘
         │                    │                    │
         ▼                    ▼                    ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────────┐
│ bluenote-oss    │  │ bluenote-kv     │  │ bluenote-distributed-id-gen │
│  对象存储 8081   │  │  键值存储 8084   │  │   分布式 ID 8085             │
│  • MinIO        │  │  • Cassandra    │  │  • Leaf Segment/Snowflake   │
│  • 文件上传     │  │  • 笔记正文      │  │  • ZooKeeper                │
└─────────────────┘  └─────────────────┘  └─────────────────────────────┘

异步 / 后台层（图中未画连线，均通过 RocketMQ 与业务服务解耦）
┌───────────────────────────────────┐  ┌───────────────────────────────────┐
│ bluenote-count（8090）            │  │ bluenote-data-align（8091）       │
│  • 9 个 MQ 消费者，按 Tag 分流    │  │  • 日增量表 + 布隆过滤            │
│  • BufferTrigger 聚合计数         │  │  • XXL-JOB 分片日终对齐           │
│  • Redis 计数 + 批量落库          │  │  • 4 个布隆 Lua                   │
└───────────────────────────────────┘  └───────────────────────────────────┘

基础设施层：MySQL • Redis Stack • Cassandra • MinIO • RocketMQ • Nacos • ZooKeeper • XXL-JOB
```

### 3.2 模块划分

端口以各模块 `application.yml` 为准（README 旧版端口有误，已按代码校正）。

| 模块 | 职责 | 端口 |
|------|------|------|
| `bluenote-gateway` | API 网关，路由转发，统一鉴权，用户 ID 透传 | 8000 |
| `bluenote-auth` | 登录 / 注册 / 登出，短信验证码 | 8080 |
| `bluenote-user` | 用户信息，RBAC 角色权限同步 Redis | 8082 |
| `bluenote-user-relation` | 关注 / 取关，关注与粉丝列表 | 8087 |
| `bluenote-note` | 笔记发布、详情、点赞、收藏，多级缓存 | 8086 |
| `bluenote-oss` | 文件上传（MinIO，阿里云 OSS 仅预留策略） | 8081 |
| `bluenote-kv` | 笔记正文存储（Cassandra） | 8084 |
| `bluenote-distributed-id-generator` | 分布式 ID（Leaf Segment / Snowflake） | 8085 |
| `bluenote-count` | MQ 消费与计数聚合落库 | 8090 |
| `bluenote-data-align` | 日增量对齐与布隆校验（XXL-JOB） | 8091 |
| `bluenote-framework` | 通用组件与自定义 Starter | - |
| `xiaohashu-vue3` | Vue3 + Vite 前端工程 | 5173（dev） |
| `bluenote-mono` | 单体联调版，用于前端演示（零外部中间件） | 8000 |

### 3.3 框架层组件

| 组件 | 说明 |
|------|------|
| `bluenote-common` | 统一响应 `Response` / `PageResponse`，异常契约，工具类 |
| `bluenote-spring-boot-starter-biz-context` | 用户上下文传递，TransmittableThreadLocal，Header→Context 过滤器 |
| `bluenote-spring-boot-starter-biz-operationlog` | 接口操作日志切面 |
| `bluenote-spring-boot-starter-jackson` | Jackson 配置，Java8 日期序列化 |

---

## 四、核心功能

### 4.1 认证模块（bluenote-auth）

- 手机号 + 验证码登录，登录即注册
- 阿里云短信验证码发送（需配置 AccessKey）
- Sa-Token 管理登录态，`random-128` Token 风格，Header 为 `Authorization: Bearer <token>`
- 登出、密码修改

### 4.2 用户模块（bluenote-user）

- 用户信息更新（含头像上传，multipart）
- 启动时 `PushRolePermissions2RedisRunner` 把角色-权限映射同步到 Redis
- 对外提供 `findById` / `findByIds` / `findByPhone` 等 Feign 接口

### 4.3 用户关系模块（bluenote-user-relation）

- 关注 / 取关，Redis ZSet 维护关注与粉丝列表
- 粉丝列表热点截断（缓存最新 5000 条）
- Lua 脚本实现原子分页查询
- RocketMQ 异步落库，补偿写失败

### 4.4 笔记模块（bluenote-note）

- 发布 / 详情 / 更新 / 删除 / 置顶 / 仅自己可见
- 点赞、收藏与取消（布隆去重 + ZSet 状态）
- Redis + Caffeine 多级缓存，更新时延迟双删 + MQ 广播清除本地缓存
- 正文写入 Cassandra，元数据留在 MySQL；跨库无本地事务，插入失败时回调 kv 删除正文做补偿
- 发布成功后 `asyncSend` 到 `NoteOperateTopic`，由 count 服务异步计数

### 4.5 对象存储模块（bluenote-oss）

- MinIO 上传 / 下载 / 删除，工厂 + 策略模式隔离存储实现
- 阿里云 OSS 策略当前为空实现（`uploadFile` 仅返回 null），仅作扩展预留

### 4.6 键值存储模块（bluenote-kv）

- 笔记正文的增 / 查 / 删，基于 Spring Data Cassandra
- 与笔记元数据通过 `content_uuid` 关联

### 4.7 分布式 ID 模块（bluenote-distributed-id-generator）

- 号段模式（Segment）：批量取号，适合高并发
- 雪花算法（Snowflake）：依赖 ZooKeeper 分配 workerId
- 以 HTTP + Feign 两种方式对外提供

### 4.8 计数模块（bluenote-count）

- 9 个 MQ 消费者，按 Tag 区分点赞 / 收藏 / 关注 / 发布 / 删除
- Redis 计数缓存 + BufferTrigger 聚合后批量落库，压平写放大
- Guava RateLimiter 控制落库速率

### 4.9 数据对齐模块（bluenote-data-align）

- 订阅日增量 Topic，写入日增量表
- XXL-JOB 分片任务做日终对账
- 布隆过滤器校验笔记 / 用户是否存在，避免无效数据写入

---

## 五、运行环境要求

### 5.1 基础设施

| 服务 | 端口 | 说明 |
|------|------|------|
| Nacos | 8848 / 9848 | 服务注册与配置中心，namespace 为 `bluenote` |
| MySQL | 3306 | 业务库 `bluenote`，Leaf 库 `leaf` |
| Redis | 6379 | **必须是 Redis Stack**，note 与 data-align 的 Lua 使用 `BF.ADD` / `BF.EXISTS` |
| Cassandra | 9042 | keyspace `bluenote`，存笔记正文 |
| MinIO | 9000 / 9001 | 对象存储 |
| RocketMQ NameServer | 9876 | 消息队列 |
| RocketMQ Broker | 10911 | 容器化时须设 `brokerIP1=127.0.0.1` |
| ZooKeeper | 2181 | Leaf 雪花算法使用 |
| XXL-JOB Admin | 7777 | 仅 data-align 使用，可延后 |

### 5.2 环境变量

```bash
# 阿里云短信配置（bluenote-auth）
ALIBABA_CLOUD_ACCESS_KEY_ID=your_access_key_id
ALIBABA_CLOUD_ACCESS_KEY_SECRET=your_access_key_secret

# MinIO 配置（bluenote-oss）
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
```

---

## 六、快速开始（微服务版）

> 完整启动需要上表全部中间件加 11 个服务，仅适合资源充足的机器。**本机联调演示请直接看第七章单体版**。

### 6.1 启动顺序

```bash
# 1. 启动基础设施：Nacos、MySQL、Redis Stack、Cassandra、MinIO、RocketMQ、ZooKeeper
# 2. 初始化数据：MySQL 建库建表、Cassandra keyspace、MinIO bucket、Nacos namespace

# 3. 分布式 ID（被 note / user 依赖，必须最先）
cd bluenote/bluenote-distributed-id-generator/bluenote-distributed-id-generator-biz && mvn spring-boot:run

# 4. 基础服务
cd bluenote/bluenote-user/bluenote-user-biz && mvn spring-boot:run
cd bluenote/bluenote-kv/bluenote-kv-biz && mvn spring-boot:run
cd bluenote/bluenote-oss/bluenote-oss-biz && mvn spring-boot:run

# 5. 认证与业务
cd bluenote/bluenote-auth && mvn spring-boot:run
cd bluenote/bluenote-note/bluenote-note-biz && mvn spring-boot:run
cd bluenote/bluenote-user-relation/bluenote-user-relation-biz && mvn spring-boot:run

# 6. 异步层（可延后）
cd bluenote/bluenote-count/bluenote-count-biz && mvn spring-boot:run
cd bluenote/bluenote-data-align && mvn spring-boot:run

# 7. 网关（最后启动）
cd bluenote/bluenote-gateway && mvn spring-boot:run
```

### 6.2 配置说明

| 文件 | 说明 |
|------|------|
| `bootstrap.yml` | Nacos 引导配置（服务名、namespace、config 开关） |
| `config/application.yml` | 本地基础配置（端口、数据源、中间件地址） |
| `config/application-dev.yml` | 开发环境配置 |
| `logback-spring.xml` | 日志配置 |

网关只启用 discovery；auth / oss / count / data-align / user-relation 同时启用 config，DataId 形如 `bluenote-auth-dev.yaml`。若想完全读本地配置，可注释这些模块 bootstrap 中的 config 段。

### 6.3 前端

```bash
cd xiaohashu-vue3
npm install
npm run dev     # 5173，/api 代理到 127.0.0.1:8000
```

---

## 七、前端联调单体版（bluenote-mono）

### 7.1 目的与定位

微服务全量启动需要 8 类中间件 + 11 个服务，在开发机上无法稳定运行，导致前端页面无法演示。为此在仓库根目录新增 **`bluenote-mono`**：单进程、单 jar、零外部中间件，只用于与 `xiaohashu-vue3` 联调、演示交互效果。

**它不是微服务架构的替代品**，不承担缓存一致性、消息可靠投递、分布式 ID 等能力验证；这些仍由 `bluenote` 下的微服务实现承载。

### 7.2 设计约束

| 约束 | 方案 |
|------|------|
| 启动快 | 单个 Spring Boot 应用，`spring-boot:run` 冷启动目标 < 5s |
| 占资源少 | 堆内存 `-Xmx256m`；无 Nacos / Redis / RocketMQ / Cassandra / ZooKeeper |
| 存储 | 纯内存（ConcurrentHashMap）+ 启动灌种子数据，重启即重置；不引数据库，省掉建表与连接池 |
| 鉴权 | 内存 Token 表 + 拦截器，非法或过期返回 HTTP 401（对齐前端拦截器判断） |
| 端口 | 8000，与网关一致，前端 `vite.config.js` 代理不用改 |
| 文件上传 | 落本地目录并通过静态资源映射访问，不依赖 MinIO |
| 接口 | **以 `xiaohashu-vue3/src/api/*.js` 为唯一契约**，路径、方法、请求体、响应结构逐条对齐 |

### 7.3 接口契约（27 个调用）

响应统一为 `Response<T>`：`{ success, message, errorCode, data }`；分页接口为 `PageResponse<T>`：在 `Response` 基础上追加 `{ pageNo, totalCount, pageSize, totalPage }`，列表放在 `data`。前端拦截器直接返回 `response.data`，页面再取 `res.data`。

单体版按**前端原样路径**实现，不做 StripPrefix 模拟。下表同时标注微服务侧现状：

| # | 前端调用 | 前端路径（POST） | 微服务侧 |
|---|----------|------------------|-----------|
| 1 | getVerificationCode | `/auth/verification/code/send` | 已实现 |
| 2 | login | `/auth/login` | 已实现 |
| 3 | logout | `/auth/logout` | 已实现 |
| 4 | publishNote | `/note/note/publish` | 已实现 |
| 5 | getNoteDetail | `/note/note/detail` | 已实现 |
| 6 | likeNote | `/note/note/like` | 已实现 |
| 7 | unlikeNote | `/note/note/unlike` | 已实现 |
| 8 | collectNote | `/note/note/collect` | 已实现 |
| 9 | uncollectNote | `/note/note/uncollect` | 已实现 |
| 10 | followUser | `/relation/relation/follow` | 已实现 |
| 11 | getFollowingList | `/relation/relation/following/list` | 已实现 |
| 12 | getFansList | `/relation/relation/fans/list` | 已实现 |
| 13 | updateUserProfile | `/user/user/update`（multipart） | 已实现 |
| 14 | getAllChannel | `/note/channel/list` | **缺**：NoteController 无频道接口 |
| 15 | getTopicList | `/note/topic/list` | **缺** |
| 16 | getDiscoverNotePageList | `/note/discover/note/list` | **缺** |
| 17 | getProfileNotePageList | `/note/profile/note/list` | **缺** |
| 18 | isLikedAndCollectedData | `/note/note/isLikedAndCollectedData` | **缺** |
| 19 | getUserProfile | `/user/user/profile` | **缺**：UserController 无 `/profile` |
| 20 | uploadFile | `/oss/file/upload`（multipart） | **路径不一致**：网关路由为 `/file/**`，OSS 服务为 `/file/upload` |
| 21 | getCommentList | `/comment/comment/list` | **无模块** |
| 22 | publishComment | `/comment/comment/publish` | **无模块** |
| 23 | getChildCommentList | `/comment/comment/child/list` | **无模块** |
| 24 | likeComment | `/comment/comment/like` | **无模块** |
| 25 | unlikeComment | `/comment/comment/unlike` | **无模块** |
| 26 | searchNote | `/search/search/note` | **无模块** |
| 27 | searchUser | `/search/search/user` | **无模块** |

13 个可直接对接，13 个微服务尚未实现，1 个路径不一致。单体版需要全部 27 个都有响应，缺失部分按页面实际用到的字段补最小可用实现（含评论、搜索的假数据检索）。

### 7.4 与微服务版的关系

- 前端两种后端都能连：端口同为 8000，切换只需决定启动哪一个。
- 单体版的接口路径刻意保留前端的多层前缀（如 `/note/note/publish`），是为了不改前端代码；微服务侧若要对齐，需要调整网关路由或控制器映射，属于另一项独立工作，本次不做。
- 数据仅存内存，刷新后重置，不提供持久化与并发一致性保证。

### 7.5 启动与能力边界

```bash
cd bluenote-mono
mvn -DskipTests package
java -Xmx256m -jar target/bluenote-mono.jar     # 约 2 秒启动，200 MB 上下
bash smoke.sh                                    # 27 个接口逐条自检
```

前端照旧 `npm run dev`，`/api` 代理指向 8000，不需要改配置。种子数据为 6 个用户、24 篇笔记、10 个频道、8 个话题、7 条评论（含 1 条二级评论），图片由 `/img/**` 现场生成 SVG，不依赖外网图床。

**已跑通的演示路径**：发现页与频道筛选、笔记详情与轮播、点赞收藏、搜索笔记与用户、关注与粉丝列表、个人主页三个 tab、评论与回复、验证码登录（手机号填满即自动取码，验证码由服务端随机生成并在弹窗内直接显示、自动填入）、发布图文笔记、上传头像与图片。

**刻意不做的**：视频笔记（无视频素材，全部图文）、密码登录（前端只提供验证码入口）、真实短信、计数最终一致与日终对账、并发控制。数据全在内存，重启即恢复种子状态。

**一个环境坑**：部分 IDE / Agent 进程会注入 `SERVER__PORT`，Spring 的宽松绑定会把它当成 `server.port` 并覆盖 `application.yml`。启动类已在 `main` 中兜底钉住 8000，`-Dserver.port` 与 `--server.port` 仍可覆盖。

---

## 八、项目结构

```
BlueNoteBook/
├── bluenote/                            # 微服务后端（Maven 多模块）
│   ├── bluenote-gateway/                # 网关
│   ├── bluenote-auth/                   # 认证
│   ├── bluenote-user/                   # 用户（api / biz）
│   ├── bluenote-note/                   # 笔记（api / biz）
│   ├── bluenote-user-relation/          # 用户关系（api / biz）
│   ├── bluenote-oss/                    # 对象存储（api / biz）
│   ├── bluenote-kv/                     # 笔记正文（api / biz）
│   ├── bluenote-distributed-id-generator/
│   ├── bluenote-count/                  # 计数聚合
│   ├── bluenote-data-align/             # 数据对齐
│   ├── bluenote-framework/              # common + 3 个 starter
│   └── pom.xml                          # 父 POM
├── xiaohashu-vue3/                      # 前端（Vue3 + Vite）
├── bluenote-mono/                       # 单体联调版（单 jar，零中间件）
│   ├── src/main/java/com/tefire/mono/   # controller / store / common / config
│   └── smoke.sh                         # 27 个前端接口自检脚本
├── 学习总结/                            # 技术笔记
├── api-documentation.md / .json         # 接口文档
└── README.md
```

---

## 九、关键设计

### 9.1 认证流程

```
客户端 → Gateway → Sa-Token 校验 → AddUserId2HeaderFilter 写入 userId → 业务服务
                        │
                        └─ 白名单（登录、验证码）/ 其余需登录
```

### 9.2 缓存策略

```
查询 → Caffeine 本地缓存 → Redis → MySQL
            ↑                ↑
       MQ 广播清除      延迟双删
```

### 9.3 笔记内容存储

```
笔记元数据（MySQL） ←→ content_uuid ←→ 笔记正文（Cassandra）
        │                                      │
        └──────── RocketMQ 计数通知 ───────────┘
```

### 9.4 跨库一致性

MySQL 与 Cassandra 之间没有本地事务，采用「先写正文、再写元数据、失败补偿删除」：
写 Cassandra 成功但 MySQL 插入失败时，捕获异常并回调 kv 删除正文，避免产生孤儿数据；消息侧由 count 的异步消费与 data-align 的日终对账兜底。

---

## 十、接口文档

- [API 文档（Markdown）](api-documentation.md)
- [API 文档（JSON，可导入 APIFox）](api-documentation.json)

接口文档记录的是微服务侧已实现的接口；前端实际调用范围见 7.3 的契约表，两者存在差异，以契约表为准补齐。

---

## 十一、学习资源

| 文档 | 主题 |
|------|------|
| [Nacos 深度解析](学习总结/Nacos%20微服务注册中心与配置中心深度解析.md) | 服务注册与配置管理 |
| [Gateway 鉴权解析](学习总结/Gateway%20网关%20Sa-Token%20鉴权与%20Nacos%20集成深度解析.md) | 网关鉴权与路由 |
| [分布式 ID 解析](学习总结/分布式ID生成服务——Segment与Snowflake双模式深度解析.md) | ID 生成策略 |
| [ThreadLocal 解析](学习总结/ThreadLocal%20与%20TransmittableThreadLocal%20深度解析.md) | 线程上下文传递 |
| [Spring 事务解析](学习总结/Spring%20声明式%20vs%20编程式事务管理深度解析.md) | 事务管理 |
| [自定义 Starter 解析](学习总结/Spring%20Boot%20自定义%20Starter%20深度解析.md) | Starter 封装 |
| [Cassandra 解析](学习总结/Cassandra%20分布式NoSQL数据库深度解析.md) | 分布式存储 |
| [Feign 解析](学习总结/Feign%20声明式HTTP客户端与微服务上下文透传深度解析.md) | HTTP 客户端 |
| [Java Stream 解析](学习总结/Java%20Stream%20流式编程深度解析.md) | 流式编程 |

---

## 十二、开发进度

| 模块 | 状态 | 说明 |
|------|------|------|
| bluenote-gateway | 完成 | 5 条路由，鉴权与用户 ID 透传 |
| bluenote-auth | 完成 | 登录、验证码、登出、改密 |
| bluenote-user | 部分完成 | 缺 `/user/profile` 个人资料查询 |
| bluenote-note | 部分完成 | 缺频道、话题、发现页列表、个人页列表、点赞收藏状态查询 |
| bluenote-user-relation | 完成 | 关注 / 取关 / 两个列表 |
| bluenote-oss | 部分完成 | MinIO 可用，阿里云 OSS 为空实现 |
| bluenote-kv | 完成 | 正文增删查 |
| bluenote-distributed-id-generator | 完成 | Segment + Snowflake |
| bluenote-count | 完成 | MQ 消费 + 聚合落库 |
| bluenote-data-align | 完成 | 日增量 + 分片对齐 |
| bluenote-framework | 完成 | common + 3 个 starter |
| 评论 / 搜索模块 | 未开始 | 前端已有页面与 API 封装，后端无对应服务 |
| xiaohashu-vue3 | 可运行 | 需后端或单体版提供接口 |
| bluenote-mono | 已实现 | 27 个前端接口全部可用，见第七章 |

**已知缺口**：无建表 SQL、无 Nacos 配置导出、无容器编排；部分配置项仍为明文凭据，上线前需外置。

---

## 十三、许可证

MIT License

---

## 十四、联系方式

如有问题或建议，欢迎提交 Issue 或 PR。
