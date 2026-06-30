package com.example.golden_retriever_java.service;

import com.example.golden_retriever_java.dto.ChartDataDto;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RiskAnalysisService {

    private final YahooFinanceClient yahooClient;

    public RiskAnalysisService(YahooFinanceClient yahooClient) {
        this.yahooClient = yahooClient;
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RiskAnalysisService.class);

    public RiskReport analyze(String symbol, BigDecimal currentPrice) {
        log.info("[Risk Analysis] 開始分析標的: {}, 現價: {}", symbol, currentPrice);
        
        // 1. 獲取歷史 K 線 (120天)
        List<ChartDataDto> history = yahooClient.fetchChartData(symbol, "1d");
        if (history == null || history.size() < 20) {
            log.warn("[Risk Analysis] {} 歷史數據不足: {}", symbol, history == null ? "null" : history.size());
            return null; // 資料不足
        }

        log.debug("[Risk Analysis] 獲取到 {} 筆歷史數據", history.size());

        // 2. 計算日報酬率 (Log Returns)
        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < history.size(); i++) {
            double p1 = history.get(i-1).getClose().doubleValue();
            double p2 = history.get(i).getClose().doubleValue();
            if (p1 > 0 && p2 > 0) {
                returns.add(Math.log(p2 / p1));
            }
        }

        if (returns.size() < 10) {
            log.warn("[Risk Analysis] {} 有效回報率數據不足", symbol);
            return null;
        }

        // 3. 計算 HV (20, 60, 120日)
        double hv20 = calculateHV(returns, 20);
        double hv60 = calculateHV(returns, 60);
        double hv120 = calculateHV(returns, 120);

        // 4. 計算夏普比率 (Sharpe Ratio) - 以 120 日數據為準
        double sharpe = calculateSharpe(returns, 120);

        // 5. 計算 VaR (95%, 99%) - 使用歷史模擬法
        double dailyVaR95 = calculateVaR(returns, 0.05);
        double dailyVaR99 = calculateVaR(returns, 0.01);
        
        // 6. BS 理論區間 (未來 30 天, ±1 標準差)
        double sigma = hv20 / 100.0; 
        double T = 30.0 / 365.0;
        double r = 0.015; 
        
        double drift = (r - 0.5 * sigma * sigma) * T;
        double shock = sigma * Math.sqrt(T);
        double upperBond = currentPrice.doubleValue() * Math.exp(drift + shock);
        double lowerBond = currentPrice.doubleValue() * Math.exp(drift - shock);

        log.info("[Risk Analysis] {} 分析完成: HV20={}% , Sharpe={}, Range={}-{}", symbol, hv20, sharpe, lowerBond, upperBond);

        return new RiskReport(
            hv20, hv60, hv120,
            sharpe,
            dailyVaR95, dailyVaR99,
            upperBond, lowerBond,
            System.currentTimeMillis()
        );
    }

    private double calculateSharpe(List<Double> returns, int days) {
        int count = Math.min(returns.size(), days);
        if (count < 2) return 0.0;
        List<Double> subList = returns.subList(returns.size() - count, returns.size());
        
        double mean = subList.stream().mapToDouble(d -> d).average().orElse(0.0);
        double variance = subList.stream().mapToDouble(d -> Math.pow(d - mean, 2)).sum() / (count - 1);
        double stdev = Math.sqrt(variance);
        
        if (stdev == 0) return 0.0;
        
        // 年化報酬 (252天) - 無風險利率 (1.5%) / 年化波動度
        double annualizedReturn = mean * 252;
        double annualizedVol = stdev * Math.sqrt(252);
        return (annualizedReturn - 0.015) / annualizedVol;
    }

    private double calculateHV(List<Double> returns, int days) {
        int count = Math.min(returns.size(), days);
        List<Double> subList = returns.subList(returns.size() - count, returns.size());
        
        double mean = subList.stream().mapToDouble(d -> d).average().orElse(0.0);
        double variance = subList.stream().mapToDouble(d -> Math.pow(d - mean, 2)).sum() / (count - 1);
        
        // 年化波動率 = 標準差 * sqrt(252個交易日)
        return Math.sqrt(variance) * Math.sqrt(252) * 100.0;
    }

    private double calculateVaR(List<Double> returns, double percentile) {
        if (returns.isEmpty()) return 0.0;
        List<Double> sorted = returns.stream().sorted().collect(Collectors.toList());
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return Math.abs(sorted.get(Math.max(0, index))) * 100.0;
    }

    public record RiskReport(
        double hv20, double hv60, double hv120,
        double sharpe,
        double dailyVaR95, double dailyVaR99,
        double upperBond, double lowerBond,
        long timestamp
    ) {}
}
