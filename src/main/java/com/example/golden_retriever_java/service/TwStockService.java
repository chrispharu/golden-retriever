package com.example.golden_retriever_java.service;

import com.example.golden_retriever_java.dto.ChartDataDto;
import com.example.golden_retriever_java.dto.StockQuoteDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class TwStockService {
    private static final Logger log = LoggerFactory.getLogger(TwStockService.class);
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==========================================
    // 📊 API 額度監控
    // ==========================================
    private final AtomicInteger remainingQuota = new AtomicInteger(-1);

    public int getRemainingQuota() {
        return remainingQuota.get();
    }

    private void updateQuota(HttpHeaders headers) {
        if (headers == null) return;
        String remaining = headers.getFirst("X-RateLimit-Remaining");
        if (remaining != null) {
            try {
                int val = Integer.parseInt(remaining);
                remainingQuota.set(val);
                log.info("[Fugle Quota] 剩餘額度: {}", val);
            } catch (Exception ignored) {}
        }
    }

    // ==========================================
    // 🚀 圖表快取系統
    // ==========================================
    private static class CacheEntry<T> {
        final T data;
        final long timestamp;
        CacheEntry(T data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private final Map<String, CacheEntry<List<ChartDataDto>>> chartCache = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION = 3600000; // 快取 1 小時

    public TwStockService(WebClient webClient) {
        this.webClient = webClient;
    }

    private String resolveFugleKey(String rawKey) {
        if (rawKey == null) return "";
        return rawKey.trim();
    }

    public Map<String, StockQuoteDto> fetchFugleQuotes(List<String> symbols, String fugleKey) {
        Map<String, StockQuoteDto> results = new HashMap<>();
        String effectiveKey = resolveFugleKey(fugleKey);
        if (effectiveKey.isEmpty()) return results;

        for (String symbol : symbols) {
            try {
                String rawSymbol = symbol.split("\\.")[0];
                String url = "https://api.fugle.tw/marketdata/v1.0/stock/intraday/quote/" + rawSymbol;

                ResponseEntity<JsonNode> response = webClient.get()
                        .uri(url)
                        .header("X-API-KEY", effectiveKey)
                        .header("User-Agent", "Mozilla/5.0")
                        .retrieve()
                        .toEntity(JsonNode.class)
                        .block();

                if (response != null) {
                    updateQuota(response.getHeaders());
                    JsonNode node = response.getBody();
                    if (node != null) {
                        BigDecimal lastPrice = new BigDecimal(node.path("lastPrice").asText("0"));
                        if (lastPrice.compareTo(BigDecimal.ZERO) == 0) lastPrice = new BigDecimal(node.path("closePrice").asText("0"));
                        if (lastPrice.compareTo(BigDecimal.ZERO) == 0) lastPrice = new BigDecimal(node.path("previousClose").asText("0"));

                        StockQuoteDto dto = StockQuoteDto.builder()
                                .price(lastPrice)
                                .prevClose(new BigDecimal(node.path("previousClose").asText("0")))
                                .high(new BigDecimal(node.path("highPrice").asText("0")))
                                .low(new BigDecimal(node.path("lowPrice").asText("0")))
                                .volume(node.path("tradingVolume").asLong(0))
                                .currency("TWD")
                                .marketStatus(node.path("isClose").asBoolean(false) ? "CLOSED" : "OPEN")
                                .build();

                        // 台股額外獲取 52 週數據 (透過歷史 K 線計算)
                        List<ChartDataDto> yearData = fetchFugleChart(symbol, effectiveKey, "1y");
                        if (yearData != null && !yearData.isEmpty()) {
                            BigDecimal yHigh = yearData.stream().map(ChartDataDto::getHigh).max(BigDecimal::compareTo).orElse(lastPrice);
                            BigDecimal yLow = yearData.stream().map(ChartDataDto::getLow).min(BigDecimal::compareTo).orElse(lastPrice);
                            dto.setFiftyTwoWeekHigh(yHigh);
                            dto.setFiftyTwoWeekLow(yLow);
                            
                            long avgVol = (long) yearData.stream().mapToLong(d -> 0L).average().orElse(0); // 這裡簡化，原 DTO 沒存每日量
                            dto.setAvgVolume(avgVol);
                        }

                        results.put(symbol, dto);
                    }
                }
            } catch (Exception e) {
                log.error("[Fugle Quotes] {} 抓取失敗 | 原因: {}", symbol, e.getMessage());
            }
        }
        return results;
    }

    public List<ChartDataDto> fetchFugleChart(String symbol, String fugleKey, String period) {
        String cacheKey = "FUGLE_" + symbol + "_" + period;
        CacheEntry<List<ChartDataDto>> cached = chartCache.get(cacheKey);
        if (cached != null && (System.currentTimeMillis() - cached.timestamp < CACHE_DURATION)) {
            return cached.data;
        }

        List<ChartDataDto> chartData = new ArrayList<>();
        String effectiveKey = resolveFugleKey(fugleKey);
        if (effectiveKey.isEmpty()) return chartData;

        try {
            String rawSymbol = symbol.split("\\.")[0];
            String fromDate;
            if ("1wk".equals(period) || "1w".equals(period)) {
                fromDate = LocalDate.now().minusYears(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            } else if ("1mo".equals(period) || "1m".equals(period)) {
                fromDate = LocalDate.now().minusYears(5).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            } else {
                fromDate = LocalDate.now().minusMonths(6).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            }
            
            String toDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String url = "https://api.fugle.tw/marketdata/v1.0/stock/historical/candles/1d?symbol=" + rawSymbol + "&from=" + fromDate + "&to=" + toDate;

            ResponseEntity<String> response = webClient.get()
                    .uri(url)
                    .header("X-API-KEY", effectiveKey)
                    .header("User-Agent", "Mozilla/5.0")
                    .retrieve()
                    .toEntity(String.class).block();

            if (response != null) {
                updateQuota(response.getHeaders());
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode candles = root.path("data");
                
                List<ChartDataDto> dailyData = new ArrayList<>();
                if (candles.isArray()) {
                    for (JsonNode c : candles) {
                        dailyData.add(new ChartDataDto(
                                c.path("date").asText(),
                                new BigDecimal(c.path("open").asText()),
                                new BigDecimal(c.path("high").asText()),
                                new BigDecimal(c.path("low").asText()),
                                new BigDecimal(c.path("close").asText())));
                    }
                }
                dailyData.sort(Comparator.comparing(ChartDataDto::getTime));

                if ("1w".equals(period) || "1wk".equals(period)) {
                    chartData = aggregateByPeriod(dailyData, "WEEK");
                } else if ("1m".equals(period) || "1mo".equals(period)) {
                    chartData = aggregateByPeriod(dailyData, "MONTH");
                } else {
                    chartData = dailyData;
                }
                
                if (!chartData.isEmpty()) {
                    chartCache.put(cacheKey, new CacheEntry<>(chartData));
                }
            }
        } catch (Exception e) {
            log.error("[Fugle Chart] 圖表抓取失敗: {}", e.getMessage());
        }
        return chartData;
    }

    private List<ChartDataDto> aggregateByPeriod(List<ChartDataDto> dailyData, String type) {
        if (dailyData == null || dailyData.isEmpty()) return new ArrayList<>();
        List<ChartDataDto> aggregated = new ArrayList<>();
        ChartDataDto currentBar = null;
        String currentKey = "";
        DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (ChartDataDto day : dailyData) {
            LocalDate date = LocalDate.parse(day.getTime(), dayFormatter);
            String key = "WEEK".equals(type) ? date.with(java.time.DayOfWeek.MONDAY).toString() : date.withDayOfMonth(1).toString();

            if (!key.equals(currentKey)) {
                if (currentBar != null) aggregated.add(currentBar);
                currentBar = new ChartDataDto(key, day.getOpen(), day.getHigh(), day.getLow(), day.getClose());
                currentKey = key;
            } else {
                if (day.getHigh().compareTo(currentBar.getHigh()) > 0) currentBar.setHigh(day.getHigh());
                if (day.getLow().compareTo(currentBar.getLow()) < 0) currentBar.setLow(day.getLow());
                currentBar.setClose(day.getClose());
            }
        }
        if (currentBar != null) aggregated.add(currentBar);
        return aggregated;
    }

    public List<ChartDataDto> fetchTwseMonthChart(String symbol) {
        String cacheKey = "TWSE_" + symbol;
        CacheEntry<List<ChartDataDto>> cached = chartCache.get(cacheKey);
        if (cached != null && (System.currentTimeMillis() - cached.timestamp < CACHE_DURATION)) {
            return cached.data;
        }

        List<ChartDataDto> chartData = new ArrayList<>();
        try {
            String rawSymbol = symbol.split("\\.")[0];
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String url = "https://www.twse.com.tw/exchangeReport/STOCK_DAY?response=json&date=" + dateStr + "&stockNo=" + rawSymbol;
            
            JsonNode root = webClient.get()
                    .uri(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(java.time.Duration.ofSeconds(5))
                    .block();
            
            if (root != null) {
                JsonNode dataNode = root.path("data");
                if (dataNode.isArray()) {
                    for (JsonNode day : dataNode) {
                        String rocDate = day.get(0).asText();
                        String[] parts = rocDate.split("/");
                        int year = Integer.parseInt(parts[0]) + 1911;
                        chartData.add(new ChartDataDto(year + "-" + parts[1] + "-" + parts[2],
                                new BigDecimal(day.get(3).asText().replace(",", "")),
                                new BigDecimal(day.get(4).asText().replace(",", "")),
                                new BigDecimal(day.get(5).asText().replace(",", "")),
                                new BigDecimal(day.get(6).asText().replace(",", ""))));
                    }
                }
            }
            if (!chartData.isEmpty()) chartCache.put(cacheKey, new CacheEntry<>(chartData));
        } catch (Exception e) { 
            log.error("[TWSE] 抓取失敗: {}", e.getMessage()); 
        }
        return chartData;
    }

    @Scheduled(fixedRate = 1800000)
    public void cleanCache() {
        long now = System.currentTimeMillis();
        chartCache.entrySet().removeIf(entry -> now - entry.getValue().timestamp > CACHE_DURATION);
    }

    private final Map<String, CacheEntry<JsonNode>> searchCache = new ConcurrentHashMap<>();
    private static final long SEARCH_CACHE_DURATION = 600000;

    public JsonNode searchTwse(String query) {
        if (query == null || query.trim().isEmpty()) return objectMapper.createObjectNode();
        String q = query.trim();
        CacheEntry<JsonNode> cached = searchCache.get(q);
        if (cached != null && (System.currentTimeMillis() - cached.timestamp < SEARCH_CACHE_DURATION)) return cached.data;

        try {
            String cleanQuery = q.replaceAll("(?i)\\.TW[O]?$", "");
            String encodedQuery = java.net.URLEncoder.encode(cleanQuery, java.nio.charset.StandardCharsets.UTF_8.toString());
            String url = "https://mis.twse.com.tw/stock/api/getStockNames.jsp?n=" + encodedQuery + "&lang=zh_tw";
            
            String res = webClient.get()
                    .uri(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofMillis(1500))
                    .block();

            if (res != null && !res.isEmpty() && res.trim().startsWith("{")) {
                JsonNode root = objectMapper.readTree(res);
                if (root.has("datas") && root.path("datas").isArray()) {
                    var quotes = objectMapper.createArrayNode();
                    for (JsonNode item : root.path("datas")) {
                        var node = objectMapper.createObjectNode();
                        String suffix = "otc".equals(item.path("t").asText()) ? ".TWO" : ".TW";
                        node.put("symbol", item.path("c").asText() + suffix);
                        node.put("shortname", item.path("n").asText());
                        node.put("longname", item.path("n").asText());
                        node.put("exchange", suffix.substring(1));
                        quotes.add(node);
                    }
                    var result = objectMapper.createObjectNode();
                    result.set("quotes", quotes);
                    searchCache.put(q, new CacheEntry<>(result));
                    return result;
                }
            }
        } catch (Exception ignored) {}
        return objectMapper.createObjectNode();
    }

    @Scheduled(fixedRate = 3600000)
    public void cleanSearchCache() {
        long now = System.currentTimeMillis();
        searchCache.entrySet().removeIf(entry -> now - entry.getValue().timestamp > SEARCH_CACHE_DURATION);
    }

    /**
     * 抓取盤中內外盤力道
     */
    public Map<String, Object> fetchIntradayStrength(String symbol, String fugleKey) {
        Map<String, Object> result = new HashMap<>();
        String effectiveKey = resolveFugleKey(fugleKey);
        if (effectiveKey.isEmpty()) return result;

        try {
            String rawSymbol = symbol.split("\\.")[0];
            String url = "https://api.fugle.tw/marketdata/v1.0/stock/intraday/volumes/" + rawSymbol;

            ResponseEntity<JsonNode> response = webClient.get()
                    .uri(url)
                    .header("X-API-KEY", effectiveKey)
                    .header("User-Agent", "Mozilla/5.0")
                    .retrieve()
                    .toEntity(JsonNode.class)
                    .block();

            if (response != null && response.getBody() != null) {
                updateQuota(response.getHeaders());
                JsonNode volumes = response.getBody().path("volumes");
                long buyVol = 0;
                long sellVol = 0;

                if (volumes.isArray()) {
                    BigDecimal maxVol = BigDecimal.ZERO;
                    BigDecimal densePrice = BigDecimal.ZERO;

                    for (JsonNode v : volumes) {
                        long b = v.path("buyVolume").asLong(0);
                        long s = v.path("sellVolume").asLong(0);
                        buyVol += b;
                        sellVol += s;

                        BigDecimal totalVol = new BigDecimal(b + s);
                        if (totalVol.compareTo(maxVol) > 0) {
                            maxVol = totalVol;
                            densePrice = new BigDecimal(v.path("price").asText("0"));
                        }
                    }
                    result.put("dense_price", densePrice);
                    result.put("dense_vol_shares", maxVol);
                }

                long total = buyVol + sellVol;
                double buyRatio = total > 0 ? (double) buyVol / total * 100 : 50.0;
                
                result.put("buy_ratio", Math.round(buyRatio * 10.0) / 10.0);
                result.put("buy_vol", buyVol);
                result.put("sell_vol", sellVol);
                result.put("strength_msg", buyRatio > 55 ? "多頭主導" : (buyRatio < 45 ? "空頭主導" : "多空拉鋸"));
            }
        } catch (Exception e) {
            log.error("[Fugle Strength] {} 獲取失敗: {}", symbol, e.getMessage());
        }
        return result;
    }
}