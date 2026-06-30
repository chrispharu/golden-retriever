# ETF 淨值與折溢價修復計畫 (2026-05-08)

## 1. 目前狀態 (Current Status)
*   **前端 UI**: 已完成卡片化重構，支援紅漲綠跌（台股習慣），且具備 `NAV` 與 `Premium` 顯示欄位。
*   **後端結構**: `StockQuoteDto` 與 `GroupedInventory` 映射邏輯已補全。
*   **效能優化**: `TwStockService` 已實作併發保護與負向快取，解決了系統卡頓問題。
*   **核心阻礙**: 證交所資料源（TSE/TPEx）對自動化請求進行了嚴格封鎖，目前回傳資料筆數為 **0**。

## 2. 已嘗試的戰術與結果 (Audit Log)
| 方案 | 端點 (Endpoint) | 結果 | 失敗原因分析 |
| :--- | :--- | :--- | :--- |
| **MIS API** | `getCategory.jsp` | 失敗 | msgArray 始終回傳空值，即使加入 Cookie 亦同。 |
| **Web 報表** | `BWETU` / `BFT41U` | 404/無效 | 證交所近期改版路徑，且對 Referer 檢查極嚴。 |
| **OpenAPI** | `v1/opendata/t187ap48_L` | 0 筆 | 耗時 305ms，疑因 302 重導向至封鎖頁，WebClient 雖配置 followRedirect 但未能成功取得內容。 |

## 3. 關鍵發現 (Critical Findings)
*   **代號碎片**: 之前發現有 `1101B` 或全形空格進入 ETF 解析器，已在 `TwStockService.java` 加入 `[0-9A-Z]+` 正規表達式過濾。
*   **欄位名稱**: 證交所 Open Data 使用中文字段名（如 `預估淨值`、`證券代號`），目前的代碼已實作「多重欄位偵測 (getJsonValue)」，具備自我修復能力。
*   **封鎖特徵**: 當 `TwStockService` 列印「成功解析 0 檔」時，代表 JSON 陣列是空的或 `rawResponse` 其實是 HTML 字串。

## 4. 接手後的執行步驟 (Next Steps)
### Step 1: 診斷 Raw Response (最高優先)
修改 `TwStockService.java`，將逾時暫時增加到 15 秒，並完整列印 `rawResponse` 的前 1000 個字元。確認是否為 `302 Found` 到 `twse.com.tw/captcha` 或其他驗證頁面。

### Step 2: 標頭深度模擬 (Headers Mimicry)
目前的 WebClient 標頭仍過於單薄。若 Step 1 確認被擋，需在 `WebClientConfig` 或 `TwStockService` 加入：
*   `Sec-Ch-Ua`, `Sec-Fetch-Mode`, `Sec-Fetch-Site` 等現代瀏覽器特有標頭。
*   確保 `Referer` 與請求的 `Domain` 100% 一致。

### Step 3: 最後保底來源
若 OpenAPI 持續失效，建議改用 `yfinance` 的 `trailingThreeMonthNav` 欄位（針對台股 ETF，Yahoo 有時會有盤後淨值資料）。

## 5. 驗證標準
*   後端日誌顯示：`[ETF NAV] TWSE 解析成功: 200+ 檔`。
*   前端 UI 0050.TW 出現淨值數字，且溢價為紅色背景，折價為綠色背景。

---
**本計畫由 Gemini CLI 於 2026-05-08 429 限流期間自動生成。**
