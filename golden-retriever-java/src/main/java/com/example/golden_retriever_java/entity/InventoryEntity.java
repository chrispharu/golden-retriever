package com.example.golden_retriever_java.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "inventory")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String symbol;
    private String name;
    
    @Column(precision = 18, scale = 4)
    private BigDecimal price;
    
    @Column(precision = 18, scale = 4)
    private BigDecimal shares;
    
    private String buyDate;
    
    @Column(precision = 18, scale = 6)
    private BigDecimal exchangeRate;
}
