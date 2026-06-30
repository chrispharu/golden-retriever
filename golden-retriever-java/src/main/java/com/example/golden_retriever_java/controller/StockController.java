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

    public StockController(YahooFinanceClient yahooFinanceClient,
            TwStockService twStockService,
            DataService db,
            GoldService goldService) {
        this.yahooFinanceClient = yahooFinanceClient;
        this.twStockService = twStockService;
        this.db = db;
        this.goldService = goldService;
    }

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

    private boolean isTaiwanStock(String symbol) {
        if (symbol == null) return false;
        return symbol.matches("^\\d+$") || symbol.contains(".TW") || symbol.contains(".TWO");
    }

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

    @GetMapping("/inventory/reference-data")
    public ResponseEntity<Map<String, Object>> getInventoryReferenceData(
            @RequestParam("symbol") String symbol,
            @RequestParam(value = "date", required = false) String date) {
        try {
            String currency = isTaiwanStock(symbol) ? "TWD" : CURRENCY_USD;
            BigDecimal price = BigDecimal.ZERO;
            BigDecimal exchangeRate = BigDecimal.ONE;
            String today = LocalDateTime.now(ZoneId.of("Asia/Taipei")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            if (date == null || date.isEmpty() || date.equals(today)) {
                Map<String, StockQuoteDto> quotes = yahooFinanceClient.fetchQuotes(Collections.singletonList(symbol));
                if (quotes.containsKey(symbol)) price = quotes.get(symbol).getPrice();
            } else {
                List<ChartDataDto> chartData = yahooFinanceClient.fetchChartData(symbol, "1d");
                price = chartData.stream().filter(d -> d.getTime().compareTo(date) <= 0).reduce((f, s) -> s).map(ChartDataDto::getClose).orElse(BigDecimal.ZERO);
            }
            if (!"TWD".equals(currency)) {
                Map<String, Map<String, BigDecimal>> rates = yahooFinanceClient.fetchExchangeRates(Collections.singletonList(currency));
                if (rates.containsKey(currency)) exchangeRate = rates.get(currency).get(KEY_PRICE);
            }
            Map<String, Object> response = new HashMap<>();
            response.put("symbol", symbol); response.put("price", price); response.put("exchangeRate", exchangeRate); response.put("currency", currency);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("獲取參考數據失敗: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/stocks")
    public ResponseEntity<DashboardResponseDto> getStocksData(
            @RequestHeader(value = "x-fugle-key", required = false, defaultValue = "") String headerKey,
            @RequestHeader(value = "Authorization", required = false, defaultValue = "") String authHeader) {
        log.info("[Stocks Controller] 🚀 收到完整儀表板請求");
        try {
            String effectiveFugleKey = headerKey;
            if (effectiveFugleKey.isEmpty() && authHeader.startsWith("Bearer ")) {
                effectiveFugleKey = authHeader.substring(7).trim();
            }
            if (effectiveFugleKey.isEmpty()) effectiveFugleKey = db.getConfig("FUGLE_API_KEY", "");

            UserPortfolioData userData = db.getData();
            List<UserPortfolioData.TargetStock> stocks = userData.getStocks();
            List<UserPortfolioData.InventoryItem> inventory = userData.getInventory();
            List<String> currencies = userData.getCurrencies().isEmpty() ? Collections.singletonList(CURRENCY_USD) : userData.getCurrencies();

            Set<String> uniqueSymbols = new HashSet<>();
            stocks.forEach(s -> uniqueSymbols.add(s.getSymbol()));
            inventory.forEach(i -> uniqueSymbols.add(i.getSymbol()));
            List<String> symbols = new ArrayList<>(uniqueSymbols);

            List<String> twSymbols = symbols.stream().filter(this::isTaiwanStock).collect(Collectors.toList());
            List<String> usSymbols = symbols.stream().filter(s -> !isTaiwanStock(s)).collect(Collectors.toList());

            if (!twSymbols.isEmpty() && effectiveFugleKey.isEmpty()) {
                log.warn("[Stocks Controller] 無 Fugle Key，台股回退至 Yahoo");
                usSymbols.addAll(twSymbols);
                twSymbols.clear();
            }

            Map<String, Map<String, BigDecimal>> ratesMap = yahooFinanceClient.fetchExchangeRates(currencies);
            Map<String, StockQuoteDto> stockData = new HashMap<>(yahooFinanceClient.fetchQuotes(usSymbols));

            if (!twSymbols.isEmpty()) {
                stockData.putAll(twStockService.fetchFugleQuotes(twSymbols, effectiveFugleKey));
            }

            // 注入 ETF 淨值與基本面
            log.info("[Stocks Controller] 📊 注入基本面數據 (包含資料庫持久化數據)...");
            Map<String, Map<String, Object>> dbNavs = twStockService.fetchAllTwEtfNavs();
            
            // 修正：必須確保 uniqueSymbols 中的每一檔台股都嘗試從數據源 Map 補回基本面
            symbols.stream().filter(this::isTaiwanStock).forEach(symbol -> {
                String cleanSym = symbol.split("\\.")[0];
                // 優先使用完整 Symbol (2330.TW) 匹配，其次使用 Clean Symbol (2330)
                Map<String, Object> fund = dbNavs.get(symbol);
                if (fund == null) fund = dbNavs.get(cleanSym);
                
                if (fund != null) {
                    StockQuoteDto dto = stockData.get(symbol);
                    if (dto == null) {
                        dto = StockQuoteDto.builder().price(BigDecimal.ZERO).build();
                        stockData.put(symbol, dto);
                    }
                    if (fund.containsKey("nav")) dto.setNav((BigDecimal) fund.get("nav"));
                    if (fund.containsKey("premium")) dto.setPremium((Double) fund.get("premium"));
                    if (fund.containsKey("peRatio")) dto.setPeRatio((BigDecimal) fund.get("peRatio"));
                    if (fund.containsKey("dividendYield")) dto.setDividendYield((BigDecimal) fund.get("dividendYield"));
                    if (fund.containsKey("pbRatio")) dto.setPbRatio((BigDecimal) fund.get("pbRatio"));
                    if (fund.containsKey("foreignBuy")) dto.setForeignBuy((Long) fund.get("foreignBuy"));
                    if (fund.containsKey("trustBuy")) dto.setTrustBuy((Long) fund.get("trustBuy"));
                    log.info("[Stocks Controller] ✅ {} 數據補強完成: NAV={}, Premium={}", symbol, dto.getNav(), dto.getPremium());
                } else {
                    log.debug("[Stocks Controller] ⚠️ {} 無法在數據源中找到補強資訊", symbol);
                }
            });

            Map<String, DashboardResponseDto.GroupedInventory> groupedInvMap = processGroupedInventory(inventory, stocks, ratesMap, stockData);
            List<DashboardResponseDto.PortfolioItem> portfolio = processPortfolio(stocks, ratesMap, stockData, groupedInvMap);
            
            GoldPriceDto gold = null;
            try { gold = goldService.fetchRealGoldPrice(); } catch (Exception e) { log.warn("黃金價格獲取失敗"); }

            Map<String, DashboardResponseDto.BenchmarkItem> benchmarks = new HashMap<>();
            List<String> benchmarkSymbols = Arrays.asList("^TWII", "^GSPC");
            Map<String, StockQuoteDto> bQuotes = yahooFinanceClient.fetchQuotes(benchmarkSymbols);
            bQuotes.forEach((sym, q) -> {
                BigDecimal cp = BigDecimal.ZERO;
                if (q.getPrevClose() != null && q.getPrevClose().compareTo(BigDecimal.ZERO) > 0) {
                    cp = q.getPrice().subtract(q.getPrevClose()).divide(q.getPrevClose(), 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
                }
                benchmarks.put(sym, DashboardResponseDto.BenchmarkItem.builder().symbol(sym).name(sym.equals("^TWII")?"加權指數":"標普 500").price(q.getPrice()).changePercent(cp).build());
            });

            DashboardResponseDto response = DashboardResponseDto.builder()
                    .updatedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")))
                    .portfolio(portfolio)
                    .groupedInventory(new ArrayList<>(groupedInvMap.values()))
                    .benchmarks(benchmarks)
                    .gold(gold)
                    .allocation(processAllocation(groupedInvMap))
                    .totalValueUsd(calculateTotalValueUsd(portfolio, ratesMap))
                    .build();

            log.info("[Stocks Controller] ✨ 完整數據包已發送");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[Stocks Controller] 嚴重異常: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private BigDecimal calculateTotalValueUsd(List<DashboardResponseDto.PortfolioItem> portfolio, Map<String, Map<String, BigDecimal>> ratesMap) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal usdtwd = (ratesMap.get(CURRENCY_USD) != null) ? ratesMap.get(CURRENCY_USD).get(KEY_PRICE) : new BigDecimal("32.3");
        for (DashboardResponseDto.PortfolioItem item : portfolio) {
            BigDecimal mvTWD = item.getMarketValueTWD() != null ? item.getMarketValueTWD() : BigDecimal.ZERO;
            if (mvTWD.compareTo(BigDecimal.ZERO) > 0) total = total.add(mvTWD.divide(usdtwd, 4, RoundingMode.HALF_UP));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private DashboardResponseDto.AllocationDto processAllocation(Map<String, DashboardResponseDto.GroupedInventory> groups) {
        Map<String, BigDecimal> byCurrency = new HashMap<>();
        Map<String, BigDecimal> byAsset = new HashMap<>();
        Map<String, BigDecimal> byType = new HashMap<>();
        for (DashboardResponseDto.GroupedInventory g : groups.values()) {
            BigDecimal mv = g.getMarketValueTWD();
            if (mv == null || mv.compareTo(BigDecimal.ZERO) <= 0) continue;
            byAsset.put(g.getName(), mv.setScale(0, RoundingMode.HALF_UP));
            String currencyKey = g.isTW() ? "TWD" : "USD";
            byCurrency.merge(currencyKey, mv, BigDecimal::add);
            String typeKey = g.isTW() ? "台股" : "美股";
            byType.merge(typeKey, mv, BigDecimal::add);
        }
        byCurrency.replaceAll((k,v) -> v.setScale(0, RoundingMode.HALF_UP));
        byType.replaceAll((k,v) -> v.setScale(0, RoundingMode.HALF_UP));
        return DashboardResponseDto.AllocationDto.builder().byCurrency(byCurrency).byAsset(byAsset).byType(byType).build();
    }

    private Map<String, DashboardResponseDto.GroupedInventory> processGroupedInventory(
            List<UserPortfolioData.InventoryItem> inventory,
            List<UserPortfolioData.TargetStock> stocks,
            Map<String, Map<String, BigDecimal>> ratesMap,
            Map<String, StockQuoteDto> stockData) {
        Map<String, DashboardResponseDto.GroupedInventory> groupedInvMap = new LinkedHashMap<>();
        Map<String, BigDecimal> dividendsMap = db.getTotalDividendsBySymbol();
        for (UserPortfolioData.InventoryItem inv : inventory) {
            if (inv.getSymbol() == null || inv.getSymbol().isEmpty()) continue;
            boolean isTW = isTaiwanStock(inv.getSymbol());
            final String resolvedName = (inv.getName() == null || inv.getName().isEmpty())
                    ? stocks.stream().filter(s -> s.getSymbol().equals(inv.getSymbol())).map(UserPortfolioData.TargetStock::getName).findFirst().orElse(inv.getSymbol())
                    : inv.getName();
            DashboardResponseDto.GroupedInventory group = groupedInvMap.computeIfAbsent(inv.getSymbol(), k -> DashboardResponseDto.GroupedInventory.builder().symbol(inv.getSymbol()).name(resolvedName).isTW(isTW).totalShares(BigDecimal.ZERO).totalCostTWD(BigDecimal.ZERO).totalCostOriginal(BigDecimal.ZERO).records(new ArrayList<>()).build());
            BigDecimal p = inv.getPrice(); BigDecimal s = inv.getShares(); BigDecimal r = inv.getExchangeRate();
            if (r == null || r.compareTo(BigDecimal.ZERO) <= 0) {
                if (isTW) r = BigDecimal.ONE;
                else r = (ratesMap.get(CURRENCY_USD) != null) ? ratesMap.get(CURRENCY_USD).get(KEY_PRICE) : BigDecimal.ZERO;
            }
            if (p != null && s != null && p.compareTo(BigDecimal.ZERO) > 0) {
                group.setTotalShares(group.getTotalShares().add(s));
                BigDecimal costOrig = p.multiply(s);
                group.setTotalCostOriginal(group.getTotalCostOriginal().add(costOrig));
                group.setTotalCostTWD(group.getTotalCostTWD().add(costOrig.multiply(r)));
            }
            inv.setUsedRate(r); group.getRecords().add(inv);
        }
        for (DashboardResponseDto.GroupedInventory g : groupedInvMap.values()) {
            String rawSym = g.getSymbol();
            String cleanSym = rawSym.split("\\.")[0];
            
            StockQuoteDto mkt = stockData.getOrDefault(rawSym, StockQuoteDto.builder().price(BigDecimal.ZERO).marketStatus("CLOSED").build());
            
            // 嘗試從數據地圖中獲取補強數據 (支援 006208.TW 或 006208 兩種 Key)
            // 注意：這裡的 mkt 對象可能已經包含了一些數據，我們要補強的是 NAV 等基本面
            BigDecimal currentRate = g.isTW() ? BigDecimal.ONE : (ratesMap.get(CURRENCY_USD) != null ? ratesMap.get(CURRENCY_USD).get(KEY_PRICE) : BigDecimal.ZERO);
            BigDecimal currentPrice = mkt.getPrice() != null ? mkt.getPrice() : BigDecimal.ZERO;
            BigDecimal marketValueTWD = currentPrice.multiply(g.getTotalShares()).multiply(currentRate);
            BigDecimal totalDividendsTWD = dividendsMap.getOrDefault(rawSym, BigDecimal.ZERO);
            
            g.setCurrentPrice(currentPrice); g.setMarketValueTWD(marketValueTWD); g.setTotalDividendsTWD(totalDividendsTWD);
            g.setMarketStatus(mkt.getMarketStatus());
            
            if (g.getTotalCostTWD().compareTo(BigDecimal.ZERO) > 0) {
                g.setRoi(marketValueTWD.subtract(g.getTotalCostTWD()).add(totalDividendsTWD).divide(g.getTotalCostTWD(), 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")));
                g.setPlAmountTWD(marketValueTWD.subtract(g.getTotalCostTWD()).setScale(0, RoundingMode.HALF_UP));
            }
            
            // 補強邏輯：如果 mkt 沒拿到 nav，嘗試從原始數據源 map 找
            g.setNav(mkt.getNav());
            g.setPremium(mkt.getPremium());
            g.setPeRatio(mkt.getPeRatio());
            g.setDividendYield(mkt.getDividendYield());
            g.setPbRatio(mkt.getPbRatio());
            g.setForeignBuy(mkt.getForeignBuy());
            g.setTrustBuy(mkt.getTrustBuy());
            
            if (g.getNav() != null) {
                log.info("[Stocks Controller] ✅ {} 數據封裝完成: NAV={}, Premium={}", rawSym, g.getNav(), g.getPremium());
            } else if (!g.isTW()) {
                log.info("[Stocks Controller] 🔍 正在處理美股 ETF 補強: {}", rawSym);
            }
        }
        return groupedInvMap;
    }

    private List<DashboardResponseDto.PortfolioItem> processPortfolio(
            List<UserPortfolioData.TargetStock> stocks,
            Map<String, Map<String, BigDecimal>> ratesMap,
            Map<String, StockQuoteDto> stockData,
            Map<String, DashboardResponseDto.GroupedInventory> groupedInvMap) {
        List<DashboardResponseDto.PortfolioItem> portfolio = new ArrayList<>();
        for (UserPortfolioData.TargetStock s : stocks) {
            StockQuoteDto mkt = stockData.getOrDefault(s.getSymbol(), StockQuoteDto.builder().price(BigDecimal.ZERO).marketStatus("CLOSED").build());
            BigDecimal ratePrice = (ratesMap.get(s.getCurrency()) != null) ? ratesMap.get(s.getCurrency()).get(KEY_PRICE) : BigDecimal.ONE;
            DashboardResponseDto.GroupedInventory gStats = groupedInvMap.get(s.getSymbol());
            BigDecimal avgCost = (gStats != null && gStats.getTotalShares().compareTo(BigDecimal.ZERO) > 0) ? gStats.getTotalCostOriginal().divide(gStats.getTotalShares(), 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            portfolio.add(DashboardResponseDto.PortfolioItem.builder()
                    .symbol(s.getSymbol()).name(s.getName()).currency(s.getCurrency())
                    .price(mkt.getPrice()).prevClose(mkt.getPrevClose())
                    .marketStatus(mkt.getMarketStatus()).nav(mkt.getNav()).premium(mkt.getPremium())
                    .peRatio(mkt.getPeRatio()).dividendYield(mkt.getDividendYield()).pbRatio(mkt.getPbRatio())
                    .foreignBuy(mkt.getForeignBuy()).trustBuy(mkt.getTrustBuy())
                    .marketValueTWD(gStats != null ? gStats.getMarketValueTWD() : BigDecimal.ZERO)
                    .avgCost(avgCost).build());
        }
        return portfolio;
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchStock(@RequestParam("q") String query) {
        try {
            JsonNode result = yahooFinanceClient.search(query);
            List<Map<String, String>> data = new ArrayList<>();
            if (result != null && result.has(KEY_QUOTES)) {
                for (JsonNode q : result.path(KEY_QUOTES)) {
                    Map<String, String> item = new HashMap<>();
                    item.put(KEY_SYMBOL, q.path(KEY_SYMBOL).asText());
                    item.put("name", q.has(KEY_SHORTNAME) ? q.path(KEY_SHORTNAME).asText() : q.path(KEY_SYMBOL).asText());
                    data.add(item);
                }
            }
            return ResponseEntity.ok(Map.of("results", data));
        } catch (Exception e) { return ResponseEntity.internalServerError().build(); }
    }

    @GetMapping("/chart")
    public ResponseEntity<Map<String, Object>> getChartData(@RequestParam("symbol") String symbol, @RequestParam(value = "source", required = false) String source, @RequestParam(value = "period", required = false, defaultValue = "1d") String period) {
        try {
            List<ChartDataDto> data;
            if (isTaiwanStock(symbol) && "twse".equals(source)) data = twStockService.fetchTwseMonthChart(symbol);
            else data = yahooFinanceClient.fetchChartData(symbol, period);
            return ResponseEntity.ok(Map.of(KEY_SUCCESS, true, "data", data));
        } catch (Exception e) { return ResponseEntity.badRequest().body(Map.of(KEY_SUCCESS, false, "error", e.getMessage())); }
    }

    @GetMapping("/config/{key}")
    public ResponseEntity<Map<String, String>> getConfig(@PathVariable("key") String key) {
        return ResponseEntity.ok(Map.of("value", db.getConfig(key, "")));
    }

    @PostMapping("/save-config")
    public ResponseEntity<Map<Boolean, Boolean>> saveConfig(@RequestBody Map<String, String> req) {
        db.saveConfig(req.get("key"), req.get("value"), req.getOrDefault("desc", ""));
        Map<Boolean, Boolean> res = new HashMap<>();
        res.put(true, true);
        return ResponseEntity.ok(res);
    }

    private String resolveStockName(JsonNode first, String defaultSymbol) {
        if (first.has(KEY_SHORTNAME))
            return first.path(KEY_SHORTNAME).asText();
        if (first.has(KEY_LONGNAME))
            return first.path(KEY_LONGNAME).asText();
        return defaultSymbol;
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
        return ResponseEntity.ok(Map.of(KEY_SUCCESS, true));
    }

    @PostMapping("/remove-stock")
    public ResponseEntity<Map<String, Boolean>> removeStock(@RequestBody StockRequest req) {
        db.removeStock(req.getSymbol());
        return ResponseEntity.ok(Map.of(KEY_SUCCESS, true));
    }

    @PostMapping("/rename-stock")
    public ResponseEntity<Map<String, Boolean>> renameStock(@RequestBody StockRequest req) {
        db.renameStock(req.getSymbol(), req.getNewName());
        return ResponseEntity.ok(Map.of(KEY_SUCCESS, true));
    }

    @PostMapping("/reorder-stocks")
    public ResponseEntity<Map<String, Boolean>> reorderStocks(@RequestBody StockRequest req) {
        db.updateStockOrder(req.getNewOrder());
        return ResponseEntity.ok(Map.of(KEY_SUCCESS, true));
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
        return ResponseEntity.ok(Map.of(KEY_SUCCESS, true));
    }

    @PostMapping("/remove-inventory")
    public ResponseEntity<Map<String, Boolean>> removeInventory(@RequestBody InventoryRequest req) {
        db.removeInventory(Long.parseLong(req.getId()));
        return ResponseEntity.ok(Map.of(KEY_SUCCESS, true));
    }

    @PostMapping("/remove-inventory-group")
    public ResponseEntity<Map<String, Boolean>> removeInventoryGroup(@RequestBody InventoryRequest req) {
        db.removeInventoryGroup(req.getSymbol());
        return ResponseEntity.ok(Map.of(KEY_SUCCESS, true));
    }

    @PostMapping("/add-currency")
    public ResponseEntity<Map<String, Boolean>> addCurrency(@RequestBody CurrencyRequest req) {
        db.addCurrency(req.getCurrency().toUpperCase());
        return ResponseEntity.ok(Map.of(KEY_SUCCESS, true));
    }

    @PostMapping("/remove-currency")
    public ResponseEntity<Map<String, Boolean>> removeCurrency(@RequestBody CurrencyRequest req) {
        db.removeCurrency(req.getCurrency());
        return ResponseEntity.ok(Map.of(KEY_SUCCESS, true));
    }

    @PostMapping("/reorder-currencies")
    public ResponseEntity<Map<String, Boolean>> reorderCurrencies(@RequestBody CurrencyRequest req) {
        db.updateCurrencyOrder(req.getNewOrder());
        return ResponseEntity.ok(Map.of(KEY_SUCCESS, true));
    }

    @PostMapping("/add-dividend")
    public ResponseEntity<Map<String, Boolean>> addDividend(@RequestBody DividendRequest req) {
        db.addDividend(com.example.golden_retriever_java.entity.DividendEntity.builder().symbol(req.getSymbol()).name(req.getName()).amount(req.getAmount()).exchangeRate(req.getExchangeRate()).date(req.getDate()).type(req.getType()).build());
        return ResponseEntity.ok(Map.of(KEY_SUCCESS, true));
    }

    @PostMapping("/sell-inventory")
    public ResponseEntity<Map<String, Boolean>> sellInventory(@RequestBody SellRequest req) {
        db.sellInventory(req.getInventoryId(), req.getShares(), req.getSellPrice(), req.getSellRate(), req.getSellDate());
        return ResponseEntity.ok(Map.of(KEY_SUCCESS, true));
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<com.example.golden_retriever_java.entity.TransactionEntity>> getTransactions() {
        return ResponseEntity.ok(db.getTransactions());
    }

    @PostMapping("/log")
    public ResponseEntity<Map<String, Boolean>> logClientError(@RequestBody LogRequest req) {
        log.error("[Frontend Error] Context: {} | Message: {}", req.getContext(), req.getMessage());
        return ResponseEntity.ok(Map.of(KEY_SUCCESS, true));
    }
}
