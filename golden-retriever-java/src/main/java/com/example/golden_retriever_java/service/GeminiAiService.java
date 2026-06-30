package com.example.golden_retriever_java.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.*;

@Service
public class GeminiAiService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final DataService db;

    public GeminiAiService(WebClient webClient, ObjectMapper objectMapper, DataService db) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.db = db;
    }

    public Map<String, Object> getAiInsight(String symbol, RiskAnalysisService.RiskReport risk, Object portfolioData) {
        try {
            String apiKey = db.getConfig("GEMINI_API_KEY", "");
            if (apiKey.isEmpty()) return Collections.singletonMap("error", "尚未設定 GEMINI_API_KEY");

            String systemPrompt = String.format(
                "你是一位資深投資顧問與風險精算師。請根據以下數據進行個股診斷。\n" +
                "標的: %s\n" +
                "歷史波動率 (HV20/60/120): %.1f%%, %.1f%%, %.1f%%\n" +
                "VaR 風險值 (95%% 單日): %.2f%%\n" +
                "BS 理論價格區間 (30天): %.2f - %.2f\n" +
                "用戶持倉狀態: %s\n\n" +
                "請針對 1.集中度 2.技術位置 3.匯率風險 4.防禦性 進行分析。\n" +
                "回傳格式必須為純 JSON 如下，嚴禁任何 Markdown 標籤或額外文字：\n" +
                "{\"score\": 總分, \"insight\": \"簡短白話解讀\", \"details\": [\"問題1\", \"問題2\", ...]}",
                symbol, risk.hv20(), risk.hv60(), risk.hv120(), risk.dailyVaR95(), risk.lowerBond(), risk.upperBond(),
                objectMapper.writeValueAsString(portfolioData)
            );

            Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", systemPrompt)))),
                "generationConfig", Map.of("response_mime_type", "application/json")
            );

            String response = webClient.post()
                .uri("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            JsonNode root = objectMapper.readTree(response);
            String content = root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
            
            return objectMapper.readValue(content, Map.class);
        } catch (Exception e) {
            return Collections.singletonMap("error", "AI 呼叫失敗: " + e.getMessage());
        }
    }

    public String askConsultant(String symbol, String question, List<Map<String, String>> history) {
        try {
            String apiKey = db.getConfig("GEMINI_API_KEY", "");
            if (apiKey.isEmpty()) return "請先在設定中配置 GEMINI_API_KEY。";

            // 組合簡單的對話 Body
            // 這裡可以擴充為包含 Context 的對話
            Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", "標的: " + symbol + "\n問題: " + question))))
            );

            String response = webClient.post()
                .uri("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            JsonNode root = objectMapper.readTree(response);
            return root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
        } catch (Exception e) {
            return "顧問暫時離線: " + e.getMessage();
        }
    }
}
