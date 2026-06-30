package com.example.golden_retriever_java.config; // [NEW] 歸類於設定資料夾

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.cache")
@Data
public class AppCacheProperties {
    private long maxAge;
    private long tradingOpen;
    private long tradingClosed;
    private long ratesGold;
    private long chart;
    private long cleanInterval;
}