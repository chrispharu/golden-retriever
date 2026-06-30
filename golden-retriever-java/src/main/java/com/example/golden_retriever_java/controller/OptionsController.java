package com.example.golden_retriever_java.controller;

import com.example.golden_retriever_java.dto.OptionsPricingDto;
import com.example.golden_retriever_java.service.BlackScholesService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/options")
public class OptionsController {

    private final BlackScholesService bsService;

    public OptionsController(BlackScholesService bsService) {
        this.bsService = bsService;
    }

    @PostMapping("/pricing")
    public ResponseEntity<OptionsPricingDto.Response> calculatePricing(@RequestBody OptionsPricingDto.Request req) {
        BlackScholesService.PricingResult res = bsService.calculate(
            req.getS(), req.getK(), req.getT(), req.getR(), req.getSigma()
        );

        OptionsPricingDto.Response response = OptionsPricingDto.Response.builder()
            .callPrice(res.callPrice())
            .putPrice(res.putPrice())
            .callDelta(res.callDelta())
            .putDelta(res.putDelta())
            .gamma(res.gamma())
            .vega(res.vega())
            .callTheta(res.callTheta())
            .putTheta(res.putTheta())
            .callRho(res.callRho())
            .putRho(res.putRho())
            .timestamp(System.currentTimeMillis())
            .build();

        return ResponseEntity.ok(response);
    }
}
