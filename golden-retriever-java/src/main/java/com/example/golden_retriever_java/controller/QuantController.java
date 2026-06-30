package com.example.golden_retriever_java.controller;

import com.example.golden_retriever_java.service.QuantService;
import com.example.golden_retriever_java.service.TwStockService;
import com.example.golden_retriever_java.service.DataService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/quant")
public class QuantController {

    private static final Logger log = LoggerFactory.getLogger(QuantController.class);

    @Autowired
    private QuantService quantService;

    @Autowired
    private TwStockService twStockService;

    @Autowired
    private DataService db;

    @GetMapping("/top-stocks")
    public JsonNode getTopStocks(
            @RequestParam(defaultValue = "10") int count,
            @RequestParam(defaultValue = "1000000") long capital) {
        return quantService.getTopStocks(count, capital, null);
    }

    @GetMapping("/diagnose")
    public JsonNode diagnoseStock(@RequestParam("symbol") String symbol) {
        // 1. 執行 AI 診斷 (長線指標)
        JsonNode diag = quantService.diagnoseStock(symbol);
        
        // 2. 獲取即時內外盤力道 (短線指標)
        try {
            String fugleKey = db.getConfig("FUGLE_API_KEY", "");
            Map<String, Object> strength = twStockService.fetchIntradayStrength(symbol, fugleKey);
            
            if (diag instanceof ObjectNode) {
                ObjectNode node = (ObjectNode) diag;
                node.putPOJO("intraday_strength", strength);
            }
        } catch (Exception e) {
            log.warn("[Quant Controller] 合併力道數據失敗: {}", e.getMessage());
        }
        
        return diag;
    }
}