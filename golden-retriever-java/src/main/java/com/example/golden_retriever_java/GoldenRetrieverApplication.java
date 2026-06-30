package com.example.golden_retriever_java;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@org.springframework.data.jpa.repository.config.EnableJpaRepositories(basePackages = "com.example.golden_retriever_java.repository")
@org.springframework.boot.autoconfigure.domain.EntityScan(basePackages = "com.example.golden_retriever_java.entity")
public class GoldenRetrieverApplication {

    private static final Logger log = LoggerFactory.getLogger(GoldenRetrieverApplication.class);

    public static void main(String[] args) {
        try {
            SpringApplication.run(GoldenRetrieverApplication.class, args);
            log.info("Golden Retriever System Started Successfully.");
        } catch (Exception e) {
            // 忽略 DevTools 的靜默退出異常，避免主執行緒非預期終止
            if (!e.getClass().getName().contains("SilentExitException")) {
                log.error("系統啟動失敗 ({}): {}", e.getClass().getName(), e.getMessage());
                System.exit(1);
            }
        }
    }
}
