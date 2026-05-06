package com.example.golden_retriever_java.dto; 

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPortfolioData {
    @Builder.Default
    private List<TargetStock> stocks = new ArrayList<>();
    @Builder.Default
    private List<String> currencies = new ArrayList<>();
    @Builder.Default
    private List<InventoryItem> inventory = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TargetStock {
        private String symbol;
        private String currency;
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InventoryItem {
        private String id;
        private String symbol;
        private String name;
        private BigDecimal price;
        private BigDecimal shares;
        private String date;
        private BigDecimal exchangeRate;
        private BigDecimal usedRate; // 運算後附加的屬性
    }
}