package com.example.golden_retriever_java.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class QuantService {
    private static final Logger log = LoggerFactory.getLogger(QuantService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Autowired
    private SearchService searchService;

    // 記憶體快取
    private final ConcurrentHashMap<String, JsonNode> cache = new ConcurrentHashMap<>();
    private LocalDate lastCacheDate = LocalDate.MIN;

    public JsonNode getTopStocks(int count, long capital, String inventorySymbols) {
        String cacheKey = "market-" + count + "-" + capital;
        if (LocalDate.now().equals(lastCacheDate) && cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }
        return runQuantEngine("market", count, capital, inventorySymbols);
    }

    /**
     * 單一股票 AI 診斷 (帶快取)
     */
    public JsonNode diagnoseStock(String symbol) {
        String cacheKey = "diag-" + symbol;
        if (LocalDate.now().equals(lastCacheDate) && cache.containsKey(cacheKey)) {
            log.info("[Quant] 使用診斷快取: {}", symbol);
            return cache.get(cacheKey);
        }
        return runQuantEngine("diagnose", 1, 0, symbol);
    }

    private JsonNode runQuantEngine(String mode, int count, long capital, String inventory) {
        try {
            log.info("[Quant] 啟動 AI 引擎 | 模式: {} | 目標: {}", mode, inventory);
            String uvPath = System.getProperty("user.home") + "\\.local\\bin\\uv.exe";
            String scriptPath = "D:\\ProjectD\\golden-retriever-java\\scripts\\quant_selector.py";

            ProcessBuilder pb = new ProcessBuilder(
                uvPath, "run", 
                "--with", "finlab>=1.5.9", "--with", "TA-Lib", "--with", "lightgbm", "--with", "scikit-learn",
                "python", scriptPath,
                "--mode", mode,
                "--count", String.valueOf(count),
                "--capital", String.valueOf(capital),
                "--inventory", (inventory != null ? inventory : "")
            );
            
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().startsWith("{")) {
                        sb.append(line);
                    } else {
                        log.debug("[UV Log] {}", line);
                    }
                }
            }
            
            process.waitFor();
            JsonNode result = objectMapper.readTree(sb.toString());
            
            // 補足名稱
            if (result.has("stocks")) fillStockNames((ArrayNode) result.get("stocks"));
            if (result.has("diagnosis")) {
                JsonNode diagNode = result.get("diagnosis");
                if (diagNode.isObject()) {
                    diagNode.fields().forEachRemaining(entry -> {
                        if (entry.getValue() instanceof ObjectNode) {
                            fillSingleStockName((ObjectNode) entry.getValue());
                        }
                    });
                }
            }
            
            if (!result.has("error")) {
                String cacheKey = mode.equals("market") ? ("market-" + count + "-" + capital) : ("diag-" + inventory);
                cache.put(cacheKey, result);
                lastCacheDate = LocalDate.now();
            }
            
            return result;
        } catch (Exception e) {
            log.error("AI 引擎執行失敗: {}", e.getMessage());
            return objectMapper.createObjectNode().put("error", e.getMessage());
        }
    }

    private void fillStockNames(ArrayNode stocks) {
        for (JsonNode stock : stocks) {
            if (stock instanceof ObjectNode) fillSingleStockName((ObjectNode) stock);
        }
    }

    private void fillSingleStockName(ObjectNode node) {
        String symbolWithSuffix = node.path("symbol").asText();
        String symbol = symbolWithSuffix.replace(".TW", "").replace(".TWO", "");
        String name = node.path("name").asText(symbol);

        if (name.equals(symbol) || name.equals(symbolWithSuffix) || name.matches("\\d+")) {
            try {
                JsonNode searchRes = searchService.search(symbol);
                JsonNode results = searchRes.path("results");
                if (results.isArray() && results.size() > 0) {
                    String realName = results.get(0).path("name").asText();
                    if (!realName.isEmpty()) node.put("name", realName);
                }
            } catch (Exception ignored) {}
        }
    }
}