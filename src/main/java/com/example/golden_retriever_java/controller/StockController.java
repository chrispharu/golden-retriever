package com.example.golden_retriever_java.controller;

import com.example.golden_retriever_java.dto.*;
import com.example.golden_retriever_java.service.DataService;
import com.example.golden_retriever_java.service.GoldService;
import com.example.golden_retriever_java.service.TwStockService;
import com.example.golden_retriever_java.service.YahooFinanceClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class StockController {

    private static final Logger log = LoggerFactory.getLogger(StockController.class);

    // [NEW] 宣告字串常數以符合靜態掃描規範
    private static final String KEY_SUCCESS = "success";
    private static final String KEY_QUOTES = "quotes";
    private static final String KEY_PRICE = "price";
    private static final String KEY_SYMBOL = "symbol";
    private static final String KEY_SHORTNAME = "shortname";
    private static final String KEY_LONGNAME = "longname";
    private static final String CURRENCY_USD = "USD";

    private final YahooFinanceClient yahooFinanceClient;
    private final TwStockService twStockService;
    private final DataService db;
    private final GoldService goldService;
    private final com.example.golden_retriever_java.service.QuantService quantService;

    public StockController(YahooFinanceClient yahooFinanceClient,
            TwStockService twStockService,
            DataService db,
            GoldService goldService,
            com.example.golden_retriever_java.service.QuantService quantService) {
        this.yahooFinanceClient = yahooFinanceClient;
        this.twStockService = twStockService;
        this.db = db;
        this.goldService = goldService;
        this.quantService = quantService;
    }

    // ==========================================
    // Data Transfer Objects (DTOs)
    // ==========================================
    @Data
    public static class InventoryRequest {
        private String id;
        private String symbol;
        private String name;
        private BigDecimal price;
        private BigDecimal shares;
        private String date;
        private BigDecimal exchangeRate;
    }

    @Data
    public static class StockRequest {
        private String symbol;
        private String newName;
        private List<String> newOrder;
    }

    @Data
    public static class CurrencyRequest {
        private String currency;
        private List<String> newOrder;
    }

    @Data
    public static class LogRequest {
        private String context;
        private String message;
        private String stack;
    }

    @Data
    public static class SellRequest {
        private Long inventoryId;
        private BigDecimal shares;
        private BigDecimal sellPrice;
        private BigDecimal sellRate;
        private String sellDate;
    }

    @Data
    public static class DividendRequest {
        private String symbol;
        private String name;
        private BigDecimal amount;
        private BigDecimal exchangeRate;
        private String date;
        private String type;
    }

    /**
     * 判斷是否為台股代碼
     */
    private boolean isTaiwanStock(String symbol) {
        if (symbol == null)
            return false;
        return symbol.matches("^\\d+$") || symbol.contains(".TW") || symbol.contains(".TWO");
    }

    // ==========================================
    // 1. 市場數據 API
    // ==========================================
    @GetMapping("/gold")
    public ResponseEntity<Map<String, Object>> getGoldData() {
        try {
            GoldPriceDto goldInfo = goldService.fetchRealGoldPrice();
            return ResponseEntity.ok(Collections.singletonMap("gold", goldInfo));
        } catch (Exception e) {
            log.error("獲取黃金價格失敗: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/rates")
    public ResponseEntity<Map<String, Object>> getRatesData() {
        try {
            UserPortfolioData userData = db.getData();
            List<String> currencies = userData.getCurrencies().isEmpty() ? Collections.singletonList(CURRENCY_USD)
                    : userData.getCurrencies();
            Map<String, Map<String, BigDecimal>> rates = yahooFinanceClient.fetchExchangeRates(currencies);
            
            Map<String, Object> response = new HashMap<>();
            response.put("rates", rates);
            response.put("currencyOrder", currencies);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("獲取匯率數據失敗: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * [NEW] 獲取庫存登錄參考數據 (價格與匯率)
     */
    @GetMapping("/inventory/reference-data")
    public ResponseEntity<Map<String, Object>> getInventoryReferenceData(
            @RequestParam("symbol") String symbol,
            @RequestParam(value = "date", required = false) String date) {
        try {
            String currency = isTaiwanStock(symbol) ? "TWD" : CURRENCY_USD;
            BigDecimal price = BigDecimal.ZERO;
            BigDecimal exchangeRate = BigDecimal.ONE;

            // 1. 獲取價格
            String today = LocalDateTime.now(ZoneId.of("Asia/Taipei")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            if (date == null || date.isEmpty() || date.equals(today)) {
                // 今日價
                Map<String, StockQuoteDto> quotes = yahooFinanceClient.fetchQuotes(Collections.singletonList(symbol));
                if (quotes.containsKey(symbol)) {
                    price = quotes.get(symbol).getPrice();
                }
            } else {
                // 歷史價
                List<ChartDataDto> chartData = yahooFinanceClient.fetchChartData(symbol, "1d");
                // 尋找小於等於該日期的最後一筆數據 (處理假日)
                price = chartData.stream()
                        .filter(d -> d.getTime().compareTo(date) <= 0)
                        .reduce((first, second) -> second) // 取最後一個
                        .map(ChartDataDto::getClose)
                        .orElse(BigDecimal.ZERO);
            }

            // 2. 獲取匯率
            if (!"TWD".equals(currency)) {
                Map<String, Map<String, BigDecimal>> rates = yahooFinanceClient.fetchExchangeRates(Collections.singletonList(currency));
                if (rates.containsKey(currency)) {
                    exchangeRate = rates.get(currency).get(KEY_PRICE);
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("symbol", symbol);
            response.put("price", price);
            response.put("exchangeRate", exchangeRate);
            response.put("currency", currency);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("獲取參考數據失敗: symbol={}, date={}, error={}", symbol, date, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/stocks")
    public ResponseEntity<DashboardResponseDto> getStocksData(
            @RequestHeader(value = "x-fugle-key", required = false, defaultValue = "") String headerKey,
            @RequestHeader(value = "Authorization", required = false, defaultValue = "") String authHeader) {
        try {
            // [ENHANCED] 多重來源驗證：從 Header 或資料庫獲取 Fugle Key
            String effectiveFugleKey = headerKey;
            
            // 如果 Authorization Header 存在且以 Bearer 開頭，嘗試提取（不進行任何編碼轉換）
            if (effectiveFugleKey.isEmpty() && authHeader.startsWith("Bearer ")) {
                effectiveFugleKey = authHeader.substring(7).trim();
            }

            // 最後嘗試從資料庫讀取
            if (effectiveFugleKey.isEmpty()) {
                effectiveFugleKey = db.getConfig("FUGLE_API_KEY", "");
            }

            log.info("[Security] Fugle Key Source: {}, Length: {}", 
                headerKey.isEmpty() ? (authHeader.isEmpty() ? "DB" : "Auth-Header") : "X-Header",
                effectiveFugleKey.length());

            UserPortfolioData userData = db.getData();
            List<UserPortfolioData.TargetStock> stocks = userData.getStocks();
            List<UserPortfolioData.InventoryItem> inventory = userData.getInventory();
            List<String> currencies = userData.getCurrencies().isEmpty() ? Collections.singletonList(CURRENCY_USD)
                    : userData.getCurrencies();

            Set<String> uniqueSymbols = new HashSet<>();
            stocks.forEach(s -> uniqueSymbols.add(s.getSymbol()));
            inventory.forEach(i -> uniqueSymbols.add(i.getSymbol()));
            List<String> symbols = new ArrayList<>(uniqueSymbols);

            List<String> twSymbols = symbols.stream().filter(this::isTaiwanStock)
                    .collect(Collectors.toList());
            List<String> usSymbols = symbols.stream().filter(s -> !isTaiwanStock(s))
                    .collect(Collectors.toList());

            // 如果有台股但沒有 API Key，則回退到 Yahoo Finance
            if (!twSymbols.isEmpty() && effectiveFugleKey.isEmpty()) {
                usSymbols.addAll(twSymbols);
                twSymbols.clear();
            }

            Map<String, Map<String, BigDecimal>> ratesMap = yahooFinanceClient.fetchExchangeRates(currencies);
            Map<String, StockQuoteDto> stockData = new HashMap<>(yahooFinanceClient.fetchQuotes(usSymbols));

            if (!twSymbols.isEmpty()) {
                if (!effectiveFugleKey.isEmpty()) {
                    stockData.putAll(twStockService.fetchFugleQuotes(twSymbols, effectiveFugleKey));
                } else {
                    stockData.putAll(yahooFinanceClient.fetchQuotes(twSymbols));
                }
            }

            Map<String, DashboardResponseDto.GroupedInventory> groupedInvMap = processGroupedInventory(inventory,
                    stocks, ratesMap, stockData);
            List<DashboardResponseDto.PortfolioItem> portfolio = processPortfolio(stocks, ratesMap, stockData,
                    groupedInvMap);

            // 獲取大盤參考指標
            Map<String, DashboardResponseDto.BenchmarkItem> benchmarks = new HashMap<>();
            List<String> benchmarkSymbols = Arrays.asList("^TWII", "^GSPC");
            Map<String, StockQuoteDto> benchmarkQuotes = yahooFinanceClient.fetchQuotes(benchmarkSymbols);
            
            benchmarkQuotes.forEach((sym, q) -> {
                BigDecimal changePercent = BigDecimal.ZERO;
                if (q.getPrevClose() != null && q.getPrevClose().compareTo(BigDecimal.ZERO) > 0) {
                    changePercent = q.getPrice().subtract(q.getPrevClose())
                            .divide(q.getPrevClose(), 4, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"));
                }
                benchmarks.put(sym, DashboardResponseDto.BenchmarkItem.builder()
                        .symbol(sym)
                        .name(sym.equals("^TWII") ? "加權指數" : "標普 500")
                        .price(q.getPrice())
                        .changePercent(changePercent)
                        .build());
            });

            DashboardResponseDto response = DashboardResponseDto.builder()
                    .updatedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")))
                    .portfolio(portfolio)
                    .groupedInventory(new ArrayList<>(groupedInvMap.values()))
                    .benchmarks(benchmarks)
                    .allocation(processAllocation(groupedInvMap))
                    .build();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("獲取股票數據失敗: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private DashboardResponseDto.AllocationDto processAllocation(Map<String, DashboardResponseDto.GroupedInventory> groups) {
        Map<String, BigDecimal> byCurrency = new HashMap<>();
        Map<String, BigDecimal> byAsset = new HashMap<>();
        Map<String, BigDecimal> byType = new HashMap<>();

        for (DashboardResponseDto.GroupedInventory g : groups.values()) {
            BigDecimal mv = g.getMarketValueTWD();
            if (mv.compareTo(BigDecimal.ZERO) <= 0) continue;

            byAsset.put(g.getName(), mv.setScale(0, RoundingMode.HALF_UP));
            String currencyKey = g.isTW() ? "TWD" : "USD";
            byCurrency.merge(currencyKey, mv, BigDecimal::add);
            String typeKey = g.isTW() ? "台股" : "美股";
            byType.merge(typeKey, mv, BigDecimal::add);
        }

        byCurrency.replaceAll((k, v) -> v.setScale(0, RoundingMode.HALF_UP));
        byType.replaceAll((k, v) -> v.setScale(0, RoundingMode.HALF_UP));

        return DashboardResponseDto.AllocationDto.builder()
                .byCurrency(byCurrency)
                .byAsset(byAsset)
                .byType(byType)
                .build();
    }

    private Map<String, DashboardResponseDto.GroupedInventory> processGroupedInventory(
            List<UserPortfolioData.InventoryItem> inventory,
            List<UserPortfolioData.TargetStock> stocks,
            Map<String, Map<String, BigDecimal>> ratesMap,
            Map<String, StockQuoteDto> stockData) {

        Map<String, DashboardResponseDto.GroupedInventory> groupedInvMap = new LinkedHashMap<>();
        Map<String, BigDecimal> dividendsMap = db.getTotalDividendsBySymbol();

        for (UserPortfolioData.InventoryItem inv : inventory) {
            if (inv.getSymbol() == null || inv.getSymbol().isEmpty())
                continue;
            boolean isTW = isTaiwanStock(inv.getSymbol());

            final String resolvedName = (inv.getName() == null || inv.getName().isEmpty())
                    ? stocks.stream().filter(s -> s.getSymbol().equals(inv.getSymbol()))
                            .map(UserPortfolioData.TargetStock::getName).findFirst().orElse(inv.getSymbol())
                    : inv.getName();

            DashboardResponseDto.GroupedInventory group = groupedInvMap.computeIfAbsent(inv.getSymbol(),
                    k -> DashboardResponseDto.GroupedInventory.builder()
                            .symbol(inv.getSymbol())
                            .name(resolvedName)
                            .isTW(isTW)
                            .totalShares(BigDecimal.ZERO)
                            .totalCostTWD(BigDecimal.ZERO)
                            .totalCostOriginal(BigDecimal.ZERO)
                            .records(new ArrayList<>())
                            .build());

            BigDecimal p = inv.getPrice() != null ? inv.getPrice() : BigDecimal.ZERO;
            BigDecimal s = inv.getShares() != null ? inv.getShares() : BigDecimal.ZERO;
            BigDecimal r = inv.getExchangeRate();

            if (r == null || r.compareTo(BigDecimal.ZERO) <= 0) {
                if (isTW)
                    r = BigDecimal.ONE;
                else {
                    Map<String, BigDecimal> rateObj = ratesMap.get(CURRENCY_USD);
                    r = (rateObj != null && rateObj.get(KEY_PRICE) != null) ? rateObj.get(KEY_PRICE) : BigDecimal.ZERO;
                }
            }

            if (p.compareTo(BigDecimal.ZERO) > 0 && s.compareTo(BigDecimal.ZERO) > 0) {
                group.setTotalShares(group.getTotalShares().add(s));
                BigDecimal costOrig = p.multiply(s);
                group.setTotalCostOriginal(group.getTotalCostOriginal().add(costOrig));
                group.setTotalCostTWD(group.getTotalCostTWD().add(costOrig.multiply(r)));
            }
            inv.setUsedRate(r);
            group.getRecords().add(inv);
        }

        for (DashboardResponseDto.GroupedInventory g : groupedInvMap.values()) {
            StockQuoteDto mkt = stockData.getOrDefault(g.getSymbol(), new StockQuoteDto(BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "", null, "CLOSED"));
            BigDecimal currentRate = BigDecimal.ONE;
            if (!g.isTW()) {
                Map<String, BigDecimal> rateObj = ratesMap.get(CURRENCY_USD);
                currentRate = (rateObj != null && rateObj.get(KEY_PRICE) != null) ? rateObj.get(KEY_PRICE)
                        : BigDecimal.ZERO;
            }

            BigDecimal currentPrice = mkt.getPrice() != null ? mkt.getPrice() : BigDecimal.ZERO;
            boolean hasValidRate = currentRate.compareTo(BigDecimal.ZERO) > 0 || g.isTW();
            BigDecimal marketValueTWD = BigDecimal.ZERO;
            BigDecimal roi = BigDecimal.ZERO;
            BigDecimal plAmountTWD = BigDecimal.ZERO;
            BigDecimal pricePLTWD = BigDecimal.ZERO;
            BigDecimal exchangePLTWD = BigDecimal.ZERO;
            BigDecimal totalDividendsTWD = dividendsMap.getOrDefault(g.getSymbol(), BigDecimal.ZERO);

            if (hasValidRate && g.getTotalShares().compareTo(BigDecimal.ZERO) > 0) {
                marketValueTWD = currentPrice.multiply(g.getTotalShares()).multiply(currentRate);
                plAmountTWD = marketValueTWD.subtract(g.getTotalCostTWD());
                
                if (g.getTotalCostTWD().compareTo(BigDecimal.ZERO) > 0) {
                    // 總投報率 = (未實現損益 + 已領股利) / 總投入成本
                    roi = plAmountTWD.add(totalDividendsTWD)
                            .divide(g.getTotalCostTWD(), 4, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"));
                    
                    BigDecimal avgCostOrig = g.getTotalCostOriginal().divide(g.getTotalShares(), 8, RoundingMode.HALF_UP);
                    BigDecimal avgExRate = g.getTotalCostTWD().divide(g.getTotalCostOriginal(), 8, RoundingMode.HALF_UP);
                    
                    pricePLTWD = currentPrice.subtract(avgCostOrig).multiply(g.getTotalShares()).multiply(currentRate);
                    exchangePLTWD = avgCostOrig.multiply(g.getTotalShares()).multiply(currentRate.subtract(avgExRate));
                }
            }

            g.setCurrentPrice(currentPrice);
            g.setMarketStatus(mkt.getMarketStatus());
            g.setPostMarketPrice(mkt.getPostMarketPrice());
            g.setCurrentRate(currentRate);
            g.setMarketValueTWD(marketValueTWD);
            g.setTotalDividendsTWD(totalDividendsTWD);
            g.setRoi(roi);
            g.setPlAmountTWD(plAmountTWD.setScale(0, RoundingMode.HALF_UP));
            g.setPricePLTWD(pricePLTWD.setScale(0, RoundingMode.HALF_UP));
            g.setExchangePLTWD(exchangePLTWD.setScale(0, RoundingMode.HALF_UP));
            g.setHasValidRate(hasValidRate);
            g.setFiftyTwoWeekHigh(mkt.getFiftyTwoWeekHigh());
            g.setFiftyTwoWeekLow(mkt.getFiftyTwoWeekLow());
            g.setVolume(mkt.getVolume());
            g.setAvgVolume(mkt.getAvgVolume());
        }

        return groupedInvMap;
    }

    @PostMapping("/add-dividend")
    public ResponseEntity<Map<String, Boolean>> addDividend(@RequestBody DividendRequest req) {
        db.addDividend(com.example.golden_retriever_java.entity.DividendEntity.builder()
                .symbol(req.getSymbol()).name(req.getName())
                .amount(req.getAmount()).exchangeRate(req.getExchangeRate())
                .date(req.getDate()).type(req.getType())
                .build());
        return ResponseEntity.ok(Collections.singletonMap(KEY_SUCCESS, true));
    }

    @GetMapping("/dividends")
    public ResponseEntity<List<com.example.golden_retriever_java.entity.DividendEntity>> getDividends() {
        return ResponseEntity.ok(db.getDividends());
    }

    private List<DashboardResponseDto.PortfolioItem> processPortfolio(
            List<UserPortfolioData.TargetStock> stocks,
            Map<String, Map<String, BigDecimal>> ratesMap,
            Map<String, StockQuoteDto> stockData,
            Map<String, DashboardResponseDto.GroupedInventory> groupedInvMap) {

        List<DashboardResponseDto.PortfolioItem> portfolio = new ArrayList<>();
        for (UserPortfolioData.TargetStock s : stocks) {
            StockQuoteDto mkt = stockData.getOrDefault(s.getSymbol(), new StockQuoteDto(BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "", null, "CLOSED"));
            Map<String, BigDecimal> rateObj = ratesMap.get(s.getCurrency());
            BigDecimal ratePrice = (rateObj != null && rateObj.get(KEY_PRICE) != null) ? rateObj.get(KEY_PRICE)
                    : BigDecimal.ZERO;

            BigDecimal avgCost = BigDecimal.ZERO;
            BigDecimal avgCostTWD = BigDecimal.ZERO;
            DashboardResponseDto.GroupedInventory gStats = groupedInvMap.get(s.getSymbol());

            if (gStats != null && gStats.getTotalShares().compareTo(BigDecimal.ZERO) > 0) {
                avgCost = gStats.getTotalCostOriginal().divide(gStats.getTotalShares(), 4, RoundingMode.HALF_UP);
                avgCostTWD = gStats.getTotalCostTWD().divide(gStats.getTotalShares(), 4, RoundingMode.HALF_UP);
            }

            BigDecimal currentPrice = mkt.getPrice() != null ? mkt.getPrice() : BigDecimal.ZERO;
            BigDecimal costTWD = currentPrice.multiply(ratePrice).setScale(0, RoundingMode.HALF_UP);
            
            String link;
            if (isTaiwanStock(s.getSymbol())) {
                String cleanNo = s.getSymbol().split("\\.")[0];
                link = "https://tw.stock.yahoo.com/quote/" + cleanNo;
            } else {
                link = "https://finance.yahoo.com/quote/" + s.getSymbol();
            }

            portfolio.add(DashboardResponseDto.PortfolioItem.builder()
                    .symbol(s.getSymbol()).name(s.getName()).currency(s.getCurrency())
                    .price(currentPrice).prevClose(mkt.getPrevClose())
                    .high(mkt.getHigh() != null ? mkt.getHigh() : BigDecimal.ZERO)
                    .low(mkt.getLow() != null ? mkt.getLow() : BigDecimal.ZERO)
                    .fiftyTwoWeekHigh(mkt.getFiftyTwoWeekHigh())
                    .fiftyTwoWeekLow(mkt.getFiftyTwoWeekLow())
                    .volume(mkt.getVolume())
                    .avgVolume(mkt.getAvgVolume())
                    .marketStatus(mkt.getMarketStatus()).postMarketPrice(mkt.getPostMarketPrice())
                    .rate(ratePrice).costTWD(costTWD).avgCost(avgCost).avgCostTWD(avgCostTWD)
                    .link(link).build());
        }
        return portfolio;
    }

    private String resolveStockName(JsonNode first, String defaultSymbol) {
        if (first.has(KEY_SHORTNAME))
            return first.path(KEY_SHORTNAME).asText();
        if (first.has(KEY_LONGNAME))
            return first.path(KEY_LONGNAME).asText();
        return defaultSymbol;
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchStock(@RequestParam("q") String query) {
        try {
            JsonNode result = yahooFinanceClient.search(query);
            List<Map<String, String>> data = new ArrayList<>();
            if (result != null && result.has(KEY_QUOTES)) {
                for (JsonNode q : result.path(KEY_QUOTES)) {
                    if (q.has("isYahooFinance") && !q.path("isYahooFinance").asBoolean(true))
                        continue;
                    Map<String, String> item = new HashMap<>();
                    item.put(KEY_SYMBOL, q.path(KEY_SYMBOL).asText());
                    item.put("name", resolveStockName(q, q.path(KEY_SYMBOL).asText()));
                    item.put("exch", q.path("exchange").asText(""));
                    data.add(item);
                }
            }
            Map<String, Object> response = new HashMap<>();
            response.put("results", data);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/chart")
    public ResponseEntity<Map<String, Object>> getChartData(
            @RequestParam("symbol") String symbol,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "period", required = false, defaultValue = "1d") String period,
            @RequestParam(value = "fugleKey", required = false) String fugleKey) {
        try {
            String effectiveFugleKey = (fugleKey != null && !fugleKey.trim().isEmpty()) 
                    ? fugleKey : db.getConfig("FUGLE_API_KEY", "");

            String targetSymbol = symbol;
            if (symbol.matches("^\\d+$")) {
                targetSymbol = symbol + ".TW";
            }

            List<ChartDataDto> data;
            boolean isTW = isTaiwanStock(targetSymbol);

            if (isTW && "twse".equals(source))
                data = twStockService.fetchTwseMonthChart(targetSymbol);
            else if (isTW && "fugle".equals(source)) {
                if (effectiveFugleKey.isEmpty())
                    throw new RuntimeException("尚未設定富果 API Key (請至設定中填寫)");
                
                log.info("[Fugle Chart] 使用 API Key 長度: {}", effectiveFugleKey.length());
                data = twStockService.fetchFugleChart(targetSymbol, effectiveFugleKey, period);
            } else
                data = yahooFinanceClient.fetchChartData(targetSymbol, period);

            Map<String, Object> response = new HashMap<>();
            response.put(KEY_SUCCESS, true);
            response.put("data", data);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put(KEY_SUCCESS, false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PostMapping("/save-config")
    public ResponseEntity<Map<String, Boolean>> saveConfig(@RequestBody Map<String, String> req) {
        String key = req.get("key");
        String value = req.get("value");
        String desc = req.get("desc");
        if (key != null && value != null) {
            db.saveConfig(key, value, desc != null ? desc : "");
            return ResponseEntity.ok(Collections.singletonMap(KEY_SUCCESS, true));
        }
        return ResponseEntity.badRequest().build();
    }

    @GetMapping("/config/{key}")
    public ResponseEntity<Map<String, String>> getConfig(@PathVariable("key") String key) {
        String val = db.getConfig(key, "");
        return ResponseEntity.ok(Collections.singletonMap("value", val));
    }

    @PostMapping("/add-inventory")
    public ResponseEntity<Map<String, Boolean>> addInventory(@RequestBody InventoryRequest req) {
        com.example.golden_retriever_java.entity.InventoryEntity entity = com.example.golden_retriever_java.entity.InventoryEntity.builder()
                .symbol(req.getSymbol())
                .name((req.getName() != null && !req.getName().isEmpty()) ? req.getName() : req.getSymbol())
                .price(req.getPrice() != null ? req.getPrice() : BigDecimal.ZERO)
                .shares(req.getShares() != null ? req.getShares() : BigDecimal.ZERO)
                .exchangeRate(req.getExchangeRate() != null ? req.getExchangeRate() : BigDecimal.ONE)
                .buyDate(req.getDate() != null ? req.getDate() : "")
                .build();

        db.addInventory(entity);
        return ResponseEntity.ok(Collections.singletonMap(KEY_SUCCESS, true));
    }

    @PostMapping("/remove-inventory")
    public ResponseEntity<Map<String, Boolean>> removeInventory(@RequestBody InventoryRequest req) {
        db.removeInventory(Long.parseLong(req.getId()));
        return ResponseEntity.ok(Collections.singletonMap(KEY_SUCCESS, true));
    }

    @PostMapping("/remove-inventory-group")
    public ResponseEntity<Map<String, Boolean>> removeInventoryGroup(@RequestBody InventoryRequest req) {
        db.removeInventoryGroup(req.getSymbol());
        return ResponseEntity.ok(Collections.singletonMap(KEY_SUCCESS, true));
    }

    @PostMapping("/add-stock")
    public ResponseEntity<Map<String, Boolean>> addStock(@RequestBody StockRequest req) {
        String symbol = req.getSymbol();
        if (symbol != null && symbol.matches("^\\d+$")) {
            symbol = symbol + ".TW";
        }

        String currency = isTaiwanStock(symbol) ? "TWD" : CURRENCY_USD;
        String name = (req.getNewName() != null && !req.getNewName().isEmpty()) ? req.getNewName() : symbol;

        if (req.getNewName() == null || req.getNewName().isEmpty()) {
            try {
                JsonNode results = yahooFinanceClient.search(symbol);
                if (results != null && results.has(KEY_QUOTES) && results.path(KEY_QUOTES).isArray()
                        && !results.path(KEY_QUOTES).isEmpty()) {
                    name = resolveStockName(results.path(KEY_QUOTES).get(0), symbol);
                }
            } catch (Exception ignored) {
                log.warn("無法自動取得股票名稱，使用預設值: {}", symbol);
            }
        }

        db.addStock(symbol, currency, name);
        return ResponseEntity.ok(Collections.singletonMap(KEY_SUCCESS, true));
    }

    @PostMapping("/remove-stock")
    public ResponseEntity<Map<String, Boolean>> removeStock(@RequestBody StockRequest req) {
        db.removeStock(req.getSymbol());
        return ResponseEntity.ok(Collections.singletonMap(KEY_SUCCESS, true));
    }

    @PostMapping("/rename-stock")
    public ResponseEntity<Map<String, Boolean>> renameStock(@RequestBody StockRequest req) {
        db.renameStock(req.getSymbol(), req.getNewName());
        return ResponseEntity.ok(Collections.singletonMap(KEY_SUCCESS, true));
    }

    @PostMapping("/reorder-stocks")
    public ResponseEntity<Map<String, Boolean>> reorderStocks(@RequestBody StockRequest req) {
        db.updateStockOrder(req.getNewOrder());
        return ResponseEntity.ok(Collections.singletonMap(KEY_SUCCESS, true));
    }

    @PostMapping("/add-currency")
    public ResponseEntity<Map<String, Boolean>> addCurrency(@RequestBody CurrencyRequest req) {
        db.addCurrency(req.getCurrency().toUpperCase());
        return ResponseEntity.ok(Collections.singletonMap(KEY_SUCCESS, true));
    }

    @PostMapping("/remove-currency")
    public ResponseEntity<Map<String, Boolean>> removeCurrency(@RequestBody CurrencyRequest req) {
        db.removeCurrency(req.getCurrency());
        return ResponseEntity.ok(Collections.singletonMap(KEY_SUCCESS, true));
    }

    @PostMapping("/reorder-currencies")
    public ResponseEntity<Map<String, Boolean>> reorderCurrencies(@RequestBody CurrencyRequest req) {
        db.updateCurrencyOrder(req.getNewOrder());
        return ResponseEntity.ok(Collections.singletonMap(KEY_SUCCESS, true));
    }

    @PostMapping("/sell-inventory")
    public ResponseEntity<Map<String, Boolean>> sellInventory(@RequestBody SellRequest req) {
        db.sellInventory(req.getInventoryId(), req.getShares(), req.getSellPrice(), 
                         req.getSellRate(), req.getSellDate());
        return ResponseEntity.ok(Collections.singletonMap(KEY_SUCCESS, true));
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<com.example.golden_retriever_java.entity.TransactionEntity>> getTransactions() {
        return ResponseEntity.ok(db.getTransactions());
    }

    @PostMapping("/log")
    public ResponseEntity<Map<String, Boolean>> logClientError(@RequestBody LogRequest req) {
        log.error("[Frontend Error] Context: {} | Message: {}\nStack: {}", req.getContext(), req.getMessage(), req.getStack());
        return ResponseEntity.ok(Collections.singletonMap(KEY_SUCCESS, true));
    }
}