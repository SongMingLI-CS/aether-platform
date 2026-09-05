# Aether Platform 完善方案与任务拆解（V0.2 规划稿）

> - 状态：**草案（待评审）**
> - 创建日期：2026-09-05
> - 适用范围：`aether-platform` 仓库（当前仅含 `backend/` Spring Boot 服务与 `docs/`）
> - 关联文档：`docs/product_requirements_v0.1.md`、`docs/db_schema_v0.1.md`
> - 原则声明：本方案 **100% 遵守** 需求蓝图中的 CTO 约束 —— 拒绝云端 AI、拒绝富文本编辑器、拒绝移动端复杂交互、只做"裸核纯文本 + 本地向量化 + PC 端极简推送"、坚持 API-First。

---

## 1. 为什么要完善（现状诊断）

### 1.1 已有基础（值得保留的设计）
- 方向感清晰：`docs/` 中 V0.1 需求蓝图把产品拆成了 3 个史诗，可作为迭代主线。
- 领域实体使用 **Builder 模式** 强制必填字段、私有构造器，质量线明确。
- JPA 生命周期回调（`@PrePersist`/`@PreUpdate`）自动填充时间戳与默认值，避免了脏代码。
- Repository 走 Spring Data JPA 标准接口，无手写 SQL 蔓延。
- API-First 意识：`PingController` 返回结构化 JSON 而非裸字符串。

### 1.2 需要修复的问题（按严重程度分级）

| 级别 | 编号 | 问题 | 影响 |
| :--- | :--- | :--- | :--- |
| 🔴 P0 | P0-1 | 数据库口令明文硬编码在 `application.properties` 且已入库 | 仓库一旦公开即泄露；无法按环境切换 |
| 🔴 P0 | P0-2 | `CommandLineRunner` 每次启动都向业务表插入测试数据 | 生产环境写脏数据；测试/真实数据混杂 |
| 🔴 P0 | P0-3 | 无任何业务 API（仅 `/api/ping`），Epic 1 验收标准未落地 | 产品核心能力缺失 |
| 🟠 P1 | P1-1 | 无分层：缺 `service/dto/exception`，Entity 直接暴露给 Web 层 | 难维护、易被 API 消费者误用 |
| 🟠 P1 | P1-2 | 无统一响应封装与全局异常处理 | 出错即默认 500，前端无法按错误码处理 |
| 🟠 P1 | P1-3 | 软删除（`is_deleted`）只是字段，查询不自动过滤 | 删除后数据仍被查出，逻辑删除形同虚设 |
| 🟠 P1 | P1-4 | 无参数校验（`jakarta.validation`）、无分页 | CRUD 健壮性不足 |
| 🟡 P2 | P2-1 | `spring.jpa.hibernate.ddl-auto=update` 管理表结构 | 无迁移历史，多人/多环境不一致 |
| 🟡 P2 | P2-2 | 时区不一致（连接串 `serverTimezone=UTC`，本地 JVM 时区为 +08） | `created_at` 等时间读数混乱（已实测差 8 小时） |
| 🟡 P2 | P2-3 | README 为空、无 API 文档、无统一日志配置 | 上手与排障成本高 |
| 🟡 P2 | P2-4 | `spring.jpa.open-in-view=true`（默认）启动告警 | 长事务风险（警告已出现于启动日志） |
| 🟢 P3 | P3-1 | 无 Dockerfile / compose / CI | 交付与复现依赖人工 |
| 🟢 P3 | P3-2 | 无集成测试与 Testcontainers | 回归风险无兜底 |

### 1.3 与需求蓝图的差距（Epic 对齐）

| Epic（蓝图） | 当前完成度 | 差距 |
| :--- | :--- | :--- |
| Epic 1：知识"原子化"与"持久化" CRUD | ~30%（实体/仓库就绪） | 无 Service、无 Controller API、无校验/分页/软删除落地、无领域事件 |
| Epic 2："主动式"连接发现（本地 AI） | 0% | 无向量化、无向量库、无后台检索、无"连接"实体与事件 |
| Epic 3：极简推送界面（PC） | 0% | 无 Electron 壳、无通知通道（WS/SSE） |

---

## 2. 完善目标与非目标

