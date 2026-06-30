package com.example.golden_retriever_java.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "stocks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockEntity {
    @Id
    private String symbol; 
    private String name;
    private String currency;

    @Column(name = "sort_order")
    private Integer sortOrder;

    // ETF 淨值相關 (Open Data 盤後同步)
    private java.math.BigDecimal nav;
    private Double premium;

    // 基本面相關 (Open Data 盤後同步)
    private java.math.BigDecimal peRatio;
    private java.math.BigDecimal dividendYield;
    private java.math.BigDecimal pbRatio;

    // 法人買賣超 (張)
    private Long foreignBuy;
    private Long trustBuy;

    // [NEW] 增加便利建構子，修復 DataService 中的編譯錯誤
    public StockEntity(String symbol, String name, String currency) {
        this(symbol, name, currency, 0);
    }

    public StockEntity(String symbol, String name, String currency, Integer sortOrder) {
        this.symbol = symbol;
        this.name = name;
        this.currency = currency;
        this.sortOrder = sortOrder;
        this.nav = java.math.BigDecimal.ZERO;
        this.premium = 0.0;
    }
}
