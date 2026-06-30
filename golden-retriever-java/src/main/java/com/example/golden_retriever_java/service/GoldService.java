package com.example.golden_retriever_java.service;

import com.example.golden_retriever_java.config.AppCacheProperties;
import com.example.golden_retriever_java.dto.GoldPriceDto;
import com.example.golden_retriever_java.dto.StockQuoteDto;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;

@Service
public class GoldService {
    private static final Logger log = LoggerFactory.getLogger(GoldService.class);
    private final YahooFinanceClient yahooClient;
    private final WebClient webClient;
    private final AppCacheProperties cacheProps;

    private GoldPriceDto cachedGold;
    private long lastFetchTime = 0;

    public GoldService(YahooFinanceClient yahooClient, WebClient webClient, AppCacheProperties cacheProps) {
        this.yahooClient = yahooClient;
        this.webClient = webClient;
        this.cacheProps = cacheProps;
    }

    public GoldPriceDto fetchRealGoldPrice() {
        long now = System.currentTimeMillis();
        if (cachedGold != null && (now - lastFetchTime < cacheProps.getRatesGold())) {
            return cachedGold;
        }

        BigDecimal current = BigDecimal.ZERO;
        BigDecimal high = BigDecimal.ZERO;
        BigDecimal trendDiff = BigDecimal.ZERO;
        boolean isFallback = false;

        // 1. 台銀首頁即時報價 (Jsoup 爬蟲)
        try {
            String html = webClient.get()
                    .uri("https://rate.bot.com.tw/gold?Lang=zh-TW")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            if (html != null) {
                Document doc = Jsoup.parse(html);
                // [FIX] 從 Debug 日誌看，'1公克' 與 '黃金存摺' 被拆分到了不同 Row
                // 我們直接搜尋包含 '黃金存摺' 的 row 並提取數字
                var rows = doc.select("tr");
                for (var row : rows) {
                    String rowText = row.text();
                    if (rowText.contains("黃金存摺")) {
                        // 嘗試從 td.text-right 提取
                        var cells = row.select("td.text-right");
                        String targetVal = "";
                        
                        if (!cells.isEmpty()) {
                            targetVal = cells.get(0).text(); // 通常第一個 text-right 是賣出
                        } else {
                            // 如果 td 沒有 class，嘗試用正規則表達式從整行文字抓取數字
                            var matcher = java.util.regex.Pattern.compile("([0-9,]{4,6})").matcher(rowText);
                            if (matcher.find()) {
                                targetVal = matcher.group(1);
                            }
                        }

                        if (!targetVal.isEmpty()) {
                            String cleanVal = targetVal.replaceAll("[^0-9.]", "");
                            if (!cleanVal.isEmpty()) {
                                current = new BigDecimal(cleanVal);
                                log.info("[GoldService] 台銀報價成功 (行匹配) - 偵測到: {}", current);
                                break;
                            }
                        }
                    }
                }
                
                if (current.compareTo(BigDecimal.ZERO) <= 0) {
                    log.warn("[GoldService] 台銀解析失敗，HTML 結構可能變動。長度: {}", html.length());
                }
            }
        } catch (Exception e) {
            log.warn("[GoldService] 台銀爬蟲失敗 (原因: {})，將改用 Yahoo 備援邏輯", e.getMessage());
        }

        // 2. 備援邏輯 (Yahoo GC=F 期貨換算)
        if (current.compareTo(BigDecimal.ZERO) <= 0) {
            try {
                isFallback = true;
                Map<String, StockQuoteDto> quotes = yahooClient.fetchQuotes(Collections.singletonList("GC=F"));
                StockQuoteDto goldQuote = quotes.get("GC=F");
                Map<String, Map<String, BigDecimal>> rates = yahooClient.fetchExchangeRates(Collections.singletonList("USD"));
                BigDecimal rate = rates.get("USD").get("price");

                if (goldQuote != null && rate != null) {
                    // 換算公式: (美金現價 * 匯率) / 31.1034768 (每盎司公克數)
                    BigDecimal troyOunce = new BigDecimal("31.1034768");
                    current = goldQuote.getPrice().multiply(rate).divide(troyOunce, 0, RoundingMode.HALF_UP);
                    
                    BigDecimal prevGoldTwd = goldQuote.getPrevClose().multiply(rate).divide(troyOunce, 0, RoundingMode.HALF_UP);
                    trendDiff = current.subtract(prevGoldTwd);
                    log.info("[GoldService] Yahoo 備援換算成功: {}", current);
                }
            } catch (Exception e) {
                log.error("[GoldService] 所有黃金來源皆失敗: {}", e.getMessage());
            }
        }

        // 構建 DTO
        GoldPriceDto dto = GoldPriceDto.builder()
                .symbol("黃金存摺 (TWD/g)")
                .current(current)
                .priceTWD(current) // [FIX] 同步賦值給 priceTWD，解決前端讀取不到數字的問題
                .high(current.compareTo(high) > 0 ? current : high)
                .prevClose(current.subtract(trendDiff))
                .isFallback(isFallback)
                .updateTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .build();

        if (current.compareTo(BigDecimal.ZERO) > 0) {
            this.cachedGold = dto;
            this.lastFetchTime = now;
        }
        return dto;
    }
}
