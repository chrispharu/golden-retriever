package com.example.golden_retriever_java.entity;

import jakarta.persistence.*; // 使用萬用字元匯入，確保包含 Column, Entity, Id, Table 等
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "currencies")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurrencyEntity {
    @Id
    private String code; // 如 USD, JPY

    @Column(name = "sort_order")
    private Integer sortOrder;

    public CurrencyEntity(String code) {
        this.code = code;
        this.sortOrder = 0;
    }
}
