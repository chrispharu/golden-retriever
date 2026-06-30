package com.example.golden_retriever_java.service;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Black-Scholes Model Service
 * Provides theoretical pricing and Greeks for European options.
 */
@Service
public class BlackScholesService {

    /**
     * Standard Normal Cumulative Distribution Function N(x)
     * Approximation using Abramowitz and Stegun formula (Accuracy ~ 1e-7)
     */
    private double CND(double x) {
        if (x < 0) return 1.0 - CND(-x);
        
        double k = 1.0 / (1.0 + 0.2316419 * x);
        double a1 = 0.319381530;
        double a2 = -0.356563782;
        double a3 = 1.781477937;
        double a4 = -1.821255978;
        double a5 = 1.330274429;
        
        double L = Math.abs(x);
        double d = 0.3989422804 * Math.exp(-L * L / 2.0);
        double val = 1.0 - d * (a1 * k + a2 * Math.pow(k, 2) + a3 * Math.pow(k, 3) + a4 * Math.pow(k, 4) + a5 * Math.pow(k, 5));
        
        return val;
    }

    /**
     * Standard Normal Probability Density Function N'(x)
     */
    private double NPD(double x) {
        return Math.exp(-x * x / 2.0) / Math.sqrt(2.0 * Math.PI);
    }

    public PricingResult calculate(double S, double K, double T, double r, double sigma) {
        if (S <= 0 || K <= 0 || sigma <= 0) {
            return new PricingResult(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
        if (T <= 0) T = 0.00001; // Avoid division by zero
        
        double d1 = (Math.log(S / K) + (r + Math.pow(sigma, 2) / 2.0) * T) / (sigma * Math.sqrt(T));
        double d2 = d1 - sigma * Math.sqrt(T);
        
        double callPrice = S * CND(d1) - K * Math.exp(-r * T) * CND(d2);
        double putPrice = K * Math.exp(-r * T) * CND(-d2) - S * CND(-d1);
        
        // Greeks
        double nd1 = NPD(d1);
        double gamma = nd1 / (S * sigma * Math.sqrt(T));
        double vega = S * nd1 * Math.sqrt(T) / 100.0; // Per 1% change
        
        // Call Greeks
        double callDelta = CND(d1);
        double callTheta = (-(S * nd1 * sigma) / (2 * Math.sqrt(T)) - r * K * Math.exp(-r * T) * CND(d2)) / 365.0; // Per day
        double callRho = (K * T * Math.exp(-r * T) * CND(d2)) / 100.0;
        
        // Put Greeks
        double putDelta = callDelta - 1.0;
        double putTheta = (-(S * nd1 * sigma) / (2 * Math.sqrt(T)) + r * K * Math.exp(-r * T) * CND(-d2)) / 365.0;
        double putRho = (-K * T * Math.exp(-r * T) * CND(-d2)) / 100.0;

        return new PricingResult(
            callPrice, putPrice, 
            callDelta, putDelta, 
            gamma, vega, 
            callTheta, putTheta, 
            callRho, putRho
        );
    }

    public record PricingResult(
        double callPrice, double putPrice,
        double callDelta, double putDelta,
        double gamma, double vega,
        double callTheta, double putTheta,
        double callRho, double putRho
    ) {}
}
