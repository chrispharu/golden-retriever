package com.example.golden_retriever_java.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

// [MODIFIED] 原因: 擴充欄位以 100% 對齊原 Node.js 中 fetchQuotes 回傳的資料結構
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockQuoteDto {
    private BigDecimal price; // 現價
    private BigDecimal prevClose; // 昨收
    private BigDecimal high; // 最高
    private BigDecimal low; // 最低
    private String currency; // 幣別
    private BigDecimal postMarketPrice; // 盤後價
    private String marketStatus; // 開/收盤狀態 (OPEN/CLOSED)
}