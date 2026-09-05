# Aether Platform

面向"知识原子"的本地优先知识管理与主动连接发现平台（后端）。

> 当前进度：**V0.2 · 阶段 0（工程化基线）已落地** —— 分层架构 / 统一响应 / 全局异常 / 配置外置 / 软删除与乐观锁 / 集成测试。规划详见 [`docs/improvement_plan_v0.2.md`](docs/improvement_plan_v0.2.md)。

## ✨ 功能路线（对齐需求蓝图）

| Epic | 内容 | 状态 |
| :--- | :--- | :--- |
| 1 | 知识原子（KnowledgeAtom）CRUD：创建用 Builder、JPA 持久化、纯文本/Markdown | 阶段 1 待实施（Service/DTO/校验已就绪） |
| 2 | "主动式"连接发现：本地 BGE 向量化 + 向量库后台检索，发现原子间高相关连接 | 未开始 |
| 3 | PC 端极简推送：AI 发现高相关连接后弹窗通知（Electron） | 未开始 |

## 🧰 技术栈

- Java 21 · Spring Boot 3.5 · Spring Data JPA · MySQL 8
- Bean Validation · springdoc-openapi（Swagger UI）· Testcontainers
- Maven Wrapper

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

