package com.example.golden_retriever_java.controller; // [NEW] 歸類於 controller 資料夾

import com.example.golden_retriever_java.dto.ShareDataDto;
import com.example.golden_retriever_java.dto.UserPortfolioData;
import com.example.golden_retriever_java.service.DataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SystemController {

    private static final Logger log = LoggerFactory.getLogger(SystemController.class);
    private final DataService db;
    private final com.example.golden_retriever_java.service.TwStockService twStockService;

    public SystemController(DataService db, com.example.golden_retriever_java.service.TwStockService twStockService) {
        this.db = db;
        this.twStockService = twStockService;
    }

    /**
     * 獲取 Fugle API 剩餘額度
     */
    @GetMapping("/system/fugle-quota")
    public ResponseEntity<Map<String, Integer>> getFugleQuota() {
        Map<String, Integer> res = new HashMap<>();
        res.put("remaining", twStockService.getRemainingQuota());
        return ResponseEntity.ok(res);
    }

    // [NEW] 匯出：僅分享清單 (不含庫存)
    @GetMapping("/export/share")
    public ResponseEntity<ShareDataDto> exportShareList() {
        try {
            UserPortfolioData data = db.getData(); // 拿最新

            Map<String, String> meta = new HashMap<>();
            meta.put("type", "SHARE");
            meta.put("date", Instant.now().toString());

            ShareDataDto shareData = ShareDataDto.builder()
                    .stocks(data.getStocks())
                    .currencies(data.getCurrencies())
                    ._meta(meta)
                    .build();

            // [LOG註記] 記錄分享清單匯出事件
            log.info("[System] Share list exported");
            return ResponseEntity.ok(shareData);
        } catch (Exception e) {
            log.error("[System] 匯出發生錯誤: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    // [NEW] 匯入：安全合併邏輯，不洗掉原有資料與庫存
    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importData(@RequestBody UserPortfolioData uploadData) {
        try {
            if (uploadData == null) {
                throw new RuntimeException("無效的資料格式");
            }

            int addedCount = 0;

            // [DATABASE EVOLUTION] 直接循環儲存，由 JPA 與 MSSQL 處理 UPSERT
            if (uploadData.getStocks() != null) {
                for (UserPortfolioData.TargetStock newS : uploadData.getStocks()) {
                    db.addStock(newS.getSymbol(), newS.getCurrency(), newS.getName());
                    addedCount++;
                }
            }

            // 合併匯率清單
            if (uploadData.getCurrencies() != null) {
                for (String c : uploadData.getCurrencies()) {
                    db.addCurrency(c.toUpperCase());
                }
            }

            log.info("[System] Imported data from external source.");

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "匯入完成 (已處理 " + addedCount + " 支標的)");
            response.put("type", "SHARE");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("[System] 匯入發生錯誤: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}