package com.example.golden_retriever_java.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class DashboardResponseDto {
    private String updatedAt;
    private List<PortfolioItem> portfolio;
    private List<GroupedInventory> groupedInventory;
    private GoldPriceDto gold;
    private Map<String, BenchmarkItem> benchmarks;
    private AllocationDto allocation;
    private BigDecimal totalValueUsd; // 補回遺失欄位

    @Data
    @Builder
    public static class AllocationDto {
        private Map<String, BigDecimal> byCurrency;
        private Map<String, BigDecimal> byAsset;
        private Map<String, BigDecimal> byType;
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
        private BigDecimal marketValueTWD; // 補回遺失屬性
        private BigDecimal avgCost;
        private BigDecimal avgCostTWD;
        private String link;
        private BigDecimal nav;
        private Double premium;
        private BigDecimal peRatio;
        private BigDecimal dividendYield;
        private BigDecimal pbRatio;
        private Long foreignBuy;
        private Long trustBuy;
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

        private Double aiScore;
        private Double roe;
        private BigDecimal peRatio;
        private BigDecimal dividendYield;
        private BigDecimal pbRatio;
        private Double bias;
        private Long foreignBuy;
        private Long trustBuy;

        private BigDecimal fiftyTwoWeekHigh;
        private BigDecimal fiftyTwoWeekLow;
        private Long volume;
        private Long avgVolume;
        private BigDecimal nav;
        private Double premium;
    }
}