package com.example.golden_retriever_java.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockQuoteDto {
    private BigDecimal price;
    private BigDecimal prevClose;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal fiftyTwoWeekHigh;
    private BigDecimal fiftyTwoWeekLow;
    private Long volume;
    private Long avgVolume;
    private String currency;
    private BigDecimal postMarketPrice;
    private String marketStatus;
    private BigDecimal nav;
    private Double premium;
    private BigDecimal peRatio;
    private BigDecimal dividendYield;
    private BigDecimal pbRatio;
    private Long foreignBuy;
    private Long trustBuy;
}