### 2.1 目标（V0.2 完善范围）
1. 把后端建成**可演进的分层架构**：`controller / service / repository / domain / dto / common`。
2. 完整交付 **Epic 1**：知识原子 RESTful CRUD，含校验、分页、软删除、审计时间。
3. 为 **Epic 2** 预留异步事件地基（`AtomCreatedEvent` + outbox 或消息中间件接口抽象）。
4. 建立**工程化基线**：配置外置、安全口令轮换、全局异常、OpenAPI 文档、日志规范。
5. 全部由**测试门禁**保护：单元 + 集成（Testcontainers MySQL）。
6. 沉淀数据库迁移（Flyway）与一键启动（compose）能力。

### 2.2 非目标（V0.2 明确不做，避免范围蔓延）
- ❌ 不做任何**云端** AI / 大模型 API 调用（CTO 约束）。
- ❌ 不做**富文本**编辑器与复杂前端交互（CTO 约束）。
- ❌ 不做移动端。
- ❌ 不在 V0.2 内做完整权限/多租户体系（仅预留扩展位）。
- ❌ 不重写既有 `Builder` 模式实体与命名约定，保持代码风格一致。

---
## 3. 目标架构与设计

### 3.1 后端分层包结构（目标态）

```
com.aether.aether_backend
├── AetherBackendApplication.java
├── common                  # 横切能力
│   ├── api                 # Result<T> 统一响应、PageResult<T>
│   ├── exception           # BusinessException、ErrorCode、GlobalExceptionHandler
│   ├── config              # OpenApiConfig、JpaConfig、AsyncConfig（事件异步）
│   └── event               # DomainEvent 基类、事件发布器接口
├── domain
│   ├── atom                # KnowledgeAtom（实体，保留 Builder）
│   ├── connection          # KnowledgeConnection（Epic 2 新实体）
│   └── event               # AtomCreatedEvent 等
├── repository              # KnowledgeAtomRepository、KnowledgeConnectionRepository
├── service
│   ├── KnowledgeAtomService
│   ├── ConnectionDiscoveryService   # Epic 2
│   └── embedding           # EmbeddingClient 接口 + LocalBgeClient 实现（Epic 2）
├── controller              # AtomController（/api/v1/atoms）
├── dto                     # AtomCreateRequest/AtomUpdateRequest/AtomResponse（record）
└── util
```

### 3.2 关键横切设计决策

| 关注点 | 方案 | 理由 |
| :--- | :--- | :--- |
| 统一响应 | `Result<T>{ code, message, data, timestamp }`；错误用 `ErrorCode` 枚举 + `BusinessException` | API-First，前端可按 code 分支 |
| 异常处理 | `@RestControllerAdvice` 统一兜底：`BusinessException`→业务码；`MethodArgumentNotValidException`→400+字段错误；其余→500 且不泄露堆栈 | 一致性 + 安全 |
| DTO 与实体隔离 | 请求/响应用 Java `record` + BeanUtils/MapStruct；实体不直接出网 | 防止字段级攻击与实体演进污染 API |
| 参数校验 | `jakarta.validation` 注解（@NotBlank/@Size/Pattern） | 与 Spring Boot 3 内置 |
| 分页 | Spring Data `Pageable` + `PageResult<T>` | 标准做法 |
| 软删除 | 实体 `@SQLDelete` + `@SQLRestriction`（或 Repository 层 `@Query` 显式过滤）+ 删除走 Service 语义方法 | 保证查询不再泄露已删数据 |
| 乐观锁 | 实体加 `@Version Long version`（配合 V0.2 schema 演进） | 防并发覆盖 |
| 时间规范 | **统一存 UTC，展示转本地**：连接串去 `serverTimezone`、JVM 容器统一 `TZ=UTC`、实体字段 `Instant`（或明确 `LocalDateTime` + 存储时区一致），并在文档写明约定 | 修复 P2-2 |
| 配置外置 | 敏感项走环境变量（`DB_PASSWORD` 等），`application.yml` + `application-dev.yml`（dev 含默认值但**不含真实口令**） | 修复 P0-1 |
| API 文档 | springdoc-openapi（`/swagger-ui.html`） | 自动、零维护成本 |
| 日志 | logback：结构化 pattern、`requestId`（MDC）贯穿、请求耗时日志 | 可观测 |
| 事务与视图 | `spring.jpa.open-in-view=false` | 修复 P2-4 |
| 事件异步 | Spring `@Async` + 应用内事件（Epic 2 前足够）；预留 Kafka 接口抽象 | 为 AI 后台计算铺路 |

