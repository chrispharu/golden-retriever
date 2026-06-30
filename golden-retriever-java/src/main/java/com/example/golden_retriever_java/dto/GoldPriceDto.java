package com.example.golden_retriever_java.dto; 

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

// [MODIFIED] 100% 對齊原專案黃金報價結構，支援台銀爬蟲回傳格式
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoldPriceDto {
    private String symbol; // 如: 黃金存摺
    private BigDecimal current; // 現價 (TWD/公克)
    private BigDecimal priceUSD; // 美金現價 (保留備用)
    private BigDecimal priceTWD; // 換算台幣價 (保留相容)
    private BigDecimal high; // 今日最高
    private BigDecimal prevClose; // 昨收
    private BigDecimal change; // 漲跌
    private boolean isFallback; // 是否為備援模式
    private String updateTime; // 更新時間戳記
}
