# 智慧庫存登錄系統 (Smart Inventory Entry) 實作計畫

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 實現自動化與多點入口的庫存登錄流程，包含後端參考價 API、觀察清單快速轉入、行內輸入以及智慧彈窗。

**Architecture:** 
1. 後端新增參考數據 API，整合既有 Yahoo/Fugle 抓取邏輯。
2. 前端實作多種 UI 組件的互動邏輯與連續操作模式。

**Tech Stack:** Spring Boot, Angular, RxJS, SCSS.

---

### Task 1: 後端參考數據 API 實作

**Files:**
- Modify: `src/main/java/com/example/golden_retriever_java/controller/StockController.java`

- [ ] **Step 1: 在 StockController 新增 /api/inventory/reference-data 端點**
  - 串接 `YahooFinanceClient.fetchQuotes` (若日期為今日)。
  - 串接 `YahooFinanceClient.fetchChartData` (若日期為過去) 取得該日 Close 價。
  - 串接 `YahooFinanceClient.fetchExchangeRates` 取得對應幣別匯率。

- [ ] **Step 2: 編譯並測試 API**

---

### Task 2: 前端 Service 與觀察清單快速按鈕 (Sidebar)

**Files:**
- Modify: `golden-retriever-web/src/app/services/stock.ts`
- Modify: `golden-retriever-web/src/app/components/sidebar/sidebar.html`
- Modify: `golden-retriever-web/src/app/components/sidebar/sidebar.ts`
- Modify: `golden-retriever-web/src/app/components/sidebar/sidebar.scss`

- [ ] **Step 1: 實作 getReferenceData API 呼叫**
- [ ] **Step 2: 在 Sidebar 股票卡片加入「+ 庫存」按鈕**
- [ ] **Step 3: 實作卡片內迷你表單，點擊按鈕後自動填入數據並聚焦股數**

---

### Task 3: 智慧彈窗與連續模式 (Smart Wizard)

**Files:**
- Modify: `golden-retriever-web/src/app/components/inventory/inventory.html`
- Modify: `golden-retriever-web/src/app/components/inventory/inventory.ts`

- [ ] **Step 1: 修改新增表單，監聽 Symbol 與 Date 變更**
- [ ] **Step 2: 變更時自動觸發 API 並更新 Price/Rate 欄位**
- [ ] **Step 3: 加入「連續新增模式」Toggle 邏輯**

---

### Task 4: 庫存表格行內快速輸入列

**Files:**
- Modify: `golden-retriever-web/src/app/components/inventory/inventory.html`
- Modify: `golden-retriever-web/src/app/components/inventory/inventory.scss`

- [ ] **Step 1: 在 Table Thead 下方加入常駐的 Quick-Add Row**
- [ ] **Step 2: 實作鍵盤監聽，Enter 鍵直接觸發 submitAddInventory**

---

### Task 5: 最終整合測試與 UI 拋光
- [ ] **Step 1: 驗證三種入口的數據一致性**
- [ ] **Step 2: 加入欄位自動填充時的 Highlight 動畫**
