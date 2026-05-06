package com.example.golden_retriever_java.service;

import com.example.golden_retriever_java.dto.UserPortfolioData;
import com.example.golden_retriever_java.entity.CurrencyEntity;
import com.example.golden_retriever_java.entity.InventoryEntity;
import com.example.golden_retriever_java.entity.StockEntity;
import com.example.golden_retriever_java.repository.CurrencyRepository;
import com.example.golden_retriever_java.repository.InventoryRepository;
import com.example.golden_retriever_java.repository.StockRepository;
import com.example.golden_retriever_java.repository.TransactionRepository;
import com.example.golden_retriever_java.repository.DividendRepository;
import com.example.golden_retriever_java.entity.TransactionEntity;
import com.example.golden_retriever_java.entity.DividendEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class DataService {
    private static final Logger log = LoggerFactory.getLogger(DataService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final StockRepository stockRepo;
    private final InventoryRepository inventoryRepo;
    private final CurrencyRepository currencyRepo;
    private final TransactionRepository transactionRepo;
    private final DividendRepository dividendRepo;
    private final com.example.golden_retriever_java.repository.ConfigRepository configRepo;

    public DataService(StockRepository stockRepo, InventoryRepository inventoryRepo, 
                       CurrencyRepository currencyRepo, TransactionRepository transactionRepo,
                       DividendRepository dividendRepo,
                       com.example.golden_retriever_java.repository.ConfigRepository configRepo) {
        this.stockRepo = stockRepo;
        this.inventoryRepo = inventoryRepo;
        this.currencyRepo = currencyRepo;
        this.transactionRepo = transactionRepo;
        this.dividendRepo = dividendRepo;
        this.configRepo = configRepo;
    }

    // ==========================================
    // 🚀 系統設定 (Configuration) 管理 - 加入記憶體快取
    // ==========================================
    private final Map<String, String> configCache = new ConcurrentHashMap<>();

    public String getConfig(String key, String defaultValue) {
        return configCache.computeIfAbsent(key, k -> 
            configRepo.findByKey(k)
                .map(com.example.golden_retriever_java.entity.ConfigEntity::getValue)
                .orElse(defaultValue)
        );
    }

    public void saveConfig(String key, String value, String description) {
        com.example.golden_retriever_java.entity.ConfigEntity entity = configRepo.findByKey(key)
                .orElse(com.example.golden_retriever_java.entity.ConfigEntity.builder().key(key).build());
        entity.setValue(value);
        entity.setDescription(description);
        configRepo.save(entity);
        configCache.put(key, value); // 更新快取
    }

    public static void ensureDatabaseExists(String url, String username, String password) {
        String dbName = "";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("databaseName=([^;]*)").matcher(url);
        if (matcher.find()) {
            dbName = matcher.group(1);
        }
        if (dbName.isEmpty()) return;
        String masterUrl = url.replaceAll("databaseName=[^;]*", "databaseName=master");
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(masterUrl, username, password);
             java.sql.Statement stmt = conn.createStatement()) {
            String checkSql = "SELECT db_id('" + dbName + "')";
            java.sql.ResultSet rs = stmt.executeQuery(checkSql);
            if (rs.next() && rs.getString(1) == null) {
                log.info("[DataService] 偵測到資料庫 {} 不存在，正在自動建立...", dbName);
                stmt.executeUpdate("CREATE DATABASE " + dbName);
                log.info("[DataService] 資料庫 {} 建立成功。", dbName);
            }
        } catch (Exception e) {
            log.warn("[DataService] 自動建庫嘗試失敗: {}", e.getMessage());
        }
    }

    @PostConstruct
    public void init() {
        if (stockRepo.count() == 0) {
            log.info("[DataService] 資料庫為空，嘗試從 JSON 檔案遷移資料...");
            initializeFromFiles();
        }
    }

    private void initializeFromFiles() {
        Path targetsFile = Paths.get(System.getProperty("user.dir"), "targets.json");
        Path assetsFile = Paths.get(System.getProperty("user.dir"), "assets.json");
        try {
            if (Files.exists(targetsFile)) {
                UserPortfolioData data = objectMapper.readValue(targetsFile.toFile(), UserPortfolioData.class);
                if (data.getStocks() != null) {
                    stockRepo.saveAll(data.getStocks().stream()
                            .map(s -> new StockEntity(s.getSymbol(), s.getName(), s.getCurrency()))
                            .toList());
                }
                if (data.getCurrencies() != null) {
                    currencyRepo.saveAll(data.getCurrencies().stream()
                            .map(CurrencyEntity::new)
                            .toList());
                }
            }
            if (Files.exists(assetsFile)) {
                UserPortfolioData data = objectMapper.readValue(assetsFile.toFile(), UserPortfolioData.class);
                if (data.getInventory() != null) {
                    inventoryRepo.saveAll(data.getInventory().stream()
                            .map(i -> InventoryEntity.builder()
                                    .symbol(i.getSymbol()).name(i.getName())
                                    .price(i.getPrice()).shares(i.getShares())
                                    .buyDate(i.getDate()).exchangeRate(i.getExchangeRate())
                                    .build())
                            .toList());
                }
            }
        } catch (Exception e) {
            log.error("[DataService] 遷移失敗: {}", e.getMessage());
        }
    }

    public UserPortfolioData getData() {
        List<StockEntity> stocks = stockRepo.findAll(Sort.by("sortOrder").ascending());
        List<InventoryEntity> inventory = inventoryRepo.findAll();
        List<CurrencyEntity> currencies = currencyRepo.findAll(Sort.by("sortOrder").ascending());

        UserPortfolioData data = new UserPortfolioData();
        data.setStocks(stocks.stream()
                .map(s -> new UserPortfolioData.TargetStock(s.getSymbol(), s.getCurrency(), s.getName()))
                .collect(Collectors.toCollection(ArrayList::new)));

        data.setInventory(inventory.stream()
                .map(i -> {
                    UserPortfolioData.InventoryItem item = new UserPortfolioData.InventoryItem();
                    item.setId(String.valueOf(i.getId()));
                    item.setSymbol(i.getSymbol());
                    item.setName(i.getName());
                    item.setPrice(i.getPrice());
                    item.setShares(i.getShares());
                    item.setDate(i.getBuyDate());
                    item.setExchangeRate(i.getExchangeRate());
                    return item;
                }).collect(Collectors.toCollection(ArrayList::new)));

        data.setCurrencies(currencies.stream()
                .map(CurrencyEntity::getCode)
                .collect(Collectors.toCollection(ArrayList::new)));
        return data;
    }

    @Transactional
    public void addStock(String symbol, String currency, String name) {
        long count = stockRepo.count();
        stockRepo.save(new StockEntity(symbol, name, currency, (int)count));
    }

    @Transactional
    public void removeStock(String symbol) {
        stockRepo.deleteById(symbol);
    }

    @Transactional
    public void renameStock(String symbol, String newName) {
        stockRepo.findById(symbol).ifPresent(s -> {
            s.setName(newName);
            stockRepo.save(s);
        });
    }

    @Transactional
    public void addInventory(InventoryEntity entity) {
        inventoryRepo.save(entity);
    }

    @Transactional
    public void removeInventory(Long id) {
        inventoryRepo.deleteById(id);
    }

    @Transactional
    public void removeInventoryGroup(String symbol) {
        inventoryRepo.deleteBySymbol(symbol);
    }

    @Transactional
    public void sellInventory(Long inventoryId, java.math.BigDecimal sellShares, 
                              java.math.BigDecimal sellPrice, java.math.BigDecimal sellRate, 
                              String sellDate) {
        InventoryEntity inv = inventoryRepo.findById(inventoryId)
                .orElseThrow(() -> new RuntimeException("找不到庫存記錄: " + inventoryId));

        if (sellShares.compareTo(inv.getShares()) > 0) {
            throw new RuntimeException("賣出股數超過持有股數!");
        }

        // 1. 計算損益 (已實現)
        java.math.BigDecimal sellValueTWD = sellShares.multiply(sellPrice).multiply(sellRate);
        java.math.BigDecimal buyValueTWD = sellShares.multiply(inv.getPrice()).multiply(inv.getExchangeRate());
        java.math.BigDecimal profitTWD = sellValueTWD.subtract(buyValueTWD);

        // 2. 存入交易記錄
        TransactionEntity tx = TransactionEntity.builder()
                .symbol(inv.getSymbol())
                .name(inv.getName())
                .shares(sellShares)
                .buyPrice(inv.getPrice())
                .sellPrice(sellPrice)
                .buyExchangeRate(inv.getExchangeRate())
                .sellExchangeRate(sellRate)
                .profitTWD(profitTWD.setScale(0, java.math.RoundingMode.HALF_UP))
                .buyDate(inv.getBuyDate())
                .sellDate(sellDate)
                .build();
        transactionRepo.save(tx);

        // 3. 更新或刪除庫存
        if (sellShares.compareTo(inv.getShares()) == 0) {
            inventoryRepo.delete(inv);
        } else {
            inv.setShares(inv.getShares().subtract(sellShares));
            inventoryRepo.save(inv);
        }
    }

    public List<TransactionEntity> getTransactions() {
        return transactionRepo.findAll(org.springframework.data.domain.Sort.by("sellDate").descending());
    }

    @Transactional
    public void addDividend(DividendEntity entity) {
        if (entity.getAmount() != null && entity.getExchangeRate() != null) {
            entity.setAmountTWD(entity.getAmount().multiply(entity.getExchangeRate())
                    .setScale(0, java.math.RoundingMode.HALF_UP));
        }
        dividendRepo.save(entity);
    }

    public List<DividendEntity> getDividends() {
        return dividendRepo.findAll(org.springframework.data.domain.Sort.by("date").descending());
    }

    public java.util.Map<String, java.math.BigDecimal> getTotalDividendsBySymbol() {
        return dividendRepo.findAll().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        DividendEntity::getSymbol,
                        java.util.stream.Collectors.reducing(
                                java.math.BigDecimal.ZERO,
                                DividendEntity::getAmountTWD,
                                java.math.BigDecimal::add
                        )
                ));
    }

    @Transactional
    public void addCurrency(String code) {
        long count = currencyRepo.count();
        currencyRepo.save(new CurrencyEntity(code, (int)count));
    }

    @Transactional
    public void removeCurrency(String code) {
        currencyRepo.deleteById(code);
    }

    @Transactional
    public void updateStockOrder(List<String> symbols) {
        for (int i = 0; i < symbols.size(); i++) {
            final int order = i;
            stockRepo.findById(symbols.get(i)).ifPresent(s -> {
                s.setSortOrder(order);
                stockRepo.save(s);
            });
        }
    }

    @Transactional
    public void updateCurrencyOrder(List<String> codes) {
        for (int i = 0; i < codes.size(); i++) {
            final int order = i;
            currencyRepo.findById(codes.get(i)).ifPresent(c -> {
                c.setSortOrder(order);
                currencyRepo.save(c);
            });
        }
    }

    /**
     * 全量同步方法 (過時)。
     * @deprecated 請改用增量更新方法。
     */
    @Deprecated(since = "2.0", forRemoval = false)
    @Transactional
    public void save(UserPortfolioData fullData) {
        log.warn("[DataService] 調用了過時的全量同步方法。");
        stockRepo.saveAll(fullData.getStocks().stream()
                .map(s -> new StockEntity(s.getSymbol(), s.getName(), s.getCurrency()))
                .toList());
    }
}
