# 跨幣別資產即時戰情室 (Unified Currency Dashboard) 設計文檔

## 1. 目標 (Goals)
解決多幣別資產（台股、美股、黃金）因匯率波動導致的資產損益模糊問題。透過後端精確計算，將「股價變動」與「匯率變動」損益拆解，並在前端提供流暢的視覺反饋。

## 2. 核心邏輯：損益定錨 (Cost Anchoring)
採用「即時混合模式 (Real-time Hybrid)」，以台幣 (TWD) 為基準點進行所有資產的損益衡量。

### 損益拆解公式
對於非台幣資產（如美股）：
- **買入成本 (TWD)** = `買入單價 * 股數 * 買入時匯率`
- **即時市值 (TWD)** = `即時單價 * 股數 * 即時匯率`
- **股價損益 (Price P/L)** = `(即時單價 - 買入單價) * 股數 * 即時匯率`
- **匯率損益 (FX P/L)** = `買入單價 * 股數 * (即時匯率 - 買入時匯率)`
- **總未實現損益 (Total Unrealized P/L)** = `股價損益 + 匯率損益`
- **總投報率 (Total ROI)** = `(總未實現損益 + 已領股利) / 買入成本 (TWD)`

## 3. 技術架構

### 後端 (Spring Boot)
- **DTO 更新**：在 `DashboardResponseDto.GroupedInventory` 中新增：
    - `pricePLTWD`: BigDecimal (股價損益)
    - `exchangePLTWD`: BigDecimal (匯率損益)
- **Controller 邏輯**：
    - 修改 `StockController.processGroupedInventory`。
    - 從 `InventoryEntity` 中讀取買入時的 `exchangeRate`。
    - 根據上述公式計算拆解後的損益。

### 前端 (Angular)
- **UI 組件**：`inventory.component`
- **呈現方式**：
    - **手動展開**：預設僅顯示「總損益」與「總投報率」。點擊股票列時，展開子區域顯示「股價損益」與「匯率損益」的具體數值。
    - **視覺特效**：
        - 數值更新時，背景進行短暫的紅/綠色閃爍（Red: Increase, Green: Decrease，符合台股習慣）。
        - 使用 CSS `transition` 實現高度展開的平滑動畫。
- **資料流**：使用 `StockService.getDashboardData()` 定期輪詢或由使用者手動刷新。

## 4. 互動細節 (UX)
1. 使用者在投資組合清單中點擊標的名稱。
2. 該行下方滑出損益詳情面板。
3. 面板顯示：
    - 股價貢獻：+$5,000 TWD
    - 匯率貢獻：-$1,200 TWD
4. 點擊第二次或點擊其他標的時，自動收合。

## 5. 測試策略 (Testing)
- **單元測試**：驗證 `StockController` 的損益拆解公式在各種匯率（升值/貶值）下的準確性。
- **整合測試**：確保前端能正確接收並渲染新增的 `pricePLTWD` 與 `exchangePLTWD` 欄位。
