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

    // [NEW] 增加便利建構子，修復 DataService 中的編譯錯誤
    public StockEntity(String symbol, String name, String currency) {
        this.symbol = symbol;
        this.name = name;
        this.currency = currency;
        this.sortOrder = 0; // 預設排序
    }
}
