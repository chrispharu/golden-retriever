package com.example.golden_retriever_java.dto; // [NEW] 歸類於 dto 資料夾

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

// [NEW] 專為匯出「分享清單」設計的 DTO，刻意不包含 Inventory，保護財務隱私
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareDataDto {
    private List<UserPortfolioData.TargetStock> stocks;
    private List<String> currencies;
    private Map<String, String> _meta; // 用於標記 type: SHARE 與匯出時間
}