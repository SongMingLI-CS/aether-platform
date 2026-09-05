# Aether Platform

面向"知识原子"的本地优先知识管理与主动连接发现平台（后端 + React 前端）。

[![CI](https://github.com/SongMingLI-CS/aether-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/SongMingLI-CS/aether-platform/actions/workflows/ci.yml)

> 当前进度：**V0.2 · 阶段 4（交付质量）已交付** —— 知识原子 CRUD + 主动连接发现 + Web 界面 + Docker Compose 一键起 / CI / Actuator 健康检查。规划详见 [`docs/improvement_plan_v0.2.md`](docs/improvement_plan_v0.2.md)。

## ✨ 功能路线（对齐需求蓝图）

| Epic | 内容 | 状态 |
| :--- | :--- | :--- |
| 1 | 知识原子（KnowledgeAtom）CRUD：创建用 Builder、JPA 持久化、纯文本/Markdown | ✅ 已交付（`/api/v1/atoms`，含分页/关键词搜索/软删除/乐观锁） |
| 2 | "主动式"连接发现：本地向量化 + 检索，自动发现原子间高相关连接 | ✅ 已交付（原子创建即异步发现，`/api/v1/connections` 可查；Embedding/向量库均可插拔切换） |
| 3 | 极简推送/界面：高相关连接触达用户（PC 端） | ✅ Web 界面已交付（React：原子管理 + 连接发现可视化，对应"新笔记→旧笔记→相似度"展示）；桌面弹窗（Electron）可选后续 |

## 🧰 技术栈

- Java 21 · Spring Boot 3.5 · Spring Data JPA · MySQL 8
- Bean Validation · springdoc-openapi（Swagger UI）· Testcontainers
- Maven Wrapper

## 🐳 一键启动（Docker Compose）

```bash
# 根目录执行（可选覆盖口令与端口）
DB_PASSWORD=your-password BACKEND_PORT=8080 FRONTEND_PORT=8081 docker compose up -d --build
```

- 前端：http://localhost:8081（`/api` 由 Nginx 反代到 backend）
- 后端：http://localhost:8080（`/actuator/health` 健康检查、`/swagger-ui.html`）
- MySQL 数据持久化在卷 `aether-mysql-data`；三个服务均带 healthcheck 与 `restart: unless-stopped`

> 默认口令为 `aether-dev-password`（仅供本地开发，生产务必通过 `DB_PASSWORD` 覆盖）。

## 🚀 快速开始（本地）

前置：JDK 21、Docker（跑 MySQL 与集成测试）。

```bash
# 1. 启动 MySQL 8（数据存 Docker 卷 aether-mysql-data，可复用）
docker run -d --name aether-mysql --restart unless-stopped \
  -e MYSQL_ROOT_PASSWORD='<你的强口令>' \
  -e MYSQL_DATABASE=aether_db \
  -p 3306:3306 \
  -v aether-mysql-data:/var/lib/mysql \
  mysql:8.0

# 2. 注入数据库口令（绝不写进代码/仓库；参考 backend/.env.example）
export DB_PASSWORD='<与上一步相同的口令>'   # DB_URL / DB_USERNAME / SERVER_PORT 均可选覆盖

# 3. 运行
cd backend
./mvnw spring-boot:run          # Linux/macOS；Windows 用 mvnw.cmd
```

启动后验证：

```bash
curl http://localhost:8080/api/ping
# {"code":0,"message":"OK","data":{"status":"Aether Core-Zero Online"},"timestamp":"..."}
```

Swagger API 文档：http://localhost:8080/swagger-ui.html

知识原子 CRUD 快速示例：

```bash
# 创建
curl -X POST http://localhost:8080/api/v1/atoms \
  -H 'Content-Type: application/json' \
  -d '{"contentText":"我的第一条知识原子","contentType":"MARKDOWN"}'
# 分页 + 关键词搜索（contentType 可选）
curl 'http://localhost:8080/api/v1/atoms?page=0&size=20&keyword=知识&contentType=TEXT'
# 详情 / 更新 / 删除（软删除）
curl http://localhost:8080/api/v1/atoms/1
curl -X PATCH http://localhost:8080/api/v1/atoms/1 -H 'Content-Type: application/json' -d '{"contentText":"更新后的内容"}'
curl -X DELETE http://localhost:8080/api/v1/atoms/1
```

知识连接发现（Epic 2，原子创建后自动异步执行）：

```bash
# 查看全部连接（可按 status=PENDING、minSimilarity=0.8 过滤）
curl 'http://localhost:8080/api/v1/connections?status=PENDING'
# 查看某个原子的连接
curl 'http://localhost:8080/api/v1/atoms/1/connections'
```

> 开发模式（自动写入一条演示数据、输出 SQL）：
> `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`

## 🧪 测试

集成测试使用 Testcontainers 自动拉起真实 MySQL，不依赖本地数据库口令：

```bash
cd backend && ./mvnw test        # 需要 Docker 正在运行
```

## 📁 目录结构

```
backend/src/main/java/com/aether/aether_backend
├── common/        # 横切能力：统一响应(Result/PageResult)、异常(ErrorCode+全局处理)、请求ID日志
├── controller/    # Web 层（SystemController）
├── service/       # 业务层（KnowledgeAtomService）
├── dto/           # 出入参 record + 校验
├── domain/        # 领域实体（Builder 创建、软删除、乐观锁、UTC 审计时间）
└── repository/    # Spring Data JPA
docs/              # 需求蓝图 / 数据库设计 / 完善方案（V0.2）
frontend/          # Vite + React + TypeScript 前端（含 Dockerfile + nginx.conf）
backend/Dockerfile # 后端多阶段构建（maven build → temurin JRE）
docker-compose.yml # MySQL + backend + frontend 一键编排
.github/workflows  # GitHub Actions CI（后端测试 + 前端构建）
```

## 🖥 前端（Vite + React + TypeScript）

```bash
cd frontend
npm install        # 安装依赖
npm run dev        # 开发服务器 → http://localhost:5173（/api 自动代理到 8080 后端）
npm run build      # 类型检查 + 生产构建 → dist/
```

## 🔐 配置与安全约定

- **仓库中不存任何真实口令**：数据源凭据全部来自环境变量（`DB_URL` / `DB_USERNAME` / `DB_PASSWORD`），见 [`backend/.env.example`](backend/.env.example)。
- 时间统一按 **UTC**（实体使用 `java.time.Instant`）。
- 删除采用**逻辑删除**（`is_deleted`），查询自动过滤；实体带 `@Version` 乐观锁防并发覆盖。
- API 统一返回 `Result{code, message, data, timestamp}`，错误码见 `ErrorCode`。

## 📚 相关文档

- [`docs/product_requirements_v0.1.md`](docs/product_requirements_v0.1.md) —— V0.1 需求蓝图
- [`docs/db_schema_v0.1.md`](docs/db_schema_v0.1.md) —— V0.1 数据库设计
- [`docs/improvement_plan_v0.2.md`](docs/improvement_plan_v0.2.md) —— 完善方案与任务拆解

