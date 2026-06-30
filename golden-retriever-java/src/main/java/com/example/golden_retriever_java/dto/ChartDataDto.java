package com.example.golden_retriever_java.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

// [NEW] 對應原 JS 中的 chartCache 回傳的 K 線資料結構
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChartDataDto {
    private String time; // 格式: YYYY-MM-DD

    // [MODIFIED] 原因: 強制以數字序列化，避免前端圖表庫無法讀取字串型別
    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    private BigDecimal open;

    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    private BigDecimal high;

    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    private BigDecimal low;

    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    private BigDecimal close;
}