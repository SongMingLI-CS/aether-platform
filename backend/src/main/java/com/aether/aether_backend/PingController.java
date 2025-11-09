package com.aether.aether_backend;

// 导入我们需要的Spring框架类
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// 导入Java工具类 (用于创建Map)
import java.util.Map;

/**
 * @RestController 告诉Spring这是一个API控制器 (Controller)。
 * 它处理Web请求，并且每个方法默认返回JSON数据。
 */
@RestController
public class PingController {

    /**
     * @GetMapping("/api/ping") 将HTTP GET请求
     * 映射到 "http://.../api/ping" 这个路径。
     * 当浏览器或工具访问这个URL时，下面的 "ping()" 方法将被执行。
     */
    @GetMapping("/api/ping")
    public Map<String, String> ping() {

        // CTO标准：我们是 "API-First" 架构。
        // 我们不返回 "pong" 这样的原始字符串。
        // 我们返回结构化的JSON数据 (Map会自动转换成JSON)。
        // 这为未来的 "API消费者" (如React前端) 提供了清晰的 "契约"。

        return Map.of("status", "Aether Core-Zero Online");
    }
}