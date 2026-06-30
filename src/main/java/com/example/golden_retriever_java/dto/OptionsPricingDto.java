package com.example.golden_retriever_java.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

public class OptionsPricingDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        private double s;     // Underlying Price
        private double k;     // Strike Price
        private double t;     // Time to Maturity (Years)
        private double r;     // Risk-free Rate (e.g. 0.05)
        private double sigma; // Volatility (e.g. 0.2)
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private double callPrice;
        private double putPrice;
        private double callDelta;
        private double putDelta;
        private double gamma;
        private double vega;
        private double callTheta;
        private double putTheta;
        private double callRho;
        private double putRho;
        private long timestamp;
    }
}