### 3.3 技术选型建议（含理由 / 备选）

| 领域 | 首选 | 备选 | 选型理由 |
| :--- | :--- | :--- | :--- |
| 后端框架 | Spring Boot 3.5.x（现状，Java 21） | — | 延续既有 pom |
| 数据库 | MySQL 8.0（现状） | — | 已选型 |
| 迁移工具 | Flyway | Liquibase | 简单、与 Spring Boot 集成好 |
| 向量库（Epic 2） | Milvus（Docker 单机 standalone） | Qdrant / LanceDB | 蓝图点名 Milvus；Qdrant 更轻可快速起步 |
| 向量化模型 | 本地 BGE（bge-m3 / bge-small-zh） | ONNX 运行时 / Ollama / sentence-transformers sidecar | CTO 约束"本地部署" |
| 推送通道（Epic 3） | WebSocket + SSE | — | Electron 订阅简单 |
| 桌面壳（Epic 3） | Electron（最小弹窗） | Tauri | 蓝图点名 Electron |
| 测试 | JUnit 5 + Testcontainers(MySQL) | — | 数据库真实验证 |
| 文档 | springdoc-openapi + Markdown | — | 契约可见 |

---

## 4. 阶段拆解与任务清单

> 优先级说明：P0 = 必须最先处理（安全/合规/地基）；P1 = 核心业务正确性；P2 = 工程质量；P3 = 锦上添花。
> 估时基于单人熟练度，单位为人天（d）。

### 阶段 0：工程化基线（先动手术，再盖楼）

| 任务 | 编号 | 描述 | 涉及文件 | 验收标准 | 优先级 | 估时 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 口令轮换与配置外置 | S0-1 | ①轮换 MySQL root 口令并同步 Docker 容器与配置；②`application.properties` 中的口令改为 `${DB_PASSWORD}` 环境变量引用；③新增 `application-dev.yml`（本地默认值，不入库敏感信息）与 `.env.example`；④将已提交的真实口令从 git 历史清除（filter-repo / BFG，或直接重建仓库历史） | `application.properties`、新增 `.env.example`、README | `git grep` 无明文口令；无环境变量时启动给出明确报错 | P0 | 0.5 |
| 移除启动测试数据写入 | S0-2 | `CommandLineRunner` 点火验证改为仅 `dev` profile 启用，或改为集成测试专用（`@DataJpaTest`） | `AetherBackendApplication.java`、测试类 | 生产启动日志无"INSERT 测试原子" | P0 | 0.5 |
| 引入统一响应与全局异常 | S0-3 | 新增 `Result<T>`、`ErrorCode`、`BusinessException`、`GlobalExceptionHandler` | `common/**` | `/api/ping` 改用统一封装；非法参数返回结构化错误 JSON | P0 | 1 |
| 分层重构 + DTO | S0-4 | 建立 service/dto 层；`PingController` 下放为 `SystemController`；`KnowledgeAtomService` 起步（含语义化软删除方法） | 全后端包结构调整 | 包结构符合 3.1；Controller 不含业务逻辑 | P1 | 1.5 |
| 校验与分页能力 | S0-5 | 依赖 `spring-boot-starter-validation`；DTO 加校验注解；提供 `PageResult<T>` 工具 | `pom.xml`、`dto/**`、`common/api` | 空/超长字段返回 400 + 明确字段错误 | P1 | 0.5 |
| 软删除落地 | S0-6 | 实体 `@SQLDelete`（UPDATE is_deleted=1）+ `@SQLRestriction`（is_deleted=0），仓库查询自动生效；新增 `@Version` | `KnowledgeAtom.java`、Repository | 删除后 `findAll` 不再返回该行；数据库行保留 | P1 | 0.5 |
| 时间与事务规范 | S0-7 | 统一 UTC 存储与展示约定；`open-in-view=false`；修复时区差 8h 问题（实测验证 `created_at`） | `application.properties`、实体 | 落库时间与预期一致且文档写明 | P2 | 0.5 |
| 文档与观测 | S0-8 | ①补全 README（快速开始/配置/接口）；②接入 springdoc-openapi；③logback 配置 + requestId 日志 | `README.md`、`pom.xml`、`resources/` | `mvnw spring-boot:run` 后 `/swagger-ui.html` 可访问 | P2 | 1 |
| 测试基线 | S0-9 | 为 S0-3~S0-6 补单元测试与集成测试（Testcontainers MySQL 或已有本地 Docker MySQL） | `src/test/**` | `./mvnw test` 全绿 | P1 | 1.5 |

