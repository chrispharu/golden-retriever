# Golden Retriever — Project-Specific Rules
# Golden Retriever 專案開發規範

---

## [L0] GOVERNANCE ANCHOR — 治理律令導航
> **Priority Notice / 優先權聲明**: Per [G5], this project STRICTLY ADHERES to the Global Governance Specification.
> 本專案之 AI 行為必須嚴格遵守全域規範（Global GEMINI.md）。
- **Execution Header / 執行標頭**: MUST use `[P2]` with `[GOVERNANCE SCOPE]` for every turn.
  每一輪操作前必須使用 `[P2]` 標頭並標註治理域歸屬。
- **Progress Sync / 進度同步**: MUST perform `[G1]` turn-sync to `TODO.md`.
  每一輪操作後必須同步進度至 `TODO.md`。
- **Cleanup Duty / 清理責任**: MUST execute `[G7]` process cleanup for Port 3000/8080.
- **Governance Anchor**: All behavioral and environmental rules (e.g., Windows curl.exe) are governed by **Global GEMINI.md [G11]** and enforced via the **P2 [STICKY RULES] Header**. Do not duplicate here.
- **Global Config Self-Healing / 全域憲法自我修復協定**: AI MUST verify if the global `C:\Users\atuser\.gemini\GEMINI.md` exists at startup. If it is missing or deleted, AI MUST immediately restore it by copying the local `GLOBAL_GEMINI_TEMPLATE.md` from the project root to `C:\Users\atuser\.gemini\GEMINI.md`.
  AI 於每次對話啟動時，必須先檢查全域 `C:\Users\atuser\.gemini\GEMINI.md` 是否存在。若發現該檔案遺失或損毀，AI **必須立即**將專案根目錄的 `GLOBAL_GEMINI_TEMPLATE.md` 複製還原至全域路徑，修復完成後方可繼續執行其他工作。

---

---

## [L1] TECH STACK & SKILLS — 技術棧與對應技能

| Layer | Path | Mandatory Skill to Activate |
|---|---|---|
| **Backend** | `golden-retriever-java` | `java-spring-guardian` |
| **Frontend** | `golden-retriever-web` | `angular-dev-expert` |
| **Quant** | `.agents/skills/finlab` | `finlab` (built-in) |
| **Environment** | Windows / PS | `windows-dev-expert` |

---

## [L2] DATA SOURCE FACTS — 資料源靜態事實

### 1. Fugle API Key & Requests / 富果 API 金鑰與請求規範
- **Storage / 儲存位置**: `system_config` table, key `FUGLE_API_KEY`.
  儲存於 `system_config` 資料表之 `FUGLE_API_KEY` 欄位。
- **Headers / 請求標頭**: `X-API-KEY: <raw_token>`, `User-Agent: Mozilla/5.0...`
- **V1.0 Endpoint / V1.0 API 端點**: `/historical/candles/1d?symbol=<code>&from=<date>&to=<date>`
- **Lesson / 教訓**: NEVER use V0.3 legacy format.
  絕對禁止使用舊版 V0.3 API 格式。

### 2. Yahoo Finance & TPEx Quirks / 資料源特性
- **USD/TWD**: Yahoo may return `TWD=X` or `USDTWD=X`. Parser MUST map both to `USD`.
  Yahoo 匯率符號可能偏移，解析器必須將 `TWD=X` 與 `USDTWD=X` 統一映射為 `USD`。
- **TPEx ETF**: Avoid `openapi/v1`. Use `web/stock/aftertrading/all_daily_etf_nav/etf_nav_result.php`.
  上櫃 ETF 抓取應避開不穩定的 OpenAPI，改用網頁版 PHP 介面。
- **US ETF NAV (2026 Update)**: Must explicitly request `fields=navPrice` in `v7/finance/quote`.
  美股 ETF 必須顯式請求 `navPrice` 欄位，否則折溢價計算會因誤用市價而失效。
- **Fallback / 保底機制**: Always provide a fallback rate (e.g., 32.3) to prevent total market value from showing $0 during API downtime.
  始終提供保底匯率，防止外部 API 失效時總市值歸零。

---

## [L9] LESSONS LEARNED — 關鍵數據事實紀錄 (2026-05-20)

### 1. Data Mapping Facts / 數據映射事實
- **Observation**: Yahoo Finance and TWSE/TPEx use different data shapes.
- **Field Completeness / 欄位完整性**: TWSE fundamental sync requires explicit inclusion of `dividendYield`, `pbRatio`, and `premium` during DB-to-DTO conversion.
  台股基本面同步必須在 Service 層明確包含殖利率、PB 與折溢價欄位，否則前端面板會顯示為空。
- **ETF NAV Mapping (2025 Ver)**: TWSE MIS `all_etf.txt` uses `e` (Estimated NAV) and `f` (Yesterday NAV). `g` is Premium %.
  ETF 淨值映射必須優先取 `e` 欄位，若為空則取 `f`。折溢價則對應 `g` 欄位。

