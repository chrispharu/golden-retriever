# Unified Card UI & Decimal Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform the current table-based inventory and portfolio UI into a modern, unified card-based interface with precise decimal support and improved accessibility.

**Architecture:** Refactor `InventoryComponent` and `SidebarComponent` to use CSS Grid/Flexbox card layouts. Implement a shared `Summary Bar` for high-level portfolio metrics. Update data handling to support up to 4 decimal places for prices and shares. Fix date picker overflow issues by moving the entry form to a modal.

**Tech Stack:** Angular (TypeScript), SCSS, Spring Boot (Java).

---

### Task 1: Update Frontend Data Models & Service

**Files:**
- Modify: `D:\ProjectD\golden-retriever-web\src\app\services\stock.ts`

- [ ] **Step 1: Update InventoryRecord and PortfolioItem interfaces**

```typescript
// Update StockService interfaces for decimal precision and extra metrics
export interface InventoryRecord {
  id: string;
  symbol: string;
  price: number;
  shares: number;
  date: string;
  exchangeRate: number;
}

export interface PortfolioItem {
  symbol: string;
  name: string;
  currency: string;
  price: number;
  prevClose: number;
  high?: number;
  low?: number;
  marketStatus: string;
  postMarketPrice: number;
  rate: number;
  costTWD: number;
  avgCost: number;
  avgCostTWD: number;
  link: string;
  volStatus?: string; // NEW: Volume signal
  range52w?: number;  // NEW: 0-100 position in 52w high/low
}
```

- [ ] **Step 2: Verify compilation**
Run: `npm run build` in web directory (if available) or check for TS errors.

- [ ] **Step 3: Commit**
`git add src/app/services/stock.ts && git commit -m "refactor: update stock interfaces for decimal and metrics"`

---

### Task 2: Implement Unified SCSS Variables & Global Mixins

**Files:**
- Modify: `D:\ProjectD\golden-retriever-web\src\styles.scss`

- [ ] **Step 1: Define Color Variables and Card Mixins**

```scss
:root {
  --friendly-green: #1D9E75;
  --playful-red: #E24B4A;
  --soft-cream: #fdf6e3;
  --bone-white: #f8f9fa;
  --fur-brown: #5d4037;
  --light-brown: #8d6e63;
  --primary-gold: #ffb300;
  --shadow-warm: rgba(93, 64, 55, 0.1);
  --border-radius-lg: 24px;
}

// Global card styling
.card-base {
  background: white;
  border-radius: var(--border-radius-lg);
  padding: 1.5rem;
  box-shadow: 0 4px 12px var(--shadow-warm);
  border: 1px solid rgba(141, 110, 99, 0.1);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  
  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 8px 24px var(--shadow-warm);
  }
}
```

- [ ] **Step 2: Commit**
`git add src/styles.scss && git commit -m "style: define unified color variables and card base"`

---

### Task 3: Refactor Sidebar (Portfolio List) to Card Layout

**Files:**
- Modify: `D:\ProjectD\golden-retriever-web\src\app\components\sidebar\sidebar.html`
- Modify: `D:\ProjectD\golden-retriever-web\src\app\components\sidebar\sidebar.scss`

- [ ] **Step 1: Apply Card-based HTML Structure**

```html
<div class="market-grid-container">
    <!-- Summary Bar (New) -->
    <div class="summary-card card-base" *ngIf="portfolio.length > 0">
        <div class="summary-main">
            <span class="label">總持股市值</span>
            <span class="total">NT$ {{ calculateTotal() | number:'1.0-0' }}</span>
        </div>
        <!-- Simplified stats -->
    </div>

    <!-- Toolbar -->
    <div class="market-toolbar">...</div>

    <!-- Cards Wrapper -->
    <div class="cards-wrapper">
        <div *ngFor="let stock of portfolio" class="stock-card card-base" [class.up]="stock.price > stock.prevClose" [class.down]="stock.price < stock.prevClose">
            <!-- New Card Structure from mockup -->
        </div>
    </div>
</div>
```

- [ ] **Step 2: Implement SCSS for Sidebar Cards**
Update `sidebar.scss` to use grid for metrics and apply the left-border colored stripe.

- [ ] **Step 3: Commit**
`git commit -m "feat: refactor portfolio list to card-based layout"`

---

### Task 4: Refactor Inventory to Unified Card Layout

**Files:**
- Modify: `D:\ProjectD\golden-retriever-web\src\app\components\inventory\inventory.html`
- Modify: `D:\ProjectD\golden-retriever-web\src\app\components\inventory\inventory.scss`
- Modify: `D:\ProjectD\golden-retriever-web\src\app\components\inventory\inventory.ts`

- [ ] **Step 1: Replace Table with Card Grid**
Remove `<table>` and implement `<div class="inventory-grid">`. Use same `card-base` class.

- [ ] **Step 2: Implement 52-week Range Bar**
Add the progress bar logic in `inventory.html`.

- [ ] **Step 3: Move Add Inventory Form to Modal**
Create a new overlay/dialog structure within the component or use an existing modal service to house the `add-inventory-form`. This fixes the date picker clipping.

- [ ] **Step 4: Update Number Inputs for Decimal Support**
Change `<input type="number">` to `<input type="number" step="any">`.

- [ ] **Step 5: Commit**
`git commit -m "feat: refactor inventory to cards and fix decimal support"`

---

### Task 5: Backend Decimal Verification (Sanity Check)

**Files:**
- Modify: `D:\ProjectD\golden-retriever-java\src\main\java\com\example\golden_retriever_java\entity\InventoryEntity.java` (Verification only)
- Test: `D:\ProjectD\golden-retriever-java\src\test\java\com\example\golden_retriever_java\GoldenRetrieverJavaApplicationTests.java`

- [ ] **Step 1: Verify Entity Precision**
Ensure `@Column(precision = 18, scale = 4)` is present for `price` and `shares`.

- [ ] **Step 2: Add Test Case for Decimal Precision**
Write a test to save a record with 4 decimal places and verify retrieval.

```java
@Test
void testInventoryDecimalPrecision() {
    InventoryEntity entity = InventoryEntity.builder()
        .symbol("TEST")
        .price(new BigDecimal("123.4567"))
        .shares(new BigDecimal("10.8888"))
        .build();
    // Save and assert...
}
```

- [ ] **Step 3: Commit**
`git commit -m "test: verify backend decimal precision for inventory"`
