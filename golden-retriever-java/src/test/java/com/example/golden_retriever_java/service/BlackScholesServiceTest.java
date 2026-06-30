package com.example.golden_retriever_java.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

class BlackScholesServiceTest {

    private final BlackScholesService bsService = new BlackScholesService();

    @Test
    void testStandardExample() {
        // Hull Example: S=42, K=40, r=0.1, T=0.5, sigma=0.2
        double S = 42.0;
        double K = 40.0;
        double T = 0.5;
        double r = 0.1;
        double sigma = 0.2;

        BlackScholesService.PricingResult result = bsService.calculate(S, K, T, r, sigma);

        // Standard BS Call Price approx 4.7594
        assertEquals(4.7594, result.callPrice(), 0.001, "Call price mismatch");
        
        // Standard BS Put Price approx 0.8086
        assertEquals(0.8086, result.putPrice(), 0.001, "Put price mismatch");

        // Delta (Call) approx 0.7791
        assertEquals(0.7791, result.callDelta(), 0.001, "Call Delta mismatch");
        
        // Gamma approx 0.04996
        assertEquals(0.04996, result.gamma(), 0.0001, "Gamma mismatch");
    }

    @Test
    void testATM() {
        // At-The-Money: S=100, K=100
        BlackScholesService.PricingResult result = bsService.calculate(100, 100, 1, 0.05, 0.2);
        
        assertTrue(result.callPrice() > 0);
        assertTrue(result.putPrice() > 0);
        // Put-Call Parity related: Delta(Call) - Delta(Put) = 1
        assertEquals(1.0, result.callDelta() - result.putDelta(), 0.0001, "Delta difference should be 1");
    }
}
