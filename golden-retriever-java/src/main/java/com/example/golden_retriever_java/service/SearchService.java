package com.example.golden_retriever_java.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SearchService {
    private static final Logger log = LoggerFactory.getLogger(SearchService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private YahooFinanceClient yahooClient;

    @Autowired
    private TwStockService twStockService;

    /**
     * 整合式搜尋：自動判斷並調用對應的搜尋引擎
     */
    public JsonNode search(String query) {
        ObjectNode response = objectMapper.createObjectNode();
        ArrayNode results = response.putArray("results");

        try {
            // 1. 同時從 Yahoo 抓取 (包含美股與台股)
            JsonNode yahooData = yahooClient.search(query);
            if (yahooData.has("quotes")) {
                for (JsonNode q : yahooData.path("quotes")) {
                    ObjectNode item = results.addObject();
                    item.put("symbol", q.path("symbol").asText());
                    item.put("name", q.path("shortname").asText(q.path("longname").asText("")));
                    item.put("exch", q.path("exchDisp").asText(""));
                }
            }
            
            log.info("[Search] 搜尋關鍵字: {}, 獲取結果數: {}", query, results.size());
        } catch (Exception e) {
            log.error("[Search] 搜尋失敗: {}", e.getMessage());
        }

        return response;
    }
}