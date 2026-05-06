package com.example.golden_retriever_java.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

// 定義前端儀表板所需的複雜回應結構
@Data
@Builder
public class DashboardResponseDto {
    private String updatedAt;
    private List<PortfolioItem> portfolio;
    private List<GroupedInventory> groupedInventory;
    private GoldPriceDto gold;
    private Map<String, BenchmarkItem> benchmarks;
    private AllocationDto allocation;

    @Data
    @Builder
    public static class AllocationDto {
        private Map<String, BigDecimal> byCurrency; // 幣別佔比 (TWD 總額)
        private Map<String, BigDecimal> byAsset;    // 資產佔比 (標的總額)
        private Map<String, BigDecimal> byType;     // 類型佔比 (TW vs US)
    }

    @Data
    @Builder
    public static class BenchmarkItem {
        private String symbol;
        private String name;
        private BigDecimal price;
        private BigDecimal changePercent;
    }

    @Data
    @Builder
    public static class PortfolioItem {
        private String symbol;
        private String name;
        private String currency;
        private BigDecimal price;
        private BigDecimal prevClose;
        private BigDecimal high;
        private BigDecimal low;
        private BigDecimal fiftyTwoWeekHigh;
        private BigDecimal fiftyTwoWeekLow;
        private Long volume;
        private Long avgVolume;
        private String marketStatus;
        private BigDecimal postMarketPrice;
        private BigDecimal rate;
        private BigDecimal costTWD;
        private BigDecimal avgCost;
        private BigDecimal avgCostTWD;
        private String link;
    }

    @Data
    @Builder
    public static class GroupedInventory {
        private String symbol;
        private String name;
        private boolean isTW;
        private BigDecimal totalShares;
        private BigDecimal totalCostTWD;
        private BigDecimal totalCostOriginal;
        private List<UserPortfolioData.InventoryItem> records;

        private BigDecimal currentPrice;
        private String marketStatus;
        private BigDecimal postMarketPrice;
        private BigDecimal currentRate;
        private BigDecimal marketValueTWD;
        private BigDecimal roi;
        private BigDecimal plAmountTWD;
        private BigDecimal pricePLTWD;
        private BigDecimal exchangePLTWD;
        private BigDecimal totalDividendsTWD;
        private boolean hasValidRate;

        // [NEW] FinLab 量化數據整合
        private Double aiScore;
        private Double roe;
        private Double pe;
        private Double bias;      // 均線乖離率
        private Integer foreignBuy; // 外資買賣超(股)
        private Integer trustBuy;   // 投信買賣超(股)

        private BigDecimal fiftyTwoWeekHigh;
        private BigDecimal fiftyTwoWeekLow;
        private Long volume;
        private Long avgVolume;
    }
}