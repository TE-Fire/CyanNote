package com.tefire.mono;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 青笺单体联调版启动类。单进程、无外部中间件，只服务于前端演示。
 */
@SpringBootApplication
public class MonoApplication {

    public static void main(String[] args) {
        // 部分 IDE / Agent 进程会注入 SERVER__PORT（会被宽松绑定解释为 server.port），
        // 其优先级高于 application.yml。这里兜底钉住 8000，-Dserver.port 与 --server.port 仍可覆盖。
        if (System.getProperty("server.port") == null) {
            System.setProperty("server.port", "8000");
        }
        SpringApplication.run(MonoApplication.class, args);
    }
}
