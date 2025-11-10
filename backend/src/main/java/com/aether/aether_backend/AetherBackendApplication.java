package com.aether.aether_backend; // <--- 【CTO已校准】 匹配你的包名

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.aether.aether_backend.domain.KnowledgeAtom;
import com.aether.aether_backend.repository.KnowledgeAtomRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean; // <-- "验证Bean"需要这个


/**
 * 这是 Aether 后端大脑的 "点火开关"。
 * @SpringBootApplication 告诉Spring Boot："从这里开始，自动配置一切！"
 */
@SpringBootApplication
public class AetherBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(AetherBackendApplication.class, args);
    }

    // --- 【CTO加速战役：点火验证】 ---
    // (@Bean 会告诉Spring："这是一个"Bean"，请"管理"它)
    // (CommandLineRunner 是一个"接口"，它"保证"这段代码在"Spring启动完成"后"自动"执行)

    @Bean
    public CommandLineRunner initDatabase(KnowledgeAtomRepository repository) {

        // Spring 会"自动"把我们【步骤七】创建的 "repository" (神经索) "注入"到这里

        return args -> {

            // "开始执行"

            System.out.println(" "); // 换行
            System.out.println(">>> CTO: 正在执行【加速战役：点火验证】...");

            // 1. 使用你(CEO)在【步骤五】交付的 "Builder模式"
            //    "创建"一个"测试原子"
            KnowledgeAtom testAtom = new KnowledgeAtom.Builder(
                    "Aether Core-Zero - DB Connection Test - OK", // contentText
                    "TEXT"                                       // contentType
            ).build();
            // (createdAt/updatedAt/isDeleted 会被我们【步骤六】的 @PrePersist 自动填充)

            // 2. "命令" 神经索 (JPA) "保存" 它
            //    这会"触发"一次"数据库 INSERT"
            repository.save(testAtom);

            // 3. "汇报战果"
            System.out.println(">>> CTO: 【点火验证】成功! 实体ID: [" + testAtom.getId() + "] 已存入 aether_db。");
            System.out.println(" "); // 换行
        };
    }
    // --- 【CTO代码结束】 ---

}





    // 我们100%拒绝 "半成品"。
    // "点火开关" 里不写任何 "业务逻辑"。
    // 业务逻辑在 "Controller" (如 PingController) 里。