### 2. UI & Frontend Logic / 前端邏輯陷阱
- **Angular Pipe Scaling**: Avoid `percent` pipe for already-scaled premium data (e.g., 1.5 meaning 1.5%). Use `number:'1.2-2'` to prevent 100x injection error.
  折溢價數據若已是百分比格式，嚴禁使用 `percent` 管道，應改用 `number` 以免數值被放大百倍。
- **Font Hierarchy**: `stock-name` (1.3rem) vs `stock-code` (0.85rem) provides optimal readability for financial dashboards.
  金融看板建議之字型階層比例：名稱使用 1.3rem，代碼使用 0.85rem。

### 3. WAF & API Constraints / 防火牆與 API 限制
- **Fugle API**: Requests are quota-limited. Maintain cached DB data for quotes during heavy loads.
  Fugle API 具備額度限制，高負載時應優先使用資料庫快取報價。
- **Obsolete Fact / 已廢除事實 (2026-05-15)**: 
  - ~~TWSE/TPEx WAF: 09:00 - 14:00 is High-Pressure Zone. Frequent API requests during this time will trigger IP bans.~~
  - **Correction**: Verification shows no such IP ban at these hours. Previous sync failures were caused by internal logic bugs, not external WAF.
  - **修正**: 經實測證實開盤時段並無 IP 封鎖限制。先前的同步失敗為內部程式邏輯錯誤，非外部防火牆限制。

### 4. Governance Compliance Facts / 治理合規事實
- **Zero Tolerance for Placeholders / 預留位置零容忍**: Any use of `...` or `rest of code` in documentation or source files is a SEVERE violation. 
  嚴禁在任何文檔、代碼或指令中使用 `...` 等預留位置。
- **Constitutional Supremacy / 憲法至上**: Global GEMINI.md logic (e.g., TODO in English, Doc in Chinese) takes absolute precedence over task-level efficiency. 
  全域規範邏輯（如 TODO 用英文、文檔用中文）具備最高優先權，AI 絕不得為了節省 Token 而簡化或省略。
- **Proactive Documentation / 主動紀錄**: AI is responsible for suggesting updates to GEMINI.md whenever a critical lesson is learned.
  AI 負有主動提議紀錄關鍵教訓之責任，確保知識傳承。

---

## [L4] SKILL OVERRIDES — 技能微調 (G10 覆蓋條款)
- **Status**: Currently adhering to standard global skill definitions.
- **Rule**: If project architecture deviates from global skills (e.g., non-standard project structure), define the overrides here.
  若專案架構與全域技能建議不符（例如：非標準資料夾結構），請在此定義覆蓋規則，AI 將優先遵循此處設定。

---

## [L6] MULTI-WORKSPACE CONFIG — 多工作區配置規範
- **Settings / 設定檔位置**: `.gemini/settings.json` (NOT root / 嚴禁放置於根目錄)。
- **Permissions / 權限設定**: Run `/permissions` -> **Trust parent folder** to enable multi-repo context.
  執行 `/permissions` 並選擇信任父資料夾以啟用多代碼庫環境。
- **Sandbox Limit / 沙盒限制**: AI's read/write actions are strictly confined to `D:\ProjectD\golden-retriever-java`. Any attempt to access `C:\` root or `D:\` root outside the project will be self-blocked.
  AI 的讀寫權限嚴格限制在 `D:\ProjectD\golden-retriever-java`。任何試圖訪問專案外的 `C:\` 根目錄或 `D:\` 根目錄的行為都將被自動攔截。

---

## [L3] CODING STANDARDS — 編碼標準
- **Java / Java 開發規範**: Must use **No-BOM UTF-8**.
  必須使用無 BOM 的 UTF-8 編碼。
- **Angular / Angular 開發規範**: All styles in `.scss`. No inline `style=`.
  所有樣式必須寫在 `.scss` 檔中，嚴禁使用行內 `style=`。

---

## [MEMORIES] Environment-Specific Rules — 環境規則
- **Commands / 系統指令**: Follow `windows-dev-expert` for all shell/network operations.
  所有命令列與網路操作必須遵循 `windows-dev-expert` 技能。
- **Global Updates / 全域更新規範**: Follow `GLOBAL_GEMINI_TEMPLATE.md` protocol (User-reviewed manual overwrite).
  遵循 `GLOBAL_GEMINI_TEMPLATE.md` 協議（經使用者審閱後手動覆寫）。

---

## [L8] INCIDENT REPORT — 嚴重事故報告 (2026-05-14)
- **Issue / 事故主因**: API loss due to placeholders & truncation.
  因使用代碼預留位置與內容截斷導致 API 遺失。
- **Action / 矯正預防**: Always activate `java-spring-guardian` to perform **Endpoint Audit** after edits.
  每次編輯完成後，強制啟動 `java-spring-guardian` 進行 API 端點稽核。

---
*Last updated: 2026-05-20*
