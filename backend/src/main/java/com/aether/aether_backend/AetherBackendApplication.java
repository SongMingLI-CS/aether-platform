package com.aether.aether_backend; // <--- 【CTO已校准】 匹配你的包名

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 这是 Aether 后端大脑的 "点火开关"。
 * @SpringBootApplication 告诉Spring Boot："从这里开始，自动配置一切！"
 */
@SpringBootApplication
public class AetherBackendApplication {

    /**
     * 这是 "Java程序" 的主入口 (main方法)。
     * 我们的 "CTO标准" 不动它，只调用Spring的 "run" 方法。
     */
    public static void main(String[] args) {
        SpringApplication.run(AetherBackendApplication.class, args);
    }

    // 我们100%拒绝 "半成品"。
    // "点火开关" 里不写任何 "业务逻辑"。
    // 业务逻辑在 "Controller" (如 PingController) 里。

}