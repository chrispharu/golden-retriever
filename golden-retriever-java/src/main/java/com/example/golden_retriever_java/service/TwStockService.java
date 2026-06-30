package com.example.golden_retriever_java.service;

import com.example.golden_retriever_java.dto.ChartDataDto;
import com.example.golden_retriever_java.dto.StockQuoteDto;
import com.example.golden_retriever_java.entity.ConfigEntity;
import com.example.golden_retriever_java.entity.StockEntity;
import com.example.golden_retriever_java.repository.ConfigRepository;
import com.example.golden_retriever_java.repository.StockRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class TwStockService {
    private static final Logger log = LoggerFactory.getLogger(TwStockService.class);
    private final WebClient webClient;
    private final StockRepository stockRepository;
    private final ConfigRepository configRepository;
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
            } catch (Exception e) {
                log.warn("[Fugle Quota] 解析額度失敗: {}", e.getMessage());
            }
        }
    }

    // ==========================================
    // 🚀 快取系統
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
    private static final long CACHE_DURATION = 3600000;

    private final Map<String, CacheEntry<JsonNode>> searchCache = new ConcurrentHashMap<>();
    private static final long SEARCH_CACHE_DURATION = 600000;

    public TwStockService(WebClient webClient, StockRepository stockRepository, ConfigRepository configRepository) {
        this.webClient = webClient;
        this.stockRepository = stockRepository;
        this.configRepository = configRepository;
    }

    private String resolveFugleKey(String rawKey) {
        if (rawKey == null || rawKey.trim().isEmpty()) return "";
        return rawKey.trim();
    }

    // ==========================================
    // 🔍 股票搜尋 (TWSE)
    // ==========================================
    public JsonNode searchTwse(String query) {
        if (query == null || query.trim().isEmpty()) return objectMapper.createObjectNode();
        String q = query.trim();
        CacheEntry<JsonNode> cached = searchCache.get(q);
        if (cached != null && (System.currentTimeMillis() - cached.timestamp < SEARCH_CACHE_DURATION)) return cached.data;

        log.info("[Search] 執行 TWSE 搜尋: {}", q);
        try {
            String cleanQuery = q.replaceAll("(?i)\\.TW[O]?$", "");
            String encodedQuery = java.net.URLEncoder.encode(cleanQuery, java.nio.charset.StandardCharsets.UTF_8.toString());
            String url = "https://mis.twse.com.tw/stock/api/getStockNames.jsp?n=" + encodedQuery + "&lang=zh_tw";
            
            String res = webClient.get()
                    .uri(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofMillis(3000))
                    .block();

            if (res == null || res.isEmpty()) {
                log.warn("[Search] TWSE 搜尋回傳空值: {}", url);
                return objectMapper.createObjectNode();
            }

            if (res.trim().startsWith("{")) {
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
            } else {
                log.error("[Search] TWSE 搜尋回傳非 JSON 格式: {}", res.substring(0, Math.min(res.length(), 100)));
            }
        } catch (Exception e) {
            log.error("[Search] TWSE 搜尋異常: {} | 查詢字: {}", e.getMessage(), q, e);
        }
        return objectMapper.createObjectNode();
    }

    // ==========================================
    // 📈 報價與圖表
    // ==========================================
    public Map<String, StockQuoteDto> fetchFugleQuotes(List<String> symbols, String fugleKey) {
        Map<String, StockQuoteDto> results = new HashMap<>();
        String effectiveKey = resolveFugleKey(fugleKey);
        if (effectiveKey.isEmpty()) {
            log.warn("[Fugle Quotes] 缺少有效 API KEY，跳過抓取");
            return results;
        }

        for (String symbol : symbols) {
            try {
                String rawSymbol = symbol.split("\\.")[0];
                String url = "https://api.fugle.tw/marketdata/v1.0/stock/intraday/quote/" + rawSymbol;

                log.debug("[Fugle Quotes] 正在抓取: {}", symbol);
                ResponseEntity<JsonNode> response = webClient.get()
                        .uri(url)
                        .header("X-API-KEY", effectiveKey)
                        .header("User-Agent", "Mozilla/5.0")
                        .retrieve()
                        .onStatus(status -> status.isError(), clientResponse -> {
                            log.error("[Fugle Quotes] API 請求失敗 | 狀態碼: {} | 標的: {}", clientResponse.statusCode(), symbol);
                            return clientResponse.bodyToMono(String.class).map(body -> new RuntimeException("Fugle API Error: " + body));
                        })
                        .toEntity(JsonNode.class)
                        .onErrorResume(e -> {
                            log.error("[Fugle Quotes] 網路或協議異常 | 標的: {} | 原因: {}", symbol, e.getMessage());
                            return Mono.empty();
                        })
                        .block();

                if (response != null && response.getBody() != null) {
                    updateQuota(response.getHeaders());
                    JsonNode node = response.getBody();
                    
                    if (node.has("error")) {
                        log.error("[Fugle Quotes] API 業務邏輯錯誤 | 標的: {} | 訊息: {}", symbol, node.path("error").path("message").asText());
                        continue;
                    }

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
                            .marketStatus(isTaiwanMarketOpen() ? "OPEN" : "CLOSED")
                            .build();

                    results.put(symbol, dto);
                }
            } catch (Exception e) {
                log.error("[Fugle Quotes] {} 處理過程發生非預期異常: {}", symbol, e.getMessage());
            }
        }
        return results;
    }

    public List<ChartDataDto> fetchTwseMonthChart(String symbol) {
        String cacheKey = "TWSE_" + symbol;
        CacheEntry<List<ChartDataDto>> cached = chartCache.get(cacheKey);
        if (cached != null && (System.currentTimeMillis() - cached.timestamp < CACHE_DURATION)) return cached.data;

        List<ChartDataDto> chartData = new ArrayList<>();
        String rawSymbol = symbol.split("\\.")[0];
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String url = "https://www.twse.com.tw/exchangeReport/STOCK_DAY?response=json&date=" + dateStr + "&stockNo=" + rawSymbol;

        log.info("[TWSE Chart] 抓取日成交資料: {}", rawSymbol);
        try {
            JsonNode root = webClient.get().uri(url).header("User-Agent", "Mozilla/5.0").retrieve().bodyToMono(JsonNode.class).timeout(java.time.Duration.ofSeconds(10)).block();
            if (root != null && root.path("data").isArray()) {
                for (JsonNode day : root.path("data")) {
                    String[] parts = day.get(0).asText().split("/");
                    int year = Integer.parseInt(parts[0]) + 1911;
                    chartData.add(new ChartDataDto(year + "-" + parts[1] + "-" + parts[2],
                            new BigDecimal(day.get(3).asText().replace(",", "")),
                            new BigDecimal(day.get(4).asText().replace(",", "")),
                            new BigDecimal(day.get(5).asText().replace(",", "")),
                            new BigDecimal(day.get(6).asText().replace(",", ""))));
                }
                log.info("[TWSE Chart] {} 抓取成功，共 {} 筆", symbol, chartData.size());
            }
            if (!chartData.isEmpty()) chartCache.put(cacheKey, new CacheEntry<>(chartData));
        } catch (Exception e) { log.error("[TWSE Chart] {} 異常: {}", symbol, e.getMessage(), e); }
        return chartData;
    }

    public List<ChartDataDto> fetchFugleChart(String symbol, String fugleKey, String period) {
        String cacheKey = "FUGLE_" + symbol + "_" + period;
        CacheEntry<List<ChartDataDto>> cached = chartCache.get(cacheKey);
        if (cached != null && (System.currentTimeMillis() - cached.timestamp < CACHE_DURATION)) return cached.data;

        List<ChartDataDto> chartData = new ArrayList<>();
        String effectiveKey = resolveFugleKey(fugleKey);
        if (effectiveKey.isEmpty()) return chartData;
        try {
            String rawSymbol = symbol.split("\\.")[0];
            String fromDate = LocalDate.now().minusMonths(6).toString();
            if (period.contains("w")) fromDate = LocalDate.now().minusYears(2).toString();
            if (period.contains("m")) fromDate = LocalDate.now().minusYears(5).toString();
            
            String url = "https://api.fugle.tw/marketdata/v1.0/stock/historical/candles/1d?symbol=" + rawSymbol + "&from=" + fromDate + "&to=" + LocalDate.now().toString();
            String raw = webClient.get().uri(url).header("X-API-KEY", effectiveKey).header("User-Agent", "Mozilla/5.0").retrieve().bodyToMono(String.class).block();
            if (raw != null) {
                JsonNode root = objectMapper.readTree(raw);
                JsonNode candles = root.path("data");
                if (candles.isArray()) {
                    for (JsonNode c : candles) {
                        chartData.add(new ChartDataDto(c.path("date").asText(), new BigDecimal(c.path("open").asText()), new BigDecimal(c.path("high").asText()), new BigDecimal(c.path("low").asText()), new BigDecimal(c.path("close").asText())));
                    }
                }
                if (period.contains("w")) chartData = aggregateByPeriod(chartData, "WEEK");
                else if (period.contains("m")) chartData = aggregateByPeriod(chartData, "MONTH");
                if (!chartData.isEmpty()) chartCache.put(cacheKey, new CacheEntry<>(chartData));
            }
        } catch (Exception e) { log.error("[Fugle Chart] {} 失敗: {}", symbol, e.getMessage(), e); }
        return chartData;
    }

    private List<ChartDataDto> aggregateByPeriod(List<ChartDataDto> dailyData, String type) {
        if (dailyData == null || dailyData.isEmpty()) return new ArrayList<>();
        List<ChartDataDto> aggregated = new ArrayList<>();
        ChartDataDto currentBar = null;
        String currentKey = "";
        for (ChartDataDto day : dailyData) {
            LocalDate date = LocalDate.parse(day.getTime());
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

    @Scheduled(fixedRate = 1800000)
    public void cleanCache() {
        long now = System.currentTimeMillis();
        chartCache.entrySet().removeIf(entry -> now - entry.getValue().timestamp > CACHE_DURATION);
        searchCache.entrySet().removeIf(entry -> now - entry.getValue().timestamp > SEARCH_CACHE_DURATION);
    }

    // ==========================================
    // 🏦 ETF 淨值與基本面系統
    // ==========================================
    private CacheEntry<Map<String, Map<String, Object>>> twNavCache = null;
    private Map<String, Map<String, Object>> lastApiResults = new ConcurrentHashMap<>();
    private static final long NAV_CACHE_DURATION = 3600000;
    private final Object navLock = new Object();

    public Map<String, Map<String, Object>> fetchAllTwEtfNavs() {
        synchronized (navLock) {
            if (twNavCache != null && (System.currentTimeMillis() - twNavCache.timestamp < NAV_CACHE_DURATION)) return twNavCache.data;
            
            String lastSyncDate = getSystemConfig("LAST_ETF_NAV_SYNC_DATE", "");
            // [修正] 增加判斷：如果內存快取為空 (表示重啟過)，即便今日已同步過也強制執行一次 API 抓取
            if (!LocalDate.now().toString().equals(lastSyncDate) || lastApiResults.isEmpty()) {
                log.info("[Sync] 內存數據為空或今日尚未同步，執行數據填充...");
                refreshNavsFromApi();
            }
            
            Map<String, Map<String, Object>> results = loadNavsFromDb();
            // 合併內存中的最新 API 結果，確保即便不在觀察清單（不在 DB）的票也能補強
            lastApiResults.forEach((k, v) -> {
                results.merge(k, v, (oldV, newV) -> {
                    oldV.putAll(newV);
                    return oldV;
                });
            });
            
            twNavCache = new CacheEntry<>(results);
            return results;
        }
    }

    public void refreshNavsFromApi() {
        log.info("[Sync] 開始執行台股數據同步流程...");
        Map<String, Map<String, Object>> results = new HashMap<>();
        try {
            fetchFromTwseNav(results);         
            fetchFromTwseFundamental(results);  
            fetchFromTwseTrading(results); 
            fetchFromTpexNav(results);
            
            // [補強] 針對關鍵 ETF (如 0050) 使用元大投信特定接口
            if (!results.containsKey("0050")) {
                fetchFromYuanta(results, "0050");
            }
            
            log.info("[Sync] API 抓取完成。總共獲取數據筆數: {}", results.size());
            
            if (!results.isEmpty()) {
                this.lastApiResults = new ConcurrentHashMap<>(results);
                updateStockStatsInDb(results);
                saveSystemConfig("LAST_ETF_NAV_SYNC_DATE", LocalDate.now().toString(), "最後同步日期");
                twNavCache = null; // 強制下次讀取時重新合併
                log.info("[Sync] 台股基本面與淨值同步成功完成。");
            } else {
                log.warn("[Sync] 所有 API 抓取結果皆為空，請檢查網路或 API 位址。");
            }
        } catch (Exception e) {
            log.error("[Sync] 同步過程發生非預期異常: {}", e.getMessage(), e);
        }
    }

    private void fetchFromYuanta(Map<String, Map<String, Object>> results, String symbol) {
        // ... (保持原有實作)
        String url = "https://www.yuantafunds.com/api/FundNav/List?fundCode=" + symbol;
        try {
            log.info("[NAV] 🚀 正在嘗試從元大官網補強抓取: {}", symbol);
            JsonNode root = webClient.get().uri(url).header("User-Agent", "Mozilla/5.0").retrieve().bodyToMono(JsonNode.class).block();
            if (root != null && root.isArray() && root.size() > 0) {
                JsonNode latest = root.get(0);
                Map<String, Object> data = results.getOrDefault(symbol, new HashMap<>());
                data.put("nav", new BigDecimal(latest.path("Nav").asText()));
                results.put(symbol, data);
                log.info("[NAV] ✅ 0050 元大官網補強成功: NAV={}", data.get("nav"));
            }
        } catch (Exception e) { log.warn("[NAV] 元大官網抓取失敗: {}", e.getMessage()); }
    }

    private void fetchFromTwseFundamental(Map<String, Map<String, Object>> results) {
        // ... (保持原有實作)
        String url = "https://www.twse.com.tw/exchangeReport/BWIBBU_ALL?response=json";
        try {
            JsonNode root = webClient.get().uri(url).header("User-Agent", "Mozilla/5.0").retrieve().bodyToMono(JsonNode.class).block();
            if (root != null && root.path("data").isArray()) {
                int count = 0;
                for (JsonNode row : root.path("data")) {
                    String symbol = row.get(0).asText().trim();
                    Map<String, Object> data = results.getOrDefault(symbol, new HashMap<>());
                    try {
                        String pe = row.get(2).asText().replace(",", "");
                        String yield = row.get(3).asText().replace(",", "");
                        String pb = row.get(4).asText().replace(",", "");
                        if (!"-".equals(pe)) data.put("peRatio", new BigDecimal(pe));
                        if (!"-".equals(yield)) data.put("dividendYield", new BigDecimal(yield));
                        if (!"-".equals(pb)) data.put("pbRatio", new BigDecimal(pb));
                        results.put(symbol, data);
                        count++;
                    } catch (Exception ignored) {}
                }
                log.info("[Fundamental] TWSE 基本面成功抓取: {} 檔", count);
            }
        } catch (Exception e) { log.error("[Fundamental] 異常: {}", e.getMessage(), e); }
    }

    private void fetchFromTwseTrading(Map<String, Map<String, Object>> results) {
        // 1. 外資及陸資買賣超 (TWT38U) - 索引 4 為買賣超股數
        fetchTradingData("https://www.twse.com.tw/fund/TWT38U?response=json", results, "foreignBuy", 4);
        // 2. 投信買賣超 (TWT39U) - 索引 4 為買賣超股數
        fetchTradingData("https://www.twse.com.tw/fund/TWT39U?response=json", results, "trustBuy", 4);
    }

    private void fetchTradingData(String url, Map<String, Map<String, Object>> results, String key, int index) {
        try {
            JsonNode root = webClient.get().uri(url).header("User-Agent", "Mozilla/5.0").retrieve().bodyToMono(JsonNode.class).block();
            if (root != null && root.path("data").isArray()) {
                for (JsonNode row : root.path("data")) {
                    String symbol = row.get(0).asText().trim();
                    Map<String, Object> data = results.getOrDefault(symbol, new HashMap<>());
                    try {
                        long volume = Long.parseLong(row.get(index).asText().replace(",", ""));
                        data.put(key, volume / 1000); // 股轉張
                        results.put(symbol, data);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) { log.error("[Trading] {} 異常: {}", key, e.getMessage()); }
    }

    private void fetchFromTwseNav(Map<String, Map<String, Object>> results) {
        // 2025 新版 MIS 穩定端點：整合所有 ETF 揭露資訊
        long timestamp = System.currentTimeMillis();
        String url = "https://mis.twse.com.tw/stock/data/all_etf.txt?_=" + timestamp;
        try {
            // 注意：MIS 伺服器對 Referer 有嚴格要求
            String rawJson = webClient.get().uri(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Referer", "https://mis.twse.com.tw/stock/various-areas/etf-price/indicator-disclosure-etf")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            if (rawJson == null || rawJson.isEmpty()) return;
            
            JsonNode root = new ObjectMapper().readTree(rawJson);
            // all_etf.txt 是一個包含多個投信物件的 JSON，每個物件內都有 msgArray
            // 我們需要遍歷所有節點找到 msgArray
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            int count = 0;
            
            while (fields.hasNext()) {
                JsonNode houseNode = fields.next().getValue();
                if (houseNode.isArray()) {
                    // 有些投信直接是陣列 (如 a1, a2...)
                    for (JsonNode entry : houseNode) {
                        processMsgArray(entry.path("msgArray"), results);
                    }
                } else if (houseNode.isObject()) {
                    // 有些投信是物件包含 msgArray
                    processMsgArray(houseNode.path("msgArray"), results);
                }
            }
            
            log.info("[NAV] TWSE MIS (all_etf.txt) 淨值同步完成");
        } catch (Exception e) { 
            log.error("[NAV] TWSE MIS 異常: {}", e.getMessage());
            // Fallback: 嘗試舊版端點
            fetchFromTwseNavLegacy(results);
        }
    }

    private void processMsgArray(JsonNode msgArray, Map<String, Map<String, Object>> results) {
        if (msgArray == null || !msgArray.isArray()) return;
        
        for (JsonNode row : msgArray) {
            String symbol = row.path("a").asText().trim();
            if (symbol.isEmpty()) continue;
            
            String cleanSymbol = symbol.replaceAll("[^0-9A-Za-z]", "");
            // 決定後綴：如果是 a 欄位內容判斷 ex 是否為 otc
            String exchange = row.path("ex").asText();
            String suffixedSymbol = cleanSymbol + ("otc".equals(exchange) ? ".TWO" : ".TW");
            
            Map<String, Object> data = results.getOrDefault(suffixedSymbol, new HashMap<>());
            try {
                // 2025 ver 1.5 規範：
                // e: 即時預估淨值 (Estimated NAV)
                // f: 前一營業日淨值 (Yesterday's NAV)
                // h: 即時市價 (Market Price)
                String iNavStr = row.path("e").asText().replace(",", "");
                String yNavStr = row.path("f").asText().replace(",", "");
                
                // 優先使用即時預估淨值，若無則使用昨日淨值
                String finalNav = (!"-".equals(iNavStr) && !iNavStr.isEmpty()) ? iNavStr : yNavStr;
                
                if (cleanSymbol.equals("0050") || cleanSymbol.equals("00981A") || cleanSymbol.equals("006208")) {
                    log.info("[NAV Debug] {} Row Data: {}", suffixedSymbol, row.toString());
                }

                if (!"-".equals(finalNav) && !finalNav.isEmpty()) {
                    BigDecimal navVal = new BigDecimal(finalNav);
                    data.put("nav", navVal);
                    if (cleanSymbol.equals("0050") || cleanSymbol.equals("0056") || cleanSymbol.equals("006208")) {
                        log.info("[NAV Debug] {} -> iNAV: {}, yNAV: {}, Final: {}", suffixedSymbol, iNavStr, yNavStr, navVal);
                    }
                }
                
                // 計算折溢價 (若 API 有提供 g 欄位則直接使用，否則維持現有計算邏輯)
                String premiumStr = row.path("g").asText().replace(",", "").replace("%", "");
                if (!"-".equals(premiumStr) && !premiumStr.isEmpty()) {
                    data.put("premium", Double.parseDouble(premiumStr));
                }
                
                results.put(suffixedSymbol, data);
                results.put(cleanSymbol, data); // 同時保留不帶後綴的 key 以供舊邏輯使用
                
                // [補強] 針對 006208 等可能在 API 中沒正確標註 exchange 的情況，多存一份常用的後綴
                if (!"otc".equals(exchange)) {
                    results.put(cleanSymbol + ".TW", data);
                }
            } catch (Exception e) {
                if (cleanSymbol.equals("0050")) log.error("[NAV Debug] 0050 解析出錯: {}", e.getMessage());
            }
        }
    }

    private void fetchFromTwseNavLegacy(Map<String, Map<String, Object>> results) {
        String url = "https://mis.twse.com.tw/stock/api/getEtfNav.jsp?ex=tse";
        try {
            JsonNode root = webClient.get().uri(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", "https://mis.twse.com.tw/stock/etf_nav.jsp")
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            
            if (root != null && root.path("msgArray").isArray()) {
                for (JsonNode row : root.path("msgArray")) {
                    String symbol = row.path("a").asText().trim();
                    String cleanSymbol = symbol.replaceAll("[^0-9A-Za-z]", "");
                    Map<String, Object> data = results.getOrDefault(cleanSymbol, new HashMap<>());
                    try {
                        // 舊版映射：f 為預估淨值，h 為昨日淨值
                        String navStr = row.path("f").asText().replace(",", "");
                        if ("-".equals(navStr) || navStr.isEmpty()) navStr = row.path("h").asText().replace(",", "");
                        if (!"-".equals(navStr) && !navStr.isEmpty()) data.put("nav", new BigDecimal(navStr));
                        results.put(cleanSymbol, data);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
    }

    private void fetchFromTpexNav(Map<String, Map<String, Object>> results) {
        String url = "https://www.tpex.org.tw/web/stock/aftertrading/all_daily_etf_nav/etf_nav_result.php?l=zh-tw";
        try {
            JsonNode root = webClient.get().uri(url).header("User-Agent", "Mozilla/5.0").retrieve().bodyToMono(JsonNode.class).block();
            if (root != null && root.path("aaData").isArray()) {
                int count = 0;
                for (JsonNode row : root.path("aaData")) {
                    String symbol = row.get(0).asText().trim();
                    Map<String, Object> data = results.getOrDefault(symbol, new HashMap<>());
                    try {
                        String navStr = row.get(2).asText().replace(",", "");
                        String premiumStr = row.get(3).asText().replace(",", "");
                        if (!"-".equals(navStr)) data.put("nav", new BigDecimal(navStr));
                        if (!"-".equals(premiumStr)) data.put("premium", Double.parseDouble(premiumStr));
                        results.put(symbol, data);
                        count++;
                    } catch (Exception ignored) {}
                }
                log.info("[NAV] TPEx 淨值成功抓取: {} 檔", count);
            }
        } catch (Exception e) { log.error("[NAV] TPEx 異常: {}", e.getMessage()); }
    }

    @Transactional
    public void updateStockStatsInDb(Map<String, Map<String, Object>> navResults) {
        List<StockEntity> stocks = stockRepository.findAll();
        int updateCount = 0;
        log.info("[DB] 開始執行資料庫匹配更新 (目標筆數: {})", stocks.size());
        for (StockEntity stock : stocks) {
            String rawSym = stock.getSymbol();
            String cleanSym = rawSym.split("\\.")[0];
            
            // 魯棒性匹配邏輯：
            // 1. 先用資料庫的完整 Symbol (如 2330.TW) 去找
            // 2. 若找不到，改用去除後綴的代碼 (如 2330) 去找
            Map<String, Object> data = navResults.getOrDefault(rawSym, navResults.get(cleanSym));
            
            if (data != null) {
                if (data.containsKey("nav")) stock.setNav((BigDecimal) data.get("nav"));
                if (data.containsKey("premium")) stock.setPremium((Double) data.get("premium"));
                if (data.containsKey("peRatio")) stock.setPeRatio((BigDecimal) data.get("peRatio"));
                if (data.containsKey("dividendYield")) stock.setDividendYield((BigDecimal) data.get("dividendYield"));
                if (data.containsKey("pbRatio")) stock.setPbRatio((BigDecimal) data.get("pbRatio"));
                if (data.containsKey("foreignBuy")) stock.setForeignBuy((Long) data.get("foreignBuy"));
                if (data.containsKey("trustBuy")) stock.setTrustBuy((Long) data.get("trustBuy"));
                stockRepository.save(stock);
                updateCount++;
                log.debug("[DB] 成功更新股票: {}", rawSym);
            } else {
                log.debug("[DB] 股票 {} 未能匹配到任何 API 數據", rawSym);
            }
        }
        log.info("[DB] 資料庫更新執行完畢。成功更新: {} 筆", updateCount);
    }

    private Map<String, Map<String, Object>> loadNavsFromDb() {
        Map<String, Map<String, Object>> results = new HashMap<>();
        stockRepository.findAll().forEach(s -> {
            Map<String, Object> d = new HashMap<>();
            if (s.getNav() != null) d.put("nav", s.getNav());
            if (s.getPremium() != null) d.put("premium", s.getPremium());
            if (s.getPeRatio() != null) d.put("peRatio", s.getPeRatio());
            if (s.getDividendYield() != null) d.put("dividendYield", s.getDividendYield());
            if (s.getPbRatio() != null) d.put("pbRatio", s.getPbRatio());
            if (s.getForeignBuy() != null) d.put("foreignBuy", s.getForeignBuy());
            if (s.getTrustBuy() != null) d.put("trustBuy", s.getTrustBuy());
            results.put(s.getSymbol(), d);
        });
        return results;
    }

    private String getSystemConfig(String key, String defaultValue) {
        return configRepository.findById(key).map(ConfigEntity::getValue).orElse(defaultValue);
    }

    private void saveSystemConfig(String key, String value, String desc) {
        ConfigEntity e = configRepository.findById(key).orElse(ConfigEntity.builder().key(key).build());
        e.setValue(value); e.setDescription(desc);
        configRepository.save(e);
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

    /**
     * 判斷台股市場當前是否開盤 (台北時間 09:00 - 13:30)
     */
    private boolean isTaiwanMarketOpen() {
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Taipei"));
        java.time.DayOfWeek day = now.getDayOfWeek();
        if (day == java.time.DayOfWeek.SATURDAY || day == java.time.DayOfWeek.SUNDAY) return false;
        java.time.LocalTime time = now.toLocalTime();
        return !time.isBefore(java.time.LocalTime.of(9, 0)) && !time.isAfter(java.time.LocalTime.of(13, 35));
    }
}