package com.aether.aether_backend.domain;

// 【导入 "弹药"】
import jakarta.persistence.*; // <-- 【JPA核心弹药】
import java.time.LocalDateTime; // <-- 【CTO标准：现代时间API】

/**
 * Aether 核心领域实体 (Domain Entity): 知识原子
 * 1. 这是一个 "JPA实体" (Entity)
 * 2. 这是一个 "POJO" (Plain Old Java Object)
 * 3. 它使用 "Builder模式" 进行"安全"创建
 */
@Entity // <-- 【注解1】: 告诉JPA："这是一个"实体类"，请"管理"它
@Table(name = "t_knowledge_atom") // <-- 【注解2】: “精确”对应我们"昨天"设计的"表名"
public class KnowledgeAtom {

    // --- 字段 (Fields) ---
    // (对应 `db_schema_v0.1.md`)

    @Id // <-- 【注解3】: 告诉JPA："这是主键(Primary Key)"
    @GeneratedValue(strategy = GenerationType.IDENTITY) // <-- 【注解4】: 数据库"自增ID"
    private Long id;

    @Column(columnDefinition = "TEXT", nullable = false) // <-- 【注解5】: 对应"字段", TEXT类型, 不允许为空
    private String contentText;

    @Column(nullable = false, length = 20)
    private String contentType;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 我们在 "db_schema_v0.1.md" 中定义了 "DEFAULT 0"
    // 我们用 "Boolean" (对象) 而不是 "boolean" (基本类型) 来允许 "null" 值，以便JPA处理
    @Column(nullable = false)
    private Boolean isDeleted;

    // --- 【CTO标准：JPA"必须"的"无参构造"】 ---
    /**
     * JPA (Hibernate) 需要这个"无参构造函数"来"实例化"对象
     */
    public KnowledgeAtom() {
    }

    // --- 【CTO标准：JPA"生命周期"回调】 ---
    // 我们"不"相信"业务逻辑"能"记住"设置时间戳。
    // 我们用"自动化"来"强制"纪律。

    @PrePersist // <-- 在"插入"(INSERT)数据库"之前"，"自动"执行此方法
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.isDeleted == null) {
            this.isDeleted = false; // 确保默认值
        }
    }

    @PreUpdate // <-- 在"更新"(UPDATE)数据库"之前"，"自动"执行此方法
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // --- 【CTO质量线：Builder模式】 (【步骤五】的"核心交付物") ---

    // 1. “私有”构造器 (禁止外部 "new KnowledgeAtom(...)" )
    // 我们的 "实体" 只能通过 "Builder" 创建
    private KnowledgeAtom(Builder builder) {
        this.contentText = builder.contentText;
        this.contentType = builder.contentType;
        // id, createdAt, updatedAt, isDeleted 将被 "JPA" 和 "@PrePersist" 自动管理
    }

    // 2. “静态”内部类：Builder
    public static class Builder {
        // "必填" 字段
        private final String contentText;
        private final String contentType;

        // "可选" 字段 (我们暂时没有，但Builder模式支持)

        public Builder(String contentText, String contentType) {
            // 在Builder的"构造器"中"强制"所有"必填"参数
            if (contentText == null || contentType == null) {
                throw new IllegalArgumentException("Content and Type must not be null");
            }
            this.contentText = contentText;
            this.contentType = contentType;
        }

        // "build()" 方法：真正"创建" KnowledgeAtom 的地方
        public KnowledgeAtom build() {
            return new KnowledgeAtom(this);
        }
    }

    // --- Getters and Setters ---
    // JPA 和 "业务逻辑" 需要它们来 "读/写" 字段
    // (CTO提示: 你可以用 `Alt + Insert` -> `Getter and Setter` 自动生成它们)

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getContentText() {
        return contentText;
    }

    public void setContentText(String contentText) {
        this.contentText = contentText;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Boolean getDeleted() {
        return isDeleted;
    }

    public void setDeleted(Boolean deleted) {
        isDeleted = deleted;
    }
}