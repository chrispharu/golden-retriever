package com.example.golden_retriever_java;

import com.example.golden_retriever_java.service.DataService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication
@EnableScheduling
public class GoldenRetrieverApplication {

    private static final Logger log = LoggerFactory.getLogger(GoldenRetrieverApplication.class);

    public static void main(String[] args) {
        // [MODIFIED] 真正的全自動化建庫：在 Spring 啟動前進行 Pre-flight Check
        preFlightDatabaseCheck();

        try {
            SpringApplication.run(GoldenRetrieverApplication.class, args);
            log.info("Golden Retriever System Started Successfully.");
        } catch (Exception e) {
            log.error("系統啟動失敗: {}", e.getMessage());
            System.exit(1);
        }
    }

    private static void preFlightDatabaseCheck() {
        log.info("[Pre-flight] 正在檢查資料庫環境...");
        try {
            // 1. 手動讀取 application.yml (不依賴 Spring Context)
            Path path = Paths.get("src", "main", "resources", "application.yml");
            if (!Files.exists(path)) {
                // 如果是在 target/classes 下執行
                path = Paths.get("target", "classes", "application.yml");
            }

            if (Files.exists(path)) {
                YAMLMapper mapper = new YAMLMapper();
                JsonNode root = mapper.readTree(path.toFile());
                JsonNode ds = root.path("spring").path("datasource");

                String url = ds.path("url").asText();
                String user = ds.path("username").asText();
                String pass = ds.path("password").asText();

                if (!url.isEmpty()) {
                    // 2. 執行自動建庫邏輯
                    DataService.ensureDatabaseExists(url, user, pass);
                }
            }
        } catch (Exception e) {
            log.warn("[Pre-flight] 無法預先讀取設定檔進行建庫檢查，將交由 Spring 嘗試連線: {}", e.getMessage());
        }
    }
}