**阶段 0 里程碑：** 无明文口令、无启动脏数据、接口全部结构化返回、`mvnw test` 全绿、README + Swagger 可用。

### 阶段 1：Epic 1 完整交付 —— 知识原子 CRUD

| 任务 | 编号 | 描述 | 涉及文件 | 验收标准 | 优先级 | 估时 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 领域事件地基 | S1-1 | 定义 `AtomCreatedEvent`，发布器在 Service 创建成功后发布（异步执行）；无订阅者时零成本 | `domain/event`、`common/event`、`KnowledgeAtomService` | 创建原子触发事件日志 | P1 | 0.5 |
| CRUD API | S1-2 | `AtomController`：`POST/GET/{id}/GET/PATCH/DELETE /api/v1/atoms`；创建走 Builder；更新仅允许 `contentText/contentType`；删除为软删除 | `controller`、`service`、`dto` | 用 curl 对全链路执行增删改查全部符合契约（见 §6） | P0 | 1.5 |
| 查询能力 | S1-3 | 分页 + 按 `contentType` 过滤 + `contentText` 关键词模糊搜索 | Service/Repository | 返回 `PageResult`；已删数据不出现在任何查询 | P1 | 0.5 |
| 幂等与并发 | S1-4 | `@Version` 乐观锁冲突返回 409 + 业务错误码 | Entity、异常 | 并发更新同一 id 一方收到 409 | P2 | 0.5 |
| 领域完整性 | S1-5 | `contentType` 收口为枚举校验（TEXT/MARKDOWN/IMAGE_URL），非法值 400 | DTO 校验 | 蓝图表设计约束可执行 | P1 | 0.5 |
| 测试覆盖 | S1-6 | Service 单测（含软删除/校验边界）+ Repository 集成测试 + Controller `MockMvc` 契约测试 | `src/test/**` | CRUD 关键路径覆盖率 ≥ 80% | P1 | 2 |
| 迁移脚本 | S1-7 | 引入 Flyway：`V1__init.sql`（按 db_schema_v0.1 + @Version 字段生成基线），切换 `ddl-auto=validate` | `resources/db/migration`、`pom.xml`、配置 | 全新环境 `mvnw spring-boot:run` 自动建表成功 | P1 | 1 |

**阶段 1 里程碑：** 交付可用 REST API + Swagger 契约 + 测试全绿 + 迁移脚本；验收标准逐条对应蓝图 Epic 1（Builder 创建 ✓ / JPA 持久化 ✓ / 纯文本输入 ✓）。

---

### 阶段 2：Epic 2 —— "主动式"连接发现（AI 核心，蓝图灵魂）

> 前置：阶段 1 的领域事件地基（S1-1）。本阶段技术栈均 **本地部署**，满足 CTO 约束。

