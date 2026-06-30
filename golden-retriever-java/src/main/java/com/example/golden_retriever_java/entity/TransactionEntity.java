package com.example.golden_retriever_java.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String symbol;
    private String name;
    
    @Column(precision = 18, scale = 4)
    private BigDecimal shares;
    
    @Column(precision = 18, scale = 4)
    private BigDecimal buyPrice;
    
    @Column(precision = 18, scale = 4)
    private BigDecimal sellPrice;
    
    @Column(precision = 18, scale = 6)
    private BigDecimal buyExchangeRate;
    
    @Column(precision = 18, scale = 6)
    private BigDecimal sellExchangeRate;
    
    @Column(precision = 18, scale = 2)
    private BigDecimal profitTWD;
    
    private String buyDate;
    private String sellDate;
}
