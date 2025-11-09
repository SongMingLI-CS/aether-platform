# Aether 平台数据库设计 v0.1

## 表1: `t_knowledge_atom` (知识原子表)

| 字段名 (Column) | 类型 (Type)   | 约束 (Constraints)              | 备注 (Notes)                                 |
| :-------------- | :------------ | :------------------------------ | :------------------------------------------- |
| `id`            | `bigint(20)`  | `PRIMARY KEY`, `AUTO_INCREMENT` | 唯一ID (CTO标准：不用UUID，用雪花算法或自增) |
| `content_text`  | `text`        | `NOT NULL`                      | 知识内容 (Markdown或纯文本)                  |
| `content_type`  | `varchar(20)` | `NOT NULL`                      | `TEXT`, `MARKDOWN`, `IMAGE_URL`              |
| `created_at`    | `datetime`    | `NOT NULL`                      | 创建时间 (CTO标准：自动填充)                 |
| `updated_at`    | `datetime`    | `NOT NULL`                      | 更新时间 (CTO标准：自动更新)                 |
| `is_deleted`    | `tinyint(1)`  | `DEFAULT 0`                     | 逻辑删除 (0: 未删, 1: 已删)                  |