| 任务 | 编号 | 描述 | 涉及文件/组件 | 验收标准 | 优先级 | 估时 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 向量库落地 | S2-1 | Docker 启动 Milvus standalone（或先以 Qdrant 起步）；建 collection（向量维度随模型） | compose 文件 | 容器健康；可写入/检索向量 | P0 | 1 |
| 向量化接入 | S2-2 | `EmbeddingClient` 接口 + 本地 BGE 实现（优先 Ollama API，fallback ONNX）；对原子正文切块向量化 | `service/embedding/**` | 输入文本 → 返回定长向量；无外网依赖 | P0 | 1.5 |
| 连接实体与迁移 | S2-3 | `KnowledgeConnection`（`source_atom_id,target_atom_id,similarity,status,reason,created_at`）+ Flyway V2 脚本 | Domain/Repository/migration | 表结构就绪并入库 | P1 | 0.5 |
| 异步发现流水线 | S2-4 | 消费 `AtomCreatedEvent` → 向量化新原子 → 检索 TopK 旧原子 → 相似度 ≥ 阈值(如 0.85) 落 `KnowledgeConnection`（双向去重）→ 发 `ConnectionFoundEvent` | Service、Event、AsyncConfig | 创建一条原子后，后台自动产出"连接"记录（日志可见），不阻塞 HTTP | P0 | 2 |
| 后台兜底扫描 | S2-5 | 定时任务（`@Scheduled`）对遗漏/失败的原子重算（outbox 表或重试队列）；7x24 主动 | Service、ScheduledConfig | 故障恢复后可补齐连接 | P2 | 1 |
| 连接查询 API | S2-6 | `GET /api/v1/atoms/{id}/connections`、`GET /api/v1/connections?minSimilarity=` | Controller/Service | 可按相似度阈值过滤 | P1 | 0.5 |
| 可观测性 | S2-7 | 向量化耗时、命中数、队列积压指标日志 | Logback/Actuator | 生产可定位"为什么没发现连接" | P2 | 0.5 |
| 测试 | S2-8 | EmbeddingClient 打桩单测 + 流水线集成测试（真实向量库可选） | `src/test/**` | 阈值边界、幂等、去重有断言 | P1 | 1.5 |

**阶段 2 里程碑：** 新增一条原子 → 数秒内自动发现并落库"连接"记录，可通过 API 查询；全程本地、无云端调用、无 UI 开销。

### 阶段 3：Epic 3 —— 极简推送 / 最小界面

| 任务 | 编号 | 描述 | 涉及文件/组件 | 验收标准 | 优先级 | 估时 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 通知通道 | S3-1 | WebSocket/SSE 端点（订阅 `connection-found` 主题） | 后端 | 事件推送可达客户端 | P0 | 0.5 |
| 桌面壳原型 | S3-2 | Electron 最小应用：订阅通知，弹系统级 toast 显示"新笔记 → 旧笔记 → 相似度"，无其余交互 | 新 `desktop/` 目录 | 高相关连接触发弹窗 | P1 | 2 |
| 备选：轻量 Web | S3-3 | （可选，便于演示）极简页面列出最近连接 | 新 `web/`（静态/极简） | 页面只读展示连接 | P3 | 1 |

**阶段 3 里程碑：** 桌面弹窗展示 AI 发现的连接——对应蓝图 Epic 3 验收标准（弹窗 100% 由后台结果驱动、仅含核心信息）。

### 阶段 4：交付质量与运维

| 任务 | 编号 | 描述 | 验收标准 | 优先级 | 估时 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 一键启动 | S4-1 | 根目录 `docker-compose.yml`（mysql + milvus + backend + 可选 web/desktop 构建） | `docker compose up` 一条命令起全部 | P1 | 1 |
| CI | S4-2 | GitHub Actions：push/PR 触发 `mvnw verify` + 构建镜像 | 每次合并前测试自动跑 | P2 | 0.5 |
| 健康与指标 | S4-3 | `spring-boot-starter-actuator`：`/actuator/health`、就绪探针 | compose healthcheck 使用 | P2 | 0.3 |
| 文档收尾 | S4-4 | README 全量更新（架构图/快速开始/API/常见问题）；补 `.env.example` | 文档可让新人 10 分钟跑通 | P2 | 0.5 |

---

## 5. 数据库演进（schema v0.2）

### 5.1 现有表结论（对照 `db_schema_v0.1.md`）
- `t_knowledge_atom`：结构基本可用。V0.2 需新增 **`version BIGINT NOT NULL DEFAULT 0`**（乐观锁），并将时间统一为带时区语义的存储约定。
- 现状 `content_type` 允许任意字符串 → 由应用层枚举校验收口（S1-5）。

### 5.2 新增表（Flyway V2 落地）

```sql
-- 知识原子之间的"主动发现"连接
CREATE TABLE t_knowledge_connection (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    source_atom_id  BIGINT       NOT NULL,
    target_atom_id  BIGINT       NOT NULL,
    similarity      DECIMAL(6,5) NOT NULL COMMENT '相似度 0~1',
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CONFIRMED/IGNORED',
    reason          VARCHAR(500) NULL     COMMENT '触发摘要，如命中片段',
    created_at      DATETIME     NOT NULL,
    updated_at      DATETIME     NOT NULL,
    is_deleted      TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_conn_source (source_atom_id),
    KEY idx_conn_similarity (similarity),
    CONSTRAINT uk_conn_pair UNIQUE (source_atom_id, target_atom_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识连接（AI 主动发现结果）';
```

