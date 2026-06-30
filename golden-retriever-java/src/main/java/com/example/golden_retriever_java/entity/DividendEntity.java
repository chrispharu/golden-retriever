package com.example.golden_retriever_java.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "dividends")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DividendEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String symbol;
    private String name;
    
    @Column(precision = 18, scale = 4)
    private BigDecimal amount; // 原始幣別總額
    
    @Column(precision = 18, scale = 6)
    private BigDecimal exchangeRate; // 配息當時匯率
    
    @Column(precision = 18, scale = 2)
    private BigDecimal amountTWD; // 台幣總額 (amount * exchangeRate)
    
    private String date; // 領取日期
    private String type; // CASH_DIVIDEND (現金股利) or STOCK_DIVIDEND (股票股利)
}
