# 跨幣別資產即時戰情室 (Unified Currency Dashboard) 實作計畫

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 實作以台幣為基準的跨幣別資產損益拆解功能，區分股價損益與匯率損益，並提供手動展開的流暢 UI。

**Architecture:** 後端負責核心損益計算引擎（拆解股價/匯率損益），前端負責資料渲染、手動展開邏輯及數值變動視覺特效。

**Tech Stack:** Spring Boot (JPA, WebClient), Angular (RxJS, SCSS), MSSQL.

---

### Task 1: 後端 DTO 擴充

**Files:**
- Modify: `src/main/java/com/example/golden_retriever_java/dto/DashboardResponseDto.java`

- [ ] **Step 1: 在 GroupedInventory DTO 中新增損益拆解欄位**

```java
// 在 GroupedInventory 類別中新增：
private BigDecimal pricePLTWD;    // 股價變動損益 (TWD)
private BigDecimal exchangePLTWD; // 匯率變動損益 (TWD)
```

- [ ] **Step 2: 編譯確認無誤**

執行: `./mvnw compile`
預期: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/golden_retriever_java/dto/DashboardResponseDto.java
git commit -m "dto: add pricePLTWD and exchangePLTWD fields to GroupedInventory"
```

---

### Task 2: 後端損益拆解邏輯實作

**Files:**
- Modify: `src/main/java/com/example/golden_retriever_java/controller/StockController.java`

- [ ] **Step 1: 修改 processGroupedInventory 實作損益拆解公式**

```java
// 核心邏輯修改
for (DashboardResponseDto.GroupedInventory g : groupedInvMap.values()) {
    // ... 既有程式碼 ...
    if (hasValidRate && g.getTotalShares().compareTo(BigDecimal.ZERO) > 0) {
        // 計算平均買入匯率 (Avg ExRate)
        BigDecimal avgExRate = g.getTotalCostTWD().divide(g.getTotalCostOriginal(), 8, RoundingMode.HALF_UP);
        // 計算平均原始成本 (Avg Cost Original)
        BigDecimal avgCostOrig = g.getTotalCostOriginal().divide(g.getTotalShares(), 8, RoundingMode.HALF_UP);

        // 1. 股價損益 (Price P/L) = (目前價 - 平均買入價) * 股數 * 目前匯率
        BigDecimal pricePLTWD = currentPrice.subtract(avgCostOrig)
                .multiply(g.getTotalShares())
                .multiply(currentRate);
        
        // 2. 匯率損益 (FX P/L) = 平均買入價 * 股數 * (目前匯率 - 平均買入匯率)
        BigDecimal exchangePLTWD = avgCostOrig.multiply(g.getTotalShares())
                .multiply(currentRate.subtract(avgExRate));

        g.setPricePLTWD(pricePLTWD.setScale(0, RoundingMode.HALF_UP));
        g.setExchangePLTWD(exchangePLTWD.setScale(0, RoundingMode.HALF_UP));
        // ...
    }
}
```

- [ ] **Step 2: 驗證 API 回傳結果**

(執行專案並檢查 /api/stocks 輸出)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/golden_retriever_java/controller/StockController.java
git commit -m "feat: implement P/L decomposition logic in StockController"
```

---

### Task 3: 前端 Service 與 Interface 更新

**Files:**
- Modify: `golden-retriever-web/src/app/services/stock.ts`

- [ ] **Step 1: 更新 GroupedInventory 介面定義**

```typescript
export interface GroupedInventory {
  // ... 既有欄位 ...
  pricePLTWD: number;
  exchangePLTWD: number;
}
```

- [ ] **Step 2: Commit**

```bash
git add golden-retriever-web/src/app/services/stock.ts
git commit -m "web: update GroupedInventory interface with P/L fields"
```

---

### Task 4: 前端庫存列表 UI 實作 (手動展開面板)

**Files:**
- Modify: `golden-retriever-web/src/app/components/inventory/inventory.html`
- Modify: `golden-retriever-web/src/app/components/inventory/inventory.scss`
- Modify: `golden-retriever-web/src/app/components/inventory/inventory.ts`

- [ ] **Step 1: 在 inventory.ts 新增展開狀態管理**

- [ ] **Step 2: 修改 HTML 加入點擊事件與展開面板**

- [ ] **Step 3: 加入 CSS 動態過場效果**

- [ ] **Step 4: Commit**

---

### Task 5: 視覺特效 - 數值變動閃爍與優化

**Files:**
- Modify: `golden-retriever-web/src/app/components/inventory/inventory.ts`
- Modify: `golden-retriever-web/src/app/components/inventory/inventory.scss`

- [ ] **Step 1: 加入數值變動比對與閃爍特效**

- [ ] **Step 2: 完成最終測試**

- [ ] **Step 3: Commit**