### 5.3 Flyway 策略
- 新增 `resources/db/migration/`；`V1__create_knowledge_atom.sql` 基线（含 `version` 字段）→ `V2__create_knowledge_connection.sql`。
- 切换 `spring.jpa.hibernate.ddl-auto=validate`；本地已存在的表可用 `flyway repair/baseline` 平滑接管。

---

## 6. API 契约草案（V0.2 目标态，`/api/v1`）

统一返回：`{ "code": 0, "message": "OK", "data": ..., "timestamp": "..." }`

| 方法 | 路径 | 说明 | 关键参数 |
| :--- | :--- | :--- | :--- |
| GET | `/api/v1/atoms` | 分页查询 | `page,size,contentType,keyword` |
| GET | `/api/v1/atoms/{id}` | 详情 | — |
| POST | `/api/v1/atoms` | 创建（Builder） | body: `contentText*, contentType*` |
| PATCH | `/api/v1/atoms/{id}` | 更新内容 | body: `contentText? / contentType?` |
| DELETE | `/api/v1/atoms/{id}` | 软删除 | — |
| GET | `/api/v1/atoms/{id}/connections` | 该原子的连接 | `minSimilarity` |
| GET | `/api/v1/connections` | 全量连接 | `status,minSimilarity,page,size` |
| GET | `/api/ping` | 存活探测（保留兼容） | — |

错误码示例：`40000 参数错误`、`40400 资源不存在`、`40900 版本冲突`、`50000 内部错误`。具体码表在实现期定稿。

---

## 7. 测试与质量门禁

- **单元测试**：Service 业务规则（软删除语义、Builder 必填、阈值比较）——打桩 Repository/EmbeddingClient。
- **集成测试**：Testcontainers MySQL（或本地 aether-mysql 容器）跑 `@SpringBootTest`，覆盖 JPA 映射与迁移脚本。
- **契约测试**：`MockMvc` 断言请求/响应结构与错误码。
- **门禁**：`./mvnw verify` 全绿才允许合并；CI（S4-2）强制执行。

---

## 8. 建议执行顺序与里程碑

```
M1（阶段 0）：安全 + 分层 + 异常 + 测试基线        → 预计 1 周
M2（阶段 1）：Epic1 CRUD + Swagger + Flyway         → 预计 1 周
M3（阶段 2）：向量库 + BGE + 主动发现流水线         → 预计 1.5~2 周
M4（阶段 3）：Electron 弹窗 / SSE                   → 预计 0.5~1 周
M5（阶段 4）：compose + CI + 文档收尾               → 预计 0.5 周
```

**给初学者的执行建议：**
1. **不要并行开工**，严格按 M1 → M2 顺序，先让"地基干净"再写业务，避免返工。
2. 阶段 2 的模型选择上，**先 Ollama 拉起 bge 系列跑通端到端**，再考虑 ONNX 深度优化，避免一开始陷入模型工程。
3. 每个任务合入前自检：`./mvnw test` + `curl` 手工验证一遍契约。

---

## 9. 风险与注意事项

| 风险 | 说明 | 缓解 |
| :--- | :--- | :--- |
| P0-1 口令历史泄露 | 轮换后旧口令仍在 git 历史 | 用 filter-repo 清洗 + 强制轮换；公开仓库前先处理 |
| 向量库资源占用 | Milvus 对内存要求较高 | 开发期用 Qdrant 或 Milvus 最小配置；模型选 bge-small |
| `ddl-auto` 切 `validate` 的过渡 | 存量表与新迁移可能不一致 | 先在测试库演练 baseline，再切换 |
| 事件丢失 | 应用内事件在进程重启时丢失 | 先接受"尽力而为"，阶段 2 用 outbox 表兜底 |
| 范围蔓延 | 想同时做 Web/移动/多租户 | 严格按 §2.2 非目标执行，一切进 backlog |

