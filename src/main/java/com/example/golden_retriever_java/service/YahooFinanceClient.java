package com.example.golden_retriever_java.service;

import com.example.golden_retriever_java.config.AppCacheProperties;
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
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Service
public class YahooFinanceClient {

    private static final Logger log = LoggerFactory.getLogger(YahooFinanceClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient webClient;
    private final TwStockService twStockService;
    private final AppCacheProperties cacheProps;
    
    // 專屬搜尋執行緒池
    private final Executor searchExecutor = Executors.newFixedThreadPool(5);

    private String currentCookie = "";
    private String currentCrumb = "";

    private static class CacheEntry<T> {
        T data;
        long timestamp;
        CacheEntry(T data, long timestamp) {
            this.data = data;
            this.timestamp = timestamp;
        }
    }

    private final Map<String, CacheEntry<StockQuoteDto>> quoteCache = new ConcurrentHashMap<>();
    private CacheEntry<Map<String, Map<String, BigDecimal>>> ratesCache = new CacheEntry<>(new HashMap<>(), 0);
    private final Map<String, CacheEntry<List<ChartDataDto>>> chartCache = new ConcurrentHashMap<>();

    private final Random random = new Random();
    private final ZoneId TAIPEI_ZONE = ZoneId.of("Asia/Taipei");

    public YahooFinanceClient(WebClient webClient, TwStockService twStockService, AppCacheProperties cacheProps) {
        this.webClient = webClient;
        this.twStockService = twStockService;
        this.cacheProps = cacheProps;
    }

    private synchronized void ensureSession() {
        if (currentCookie.isEmpty() || currentCrumb.isEmpty()) {
            try {
                log.info("開始獲取 Yahoo API 授權 (Cookie & Crumb)...");
                ResponseEntity<Void> cookieResponse = webClient.get()
                        .uri("https://fc.yahoo.com")
                        .headers(getHeaders())
                        .exchangeToMono(response -> response.toBodilessEntity())
                        .block();

                List<String> setCookies = cookieResponse.getHeaders().get(HttpHeaders.SET_COOKIE);
                if (setCookies != null && !setCookies.isEmpty()) {
                    currentCookie = setCookies.get(0).split(";")[0];
                }

                currentCrumb = webClient.get()
                        .uri("https://query1.finance.yahoo.com/v1/test/getcrumb")
                        .headers(getHeaders())
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                log.info("Yahoo API 授權成功! 成功取得認證金鑰。");
            } catch (Exception e) {
                log.error("獲取 Yahoo 授權失敗: {}", e.getMessage());
                currentCookie = "";
                currentCrumb = "";
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.cache.clean-interval}")
    public void cleanCache() {
        long now = System.currentTimeMillis();
        quoteCache.entrySet().removeIf(entry -> (now - entry.getValue().timestamp) > cacheProps.getMaxAge());
        chartCache.entrySet().removeIf(entry -> (now - entry.getValue().timestamp) > cacheProps.getMaxAge());
        log.debug("執行快取清理完畢");
    }

    private Consumer<HttpHeaders> getHeaders() {
        List<String> agents = Arrays.asList(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36");
        return headers -> {
            headers.set(HttpHeaders.USER_AGENT, agents.get(random.nextInt(agents.size())));
            headers.set(HttpHeaders.ACCEPT_LANGUAGE, "zh-TW,zh;q=0.9,en-US;q=0.8,en;q=0.7");
            if (!currentCookie.isEmpty()) {
                headers.set(HttpHeaders.COOKIE, currentCookie);
            }
        };
    }

    public String getMarketStatus(String symbol) {
        ZonedDateTime twDate = ZonedDateTime.now(TAIPEI_ZONE);
        int day = twDate.getDayOfWeek().getValue();
        int h = twDate.getHour();
        int m = twDate.getMinute();
        boolean isTW = symbol.contains(".TW") || symbol.contains(".TWO");

        if (isTW) {
            boolean isTradingHours = (h == 9 && m >= 0) || (h >= 10 && h <= 12) || (h == 13 && m <= 30);
            return (day >= 1 && day <= 5 && isTradingHours) ? "OPEN" : "CLOSED";
        } else {
            boolean isNightTrading = (h == 21 && m >= 30) || (h >= 22 && h <= 23);
            boolean isMorningTrading = (h >= 0 && h < 5);
            boolean isTradingDay = (isNightTrading && (day >= 1 && day <= 5)) || (isMorningTrading && (day >= 2 && day <= 6));
            return isTradingDay ? "OPEN" : "CLOSED";
        }
    }

    public Map<String, Map<String, BigDecimal>> fetchExchangeRates(List<String> targetCurrencies) {
        long now = System.currentTimeMillis();
        
        boolean allCached = true;
        if (targetCurrencies != null && !ratesCache.data.isEmpty()) {
            for (String c : targetCurrencies) {
                if (!ratesCache.data.containsKey(c)) {
                    allCached = false;
                    break;
                }
            }
        } else {
            allCached = false;
        }

        if (allCached && (now - ratesCache.timestamp < cacheProps.getRatesGold())) {
            return ratesCache.data;
        }

        List<String> curList = (targetCurrencies != null && !targetCurrencies.isEmpty()) ? targetCurrencies : Collections.singletonList("USD");
        List<String> symbols = curList.stream().map(c -> c + "TWD=X").toList();
        Map<String, Map<String, BigDecimal>> results = new HashMap<>();

        try {
            ensureSession();
            String url = "https://query1.finance.yahoo.com/v7/finance/quote?symbols=" + String.join(",", symbols) + "&crumb=" + currentCrumb;
            JsonNode rootNode = webClient.get().uri(url).headers(getHeaders()).retrieve().bodyToMono(JsonNode.class).block();
            JsonNode resultNode = rootNode.path("quoteResponse").path("result");

            if (resultNode.isArray()) {
                for (JsonNode q : resultNode) {
                    String sym = q.path("symbol").asText();
                    
                    // [FIX] Yahoo 常將 USDTWD=X 正規化為 TWD=X，導致 substring(0,3) 變成 "TWD"
                    // 我們需要將其正確映射回 "USD" 以符合前端預期
                    String code = sym.substring(0, 3);
                    if ("TWD=X".equals(sym)) {
                        code = "USD";
                    }

                    Map<String, BigDecimal> data = new HashMap<>();
                    // [ENHANCED] 提高數值抓取魯棒性，優先取 regularMarketPrice，若無則嘗試 bid
                    BigDecimal price = new BigDecimal(q.path("regularMarketPrice").asText("0"));
                    if (price.compareTo(BigDecimal.ZERO) == 0 && q.has("bid")) {
                        price = new BigDecimal(q.path("bid").asText("0"));
                    }
                    
                    data.put("price", price);
                    data.put("prevClose", new BigDecimal(q.path("regularMarketPreviousClose").asText("0")));
                    results.put(code, data);
                }
            }
            ratesCache = new CacheEntry<>(results, now);
        } catch (Exception e) {
            log.error("[YahooService] 匯率抓取失敗: {}", e.getMessage());
            return ratesCache.data;
        }
        return results;
    }

    public Map<String, StockQuoteDto> fetchQuotes(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) return new HashMap<>();
        Map<String, StockQuoteDto> results = new HashMap<>();
        List<String> symbolsToFetch = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (String sym : symbols) {
            CacheEntry<StockQuoteDto> cached = quoteCache.get(sym);
            long cacheTTL = "OPEN".equals(getMarketStatus(sym)) ? cacheProps.getTradingOpen() : cacheProps.getTradingClosed();
            if (cached != null && (now - cached.timestamp < cacheTTL)) {
                results.put(sym, cached.data);
            } else {
                symbolsToFetch.add(sym);
            }
        }

        if (!symbolsToFetch.isEmpty()) {
            try {
                ensureSession();
                String url = "https://query1.finance.yahoo.com/v7/finance/quote?symbols=" + String.join(",", symbolsToFetch) + "&crumb=" + currentCrumb;
                JsonNode rootNode = webClient.get().uri(url).headers(getHeaders()).retrieve().bodyToMono(JsonNode.class).block();
                JsonNode resultNode = rootNode.path("quoteResponse").path("result");

                if (resultNode.isArray()) {
                    for (JsonNode q : resultNode) {
                        String sym = q.path("symbol").asText();
                        StockQuoteDto data = StockQuoteDto.builder()
                                .price(new BigDecimal(q.path("regularMarketPrice").asText("0")))
                                .prevClose(new BigDecimal(q.path("regularMarketPreviousClose").asText("0")))
                                .high(new BigDecimal(q.path("regularMarketDayHigh").asText(q.path("dayHigh").asText("0"))))
                                .low(new BigDecimal(q.path("regularMarketDayLow").asText(q.path("dayLow").asText("0"))))
                                .fiftyTwoWeekHigh(new BigDecimal(q.path("fiftyTwoWeekHigh").asText("0")))
                                .fiftyTwoWeekLow(new BigDecimal(q.path("fiftyTwoWeekLow").asText("0")))
                                .volume(q.path("regularMarketVolume").asLong(0))
                                .avgVolume(q.path("averageDailyVolume10Day").asLong(0))
                                .currency(q.path("currency").asText())
                                .postMarketPrice(q.has("postMarketPrice") ? new BigDecimal(q.path("postMarketPrice").asText()) : null)
                                .build();
                        results.put(sym, data);
                        quoteCache.put(sym, new CacheEntry<>(data, now));
                    }
                }
            } catch (Exception e) {
                log.error("[YahooService] 股價抓取異常: {}", e.getMessage());
            }
        }
        results.forEach((sym, data) -> data.setMarketStatus(getMarketStatus(sym)));
        return results;
    }

    public List<ChartDataDto> fetchChartData(String symbol, String period) {
        long startReq = System.currentTimeMillis();
        String cacheKey = symbol + "_" + period;
        CacheEntry<List<ChartDataDto>> cached = chartCache.get(cacheKey);
        if (cached != null && (System.currentTimeMillis() - cached.timestamp < cacheProps.getChart())) {
            return cached.data;
        }

        try {
            ensureSession();
            long endTs = Instant.now().getEpochSecond();
            long startTs;
            String interval = "1d";
            if ("1w".equals(period) || "1wk".equals(period)) {
                startTs = ZonedDateTime.now(TAIPEI_ZONE).minusYears(2).toEpochSecond();
                interval = "1wk";
            } else if ("1m".equals(period) || "1mo".equals(period)) {
                startTs = ZonedDateTime.now(TAIPEI_ZONE).minusYears(5).toEpochSecond();
                interval = "1mo";
            } else {
                startTs = ZonedDateTime.now(TAIPEI_ZONE).minusMonths(6).toEpochSecond();
                interval = "1d";
            }

            String url = String.format("https://query1.finance.yahoo.com/v8/finance/chart/%s?period1=%d&period2=%d&interval=%s&crumb=%s",
                    symbol, startTs, endTs, interval, currentCrumb);
            
            JsonNode rootNode = webClient.get().uri(url).headers(getHeaders()).retrieve().bodyToMono(JsonNode.class).block();
            JsonNode resultNode = rootNode.path("chart").path("result").get(0);
            if (resultNode == null || resultNode.isMissingNode()) throw new RuntimeException("查無歷史數據");

            JsonNode timestamps = resultNode.path("timestamp");
            JsonNode quote = resultNode.path("indicators").path("quote").get(0);
            List<ChartDataDto> formattedData = new ArrayList<>();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(TAIPEI_ZONE);

            if (timestamps.isArray()) {
                for (int i = 0; i < timestamps.size(); i++) {
                    JsonNode openNode = quote.path("open").get(i);
                    if (!openNode.isNull()) {
                        formattedData.add(new ChartDataDto(
                                formatter.format(Instant.ofEpochSecond(timestamps.get(i).asLong())),
                                new BigDecimal(openNode.asText()),
                                new BigDecimal(quote.path("high").get(i).asText()),
                                new BigDecimal(quote.path("low").get(i).asText()),
                                new BigDecimal(quote.path("close").get(i).asText())));
                    }
                }
            }
            formattedData.sort(Comparator.comparing(ChartDataDto::getTime));
            chartCache.put(cacheKey, new CacheEntry<>(formattedData, System.currentTimeMillis()));
            log.info("[Performance] {} Chart loaded in {}ms", symbol, (System.currentTimeMillis() - startReq));
            return formattedData;
        } catch (Exception e) {
            throw new RuntimeException("無法載入 K 線圖資料: " + e.getMessage());
        }
    }

    public JsonNode search(String query) {
        if (query == null || query.trim().isEmpty()) return null;
        String q = query.trim();
        boolean isTwQuery = q.matches(".*[\\u4e00-\\u9fa5].*") || q.matches("^\\d+$") || q.toUpperCase().matches("^\\d+\\.TW[O]?$");

        // 1. 同時啟動雙重搜尋
        CompletableFuture<JsonNode> twseFuture = isTwQuery 
            ? CompletableFuture.supplyAsync(() -> twStockService.searchTwse(q), searchExecutor)
            : CompletableFuture.completedFuture(null);

        CompletableFuture<JsonNode> yahooFuture = CompletableFuture.supplyAsync(() -> {
            try {
                String encodedQuery = java.net.URLEncoder.encode(q, java.nio.charset.StandardCharsets.UTF_8.toString());
                String url = "https://query2.finance.yahoo.com/v1/finance/search?q=" + encodedQuery;
                return webClient.get().uri(url)
                        .header("User-Agent", "Mozilla/5.0")
                        .header("Referer", "https://finance.yahoo.com/")
                        .retrieve().bodyToMono(JsonNode.class)
                        .timeout(java.time.Duration.ofMillis(2000)) // Yahoo 限時 2 秒
                        .block();
            } catch (Exception e) { return null; }
        }, searchExecutor);

        try {
            // [關鍵優化]：使用競爭機制
            // 先等最多 1.2 秒，如果任何一方回來了且結果不錯，就直接用
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < 4000) {
                // 如果是台股關鍵字，優先看證交所是否已經回來
                if (isTwQuery && twseFuture.isDone()) {
                    JsonNode r = twseFuture.get();
                    if (r != null && r.has("quotes") && r.path("quotes").size() > 0) return r;
                }
                
                // 如果 Yahoo 回來了，且已經包含足夠資訊，直接回傳 (不再等證交所)
                if (yahooFuture.isDone()) {
                    JsonNode r = yahooFuture.get();
                    if (r != null && r.has("quotes") && r.path("quotes").size() > 0) return r;
                }
                
                // 每 100ms 巡檢一次
                Thread.sleep(100);
            }
            
            // 超過 4 秒後，採取保底策略
            JsonNode fallback = yahooFuture.getNow(twseFuture.getNow(null));
            return fallback != null ? fallback : objectMapper.createObjectNode();
        } catch (Exception e) {
            log.warn("[Search] 搜尋競爭等待異常: {}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }
}