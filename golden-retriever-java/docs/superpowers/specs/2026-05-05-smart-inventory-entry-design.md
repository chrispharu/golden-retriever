# 智慧庫存登錄系統 (Smart Inventory Entry) 設計文檔

## 1. 目標 (Goals)
消除現有庫存新增流程的摩擦感，實現「自動填充、多點入口、連續操作」的流暢體驗。讓使用者無需手動查詢股價與匯率即可完成資產登錄。

## 2. 三位一體流程設計 (Three-Pronged Workflow)

### A. 觀察清單一鍵轉入 (Watchlist-to-Inventory)
- **入口**：在 `sidebar.component` 的每個股票卡片新增「+ 庫存」快速按鈕。
- **行為**：點擊後在卡片內部展開迷你表單，自動帶入該標的的即時報價與目前匯率。
- **目的**：針對正在追蹤的標的，實現秒級庫存登錄。

### B. 庫存表格行內快速輸入 (Inline Quick Add)
- **入口**：庫存表格 (`inventory.component`) 的首行常駐透明輸入列。
- **行為**：支援鍵盤 `Tab` 切換，輸入代碼後自動填充現價，按下 `Enter` 直接儲存。

### C. 智慧引導彈窗與連續模式 (Smart Wizard & Batch Mode)
- **入口**：全域新增按鈕或進階登錄選項。
- **核心功能**：
    - **自動填充 (Auto-fill)**：選定標的與日期後，系統自動填入參考價格與匯率。
    - **連續模式 (Batch Mode)**：開啟後，儲存操作不會關閉彈窗，而是清空內容並重新聚焦代碼輸入框。
    - **日期選擇器**：支援選擇歷史日期並自動匹配當日收盤數據。

## 3. 技術實作

### 後端輔助 API (Spring Boot)
- **端點**：`GET /api/inventory/reference-data`
- **參數**：`symbol` (必填), `date` (選填，預設今日)
- **邏輯**：
    - 串接 `YahooFinanceClient` 獲取指定日期的歷史數據 (History Data)。
    - 若日期為今日，優先從 `TwStockService` (Fugle) 或 `YahooFinanceClient` 獲取盤中報價。
    - 回傳格式：`{ "price": 145.2, "exchangeRate": 32.1, "currency": "USD" }`

### 前端優化 (Angular)
- **資料流**：
    - 監聽代碼 (Symbol) 與日期 (Date) 的變更事件。
    - 變動時觸發後端輔助 API，並使用 `patchValue` 更新表單。
- **UX 強化**：
    - 欄位變動動畫（Highlight），提示使用者數據已自動填入。
    - 鍵盤監聽事件 (`keydown.enter`)。

## 4. 異常處理 (Edge Cases)
- **API 失敗**：若查無參考價（如休市日或新上市），保留手動輸入權限並提示「請手動輸入」。
- **匯率轉換**：台股標的自動鎖定匯率為 1.0 並停用該欄位。

## 5. 測試策略 (Testing)
- **單元測試**：驗證輔助 API 在不同日期與幣別下的回傳準確性。
- **UI 測試**：驗證「連續模式」下焦點是否正確回到代碼輸入框。
