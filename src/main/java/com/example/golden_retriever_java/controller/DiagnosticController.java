package com.example.golden_retriever_java.controller;

import com.example.golden_retriever_java.dto.DashboardResponseDto;
import com.example.golden_retriever_java.service.DataService;
import com.example.golden_retriever_java.service.GeminiAiService;
import com.example.golden_retriever_java.service.RiskAnalysisService;
import com.example.golden_retriever_java.service.TwStockService;
import com.example.golden_retriever_java.service.YahooFinanceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/diagnostic")
public class DiagnosticController {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticController.class);

    private final RiskAnalysisService riskService;
    private final GeminiAiService aiService;
    private final YahooFinanceClient yahooClient;
    private final DataService dataService;
    private final com.example.golden_retriever_java.repository.ConfigRepository configRepo;
    private final TwStockService twStockService;

    public DiagnosticController(RiskAnalysisService riskService, GeminiAiService aiService, YahooFinanceClient yahooClient, DataService dataService, com.example.golden_retriever_java.repository.ConfigRepository configRepo, TwStockService twStockService) {
        this.riskService = riskService;
        this.aiService = aiService;
        this.yahooClient = yahooClient;
        this.dataService = dataService;
        this.configRepo = configRepo;
        this.twStockService = twStockService;
    }

    @GetMapping("/sync-tw-data")
    public ResponseEntity<Map<String, Object>> forceSyncTwData() {
        Map<String, Object> result = new HashMap<>();
        try {
            twStockService.refreshNavsFromApi();
            result.put("status", "success");
            result.put("message", "台股數據同步觸發成功，請檢查日誌獲取具體進度。");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[Diagnostic] 同步台股數據失敗: {}", e.getMessage(), e);
            result.put("status", "error");
            result.put("message", "內部服務器錯誤，請檢查系統日誌。");
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @GetMapping("/data")
    public ResponseEntity<Map<String, Object>> getDiagnosticData() {
        Map<String, Object> report = new HashMap<>();
        var userData = dataService.getData();
        report.put("stockCount", userData.getStocks().size());
        report.put("inventoryCount", userData.getInventory().size());
        report.put("stocks", userData.getStocks());
        report.put("inventory", userData.getInventory());
        report.put("fugleKeyExists", configRepo.findByKey("FUGLE_API_KEY").isPresent());
        
        try {
            var rates = yahooClient.fetchExchangeRates(List.of("USD", "TWD"));
            report.put("rates", rates);
        } catch (Exception e) {
            report.put("ratesError", e.getMessage());
        }
        
        return ResponseEntity.ok(report);
    }

    @GetMapping("/full-report")
    public ResponseEntity<Map<String, Object>> getFullReport(@RequestParam("symbol") String symbol) {
        long start = System.currentTimeMillis();
        try {
            // 1. 獲取現價
            var quotes = yahooClient.fetchQuotes(Collections.singletonList(symbol));
            if (!quotes.containsKey(symbol)) {
                log.error("[Diagnostic] 找不到標的報價: {}", symbol);
                return ResponseEntity.notFound().build();
            }
            var quote = quotes.get(symbol);

            // 2. 風險引擎計算 (HV, VaR, BS)
            var riskReport = riskService.analyze(symbol, quote.getPrice());
            if (riskReport == null) {
                log.error("[Diagnostic] 分析失敗 (可能數據不足): {}", symbol);
                return ResponseEntity.badRequest().body(Map.of("error", "歷史數據不足，無法生成診斷"));
            }

            // 3. 組合回傳數據 (基礎數據)
            Map<String, Object> result = new HashMap<>();
            result.put("symbol", symbol);
            result.put("price", quote.getPrice());
            result.put("risk", riskReport);
            
            log.info("[Diagnostic] 完成請求 {} 耗時: {}ms", symbol, (System.currentTimeMillis() - start));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[Diagnostic] 發生異常: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "內部服務器錯誤，請檢查系統日誌。"));
        }
    }

    @PostMapping("/ask")
    public ResponseEntity<Map<String, String>> askConsultant(@RequestBody Map<String, Object> req) {
        String symbol = (String) req.get("symbol");
        String question = (String) req.get("question");
        
        List<Map<String, String>> history = new ArrayList<>();
        Object rawHistory = req.get("history");
        if (rawHistory instanceof List) {
            for (Object item : (List<?>) rawHistory) {
                if (item instanceof Map) {
                    Map<String, String> map = new HashMap<>();
                    ((Map<?, ?>) item).forEach((k, v) -> map.put(String.valueOf(k), String.valueOf(v)));
                    history.add(map);
                }
            }
        }
        
        String answer = aiService.askConsultant(symbol, question, history);
        return ResponseEntity.ok(Map.of("answer", answer));
    }
}
