# 🐕 Golden Retriever 專案開發規範與 API 維護紀錄

## 🌐 語言與溝通規範 (Language Preference)
*   **溝通語言**：一律使用**繁體中文** (Traditional Chinese) 進行回覆與技術討論。

## 🚨 重點警告：富果 (Fugle) API 處理原則 (2026-04-17 修正)

### 1. 絕對禁止「過度聰明」的金鑰轉換
*   **原始狀態原則**：對使用者輸入的 API Key (JWT Token) 必須保持 **100% 原始狀態** 送出。
*   **嚴禁解碼/加密**：絕對禁止嘗試對金鑰進行 Base64 解碼、UUID 提取或任何字串轉換。金鑰中包含的 `==` 或 `OTkz...` 是 JWT 的合法組成部分，並非需要「還原」的編碼。
*   **教訓反省**：在開發過程中，曾因看到金鑰具備 Base64 特徵而反覆嘗試解碼/不解碼，導致授權格式被破壞產生 401 錯誤。未來遇到此類情況，應優先比對「原始成功案例」而非自行揣測金鑰格式。

### 2. 精確的 API 通訊格式
向富果官方伺服器 (`api.fugle.tw`) 發送請求時，必須嚴格遵守以下格式：
*   **Header 名稱**：必須使用 **`X-API-KEY`** (注意：不是 `Authorization` 也不具備 `Bearer` 前綴)。
*   **User-Agent**：必須帶上標準瀏覽器 Header (如 `Mozilla/5.0...`)，防止被伺服器阻擋。
*   **網址格式 (K 線圖)**：
    *   正確：`.../historical/candles/1d?symbol=代碼&from=日期&to=日期`
    *   錯誤：`.../historical/candles/代碼?fields=...` (這是 V0.3 的舊格式，V1.0 已不適用)。
*   **JSON 解析節點**：資料陣列位於 **`.path("data")`** 下，並非 `candles` 或 `results`。

---

## 🏛️ 專案架構與開發規範 (2026-04-20 更新)

### 0. 專案技術棧轉型背景 (Node.js to Angular)
*   **重大變更**：本專案已全面由 **Node.js 改寫為 Angular**。
*   **Token 節約原則**：AI 助理在進行程式碼分析時，**嚴禁參考或讀取** `src/main/resources/static` 下的舊有 `.js` 或 `.html` 檔案（該目錄僅為編譯產出）。請直接前往 `D:\ProjectD\golden-retriever-web` 進行開發。
*   **禁止耗費資源**：不要在過去的 Node.js 邏輯上耗費時間與 Token，一切以 Angular 實作為準。

### 1. 目錄結構與樣式規範
本專案採完全的前後端分離架構：
*   **後端 (Backend)**: `D:\ProjectD\golden-retriever-java`
*   **前端 (Frontend)**: `D:\ProjectD\golden-retriever-web`
*   **🎨 CSS/SCSS 資安強制規範**：
    *   **集中管理**：所有 Angular 樣式必須集中於組件對應的 `.scss` 檔案中。
    *   **嚴禁行內樣式**：絕對禁止在 HTML 模板中使用 `style="..."`。
    *   **嚴禁模板樣式表**：絕對禁止在 HTML 頂部放置 `<style>` 標籤。
    *   **資安理由**：行內樣式會破壞內容安全政策 (CSP)，增加 CSS 注入與 UI Redressing 攻擊風險。違反此規範將視為嚴重資安漏洞。

### 2. 資料庫 (SQL Server) 為唯一真理
*   專案已全面遷移至 MSSQL，不再使用 `.json` 檔案儲存資料。
*   **金鑰儲存**：金鑰持久化儲存於 `system_config` 表中，Key 名稱為 `FUGLE_API_KEY`。

### 3. Angular 前端穩定性 (NG0100)
*   前端與後端通訊預設使用 `http://localhost:3000/api`。
*   在 `ngOnInit` 觸發 API 抓取時，必須包裝在 `setTimeout(..., 0)` 中，以避開 Angular 的變更偵測衝突錯誤 (`ExpressionChangedAfterItHasBeenCheckedError`)。
*   **SSR 相容性**：存取 `localStorage` 前必須使用 `isPlatformBrowser` 檢查環境。

## 🛡️ 修改與測試規範 (Modification & Testing Protocol)

1. **強制自行測試 (Mandatory Self-Testing)**:
   - 在宣佈任何程式碼修改完成、修正成功或功能實作完畢之前，**必須**先行執行相關的自動化測試（如 Unit Test 或 Integration Test）。
   - **嚴禁**在未取得測試通過證據（如測試執行輸出、通過率等）的情況下聲稱工作已完成。
   - 若環境允許，應優先使用專案內的測試專案（如 `ValidationServiceTest`）進行驗證。

2. **語法與編譯檢查 (Syntax & Compilation Check)**:
   - 修改後必須確認無語法錯誤、引用遺漏或編譯失敗。
   - 對於涉及反射（Reflection）的代碼，必須確保欄位名稱、方法名稱與實體類別完全一致。

3. **驗證證據回報 (Evidence-Based Reporting)**:
   - 回報進度時，應附帶簡短的測試執行結果說明，確保每一步更動都有數據支撐。

## 📈 量化分析擴充 (FinLab AI Integration)

本專案已整合 **FinLab AI** 技能。

### 1. 技能位置
*   **專案層級路徑**: `D:\ProjectD\golden-retriever-web\.agents\skills\finlab`
*   **註記**: 技能已部署於前端目錄的 `.agents` 資料夾中，AI 助理啟動時會自動載入此路徑下的量化工具。

### 2. 環境依賴 (Hybrid Stack)
雖然主架構為 Java + Angular，但為了執行量化分析，開發環境需具備：
*   **Python 3.10+** 與 **uv** 套件管理器。
*   **FinLab 庫**: 用於執行後端量化計算，結果可透過 JSON 格式與 Java API 對接。

### 3. 功能調用
安裝完成後，AI 助理將具備以下工具：
*   `list_documents`: 查詢 FinLab 官方量化教學文件。
*   `sim`: 執行量化回測模擬邏輯。
*   `finlab_data`: 存取台美股財務指標 (900+ 欄位)。

---

## 🛠️ 開發紀律與環境坑位預防 (Environment Specifics)

### 1. 跨語言編碼與寫入規範 (防範 BOM/亂碼)
*   **Java 端寫入**：在 Windows 環境下修改 Java 原始碼，必須強制使用 `No-BOM UTF8` 編碼，避免 `\ufeff` (BOM) 導致 Maven 編譯失敗。
*   **Python 調度**：Java 透過 `ProcessBuilder` 讀取 Python 輸出時，必須明確指定 `StandardCharsets.UTF_8` 讀取流。
*   **Python 輸出**：量化腳本必須包含 `sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')`，確保輸出不受 Windows 系統語系影響產生亂碼。

### 2. 外部 API 限額與快取原則
*   **FinLab 模組**：
    *   **限制**：具備每日 500MB 流量限制。
    *   **快取**：`QuantService` 必須實作「每日記憶體快取」，同一日期內嚴禁重複啟動 FinLab 引擎，除非使用者點擊「重新掃描」。

### 3. UI 設計模式標準 (Name-First Pattern)
*   **佈局原則**：所有股票卡片（Sidebar, Inventory, Quant），必須將「股票中文名稱」置於主標題位（大字/粗體），「代碼」置於副標題或縮小顯示，以符合台灣市場閱讀習慣。

### 4. 多工作區 (Multi-Workspace) 配置規範 (2026-04-21 新增)
*   **標準路徑**：跨目錄開發時，設定檔必須置於 `.gemini/settings.json`（而非根目錄 `.gemini.json`）。
*   **權限授權**：啟動後必須執行 `/permissions` 並選擇 **Trust parent folder**，否則 CLI 會基於安全理由忽略本地 settings.json。
*   **配置範例**：
    ```json
    { "context": { "includeDirectories": ["D:/ProjectD/golden-retriever-web"] } }
    ```
*   **生效條件**：修改 settings.json 後需執行 `/reload` 或重啟 CLI 才能擴張工具的讀寫邊界。

---

## 🛡️ 邏輯定錨與斷路協議 (Logical Anchoring & Circuit-Breaker Protocol)

1. **實徵驅動 (Evidence-Based)**：嚴禁「試試看」的盲目修改。
2. **環境語法一致性 (Environment Awareness)**：
   *   **嚴禁指令矛盾**：若已確認為 Windows 環境，**絕對禁止**提供 `curl | sh` 等 Unix 專用安裝腳本。
   *   **Windows 優先原則**：所有安裝建議必須優先提供 PowerShell (`iwr`, `Set-ExecutionPolicy`) 或 Windows 包管理工具 (`scoop`, `choco`, `winget`) 的指令。
3. **參考基準優先 (Reference Supremacy)**：當使用者提及「已知可運作版本」時，該版本為唯一真理。

---

## 💡 AI 協作反省 (Post-Mortem)

*   **⚠️ 環境建議矛盾 (2026-04-20)**：
    *   **描述**：在先前已明確診斷出 Windows 環境不支援 `curl | sh` 的情況下，AI 在建議安裝 `uv` 工具時卻再次提供了該 Linux 指令，造成邏輯前後矛盾。
    *   **改進措施**：提供跨平台工具安裝建議前，必須強制檢核目前的 `session_context.os`，確保指令在該作業系統下可直接執行。
*   **⚠️ 嚴重流程違規案例 (2026-04-20)**：(略)
---

## 💡 程式碼全量覆寫與功能續存協議 (Functional Continuity Protocol)

*   **⚠️ 破壞性全量覆寫事故 (Destructive Overwrite Incident)**：
    *   **現象**：在進行代碼重構或新功能導入時，AI 容易因過度追求「新代碼結構整潔」或「任務目標完成」，而忽略對「既有功能標籤、事件綁定、隱藏邏輯或依賴組件」的保全，導致使用全量覆寫 (`write_file`) 後，原有核心功能遭到意外移除。
    *   **根本原因**：未將重構視為「合併 (Merge)」行為，而是錯誤地視為「取代 (Replace)」。缺乏對源檔案的「功能定錨點 (Functional Anchors)」識別與繼承。
    *   **【強制開發規範 - 全局功能保全協議】**：
        1.  **功能定錨識別 (Anchoring Check)**：執行任何大規模覆寫前，必須先掃描源檔案，列出所有非本次更動目標的關鍵組件（如 `<app-xxx>`）、特定事件綁定、以及重要的條件渲染邏輯。
        2.  **繼承優先原則**：若必須使用全量覆寫，新產出的代碼必須 100% 繼承所有已識別的「功能定錨點」，禁止以「代碼優化」為名私自移除任何看似不相關的既有邏輯。
        3.  **覆寫後回歸自檢**：完成寫入後，AI 必須立即執行關鍵字比對，確保原有的核心標籤或邏輯片段依然完整存在。
        4.  **手術式修改偏好**：在可能的情況下，應優先選用精確匹配的 `replace` 工具，而非全量 `write_file`，以最小化對既有功能的破壞風險。


---
*文件更新於：2026-04-21*
