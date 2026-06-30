import { Component, Input, OnChanges, SimpleChanges, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StockService, GroupedInventory } from '../../services/stock';
import { ChartModalComponent } from '../chart-modal/chart-modal';
import { RiskModalComponent } from '../risk-modal/risk-modal';

@Component({
  selector: 'app-inventory',
  standalone: true,
  imports: [CommonModule, FormsModule, ChartModalComponent, RiskModalComponent],
  templateUrl: './inventory.html',
  styleUrls: ['./inventory.scss']
})
export class InventoryComponent implements OnChanges {

  @Input() groupedInventory: GroupedInventory[] | undefined = [];
  @Output() dataChanged = new EventEmitter<void>();

  displayInventory: GroupedInventory[] = [];
  searchQuery: string = '';
  sortMode: string = 'roi-desc';
  expandedSymbols: Set<string> = new Set();
  fundamentalSymbols: Set<string> = new Set();
  
  // 診斷相關 (New BS Risk Modal)
  isRiskModalVisible: boolean = false;
  riskModalSymbol: string = '';
  riskModalCost: number = 0;
  
  // 追蹤數值變動狀態以觸發動畫
  valueChanges: Map<string, 'up' | 'down' | null> = new Map();
  private prevValues: Map<string, number> = new Map();

  // [RESTORE] K 線圖狀態
  isChartVisible: boolean = false;
  chartSymbol: string = '';

  showAddForm: boolean = false;
  continuousMode: boolean = false;
  loadingRef: boolean = false;
  newRecord: any = { symbol: '', name: '', price: null, shares: null, date: new Date().toISOString().split('T')[0], exchangeRate: 1.0 };
  searchResults: any[] = [];
  searchTimeout: any;

  constructor(private readonly stockService: StockService) { }

  getTotalMarketValue(): number {
    return this.displayInventory.reduce((sum, group) => sum + (group.marketValueTWD || 0), 0);
  }

  getTotalCost(): number {
    return this.displayInventory.reduce((sum, group) => sum + (group.totalCostTWD || 0), 0);
  }

  getDailyChange(): { amount: number; percent: number } {
    let totalCurrent = 0;
    let totalPrev = 0;
    this.displayInventory.forEach(group => {
      totalCurrent += group.marketValueTWD;
      totalPrev += (group.marketValueTWD / (1 + (group.roi / 100)));
    });
    const amount = totalCurrent - totalPrev;
    const percent = totalPrev > 0 ? (amount / totalPrev) : 0;
    return { amount, percent };
  }

  getRangePosition(current: number, high: number, low: number): number {
    if (!high || !low || high === low) return 50;
    const pos = ((current - low) / (high - low)) * 100;
    return Math.min(Math.max(pos, 0), 100);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['groupedInventory']) {
      this.applyFilterAndSort();
    }
  }

  openChart(event: Event, symbol: string): void {
    event.stopPropagation();
    this.chartSymbol = symbol;
    this.isChartVisible = true;
  }

  toggleExpand(symbol: string): void {
    if (this.expandedSymbols.has(symbol)) this.expandedSymbols.delete(symbol);
    else this.expandedSymbols.add(symbol);
  }

  toggleFundamental(symbol: string): void {
    if (this.fundamentalSymbols.has(symbol)) this.fundamentalSymbols.delete(symbol);
    else this.fundamentalSymbols.add(symbol);
  }

  toggleQuant(event: Event, symbol: string, cost: number = 0): void {
    event.stopPropagation();
    this.riskModalSymbol = symbol;
    this.riskModalCost = cost;
    this.isRiskModalVisible = true;
  }

  applyFilterAndSort(): void {
    if (!this.groupedInventory) return;

    // 診斷：檢查第一筆台股的基本面數據
    const twStock = this.groupedInventory.find(s => s.symbol.includes('.TW'));
    if (twStock) {
      console.log(`[UI Audit] ${twStock.symbol} Data:`, {
        pe: twStock.peRatio,
        yield: twStock.dividendYield,
        pb: twStock.pbRatio,
        nav: twStock.nav
      });
    }

    this.groupedInventory.forEach(item => {
      const prev = this.prevValues.get(item.symbol);
      if (prev !== undefined && prev !== item.marketValueTWD) {
        const direction = item.marketValueTWD > prev ? 'up' : 'down';
        this.valueChanges.set(item.symbol, direction);
        setTimeout(() => this.valueChanges.delete(item.symbol), 1000);
      }
      this.prevValues.set(item.symbol, item.marketValueTWD);
    });

    let filtered = [...this.groupedInventory];
    if (this.searchQuery.trim()) {
      const q = this.searchQuery.toLowerCase();
      filtered = filtered.filter(item => item.symbol.toLowerCase().includes(q) || item.name.toLowerCase().includes(q));
    }
    filtered.sort((a, b) => {
      switch (this.sortMode) {
        case 'roi-desc': return b.roi - a.roi;
        case 'roi-asc': return a.roi - b.roi;
        case 'value-desc': return (b.marketValueTWD || 0) - (a.marketValueTWD || 0);
        case 'value-asc': return (a.marketValueTWD || 0) - (b.marketValueTWD || 0);
        default: return 0;
      }
    });
    this.displayInventory = filtered;
  }

  onSearchInput(query: string): void {
    if (this.searchTimeout) clearTimeout(this.searchTimeout);
    this.searchTimeout = setTimeout(() => {
      this.stockService.searchStock(query).subscribe({
        next: (res) => this.searchResults = res.results || []
      });
    }, 500);
  }

  selectSearchResult(symbol: string, name: string): void {
    this.newRecord.symbol = symbol;
    this.newRecord.name = name;
    this.searchResults = [];
    this.fetchReferenceData();
  }

  fetchReferenceData(): void {
    if (!this.newRecord.symbol || !this.newRecord.date) return;
    this.loadingRef = true;
    this.stockService.getReferenceData(this.newRecord.symbol, this.newRecord.date).subscribe({
      next: (res) => {
        this.newRecord.price = res.price;
        this.newRecord.exchangeRate = res.exchangeRate;
        this.loadingRef = false;
      },
      error: () => this.loadingRef = false
    });
  }

  submitAddInventory(): void {
    this.stockService.addInventory(this.newRecord).subscribe({
      next: () => {
        if (!this.continuousMode) {
          this.showAddForm = false;
          this.newRecord = { symbol: '', name: '', price: null, shares: null, date: new Date().toISOString().split('T')[0], exchangeRate: 1.0 };
        } else {
          this.newRecord.shares = null;
          this.fetchReferenceData();
        }
        this.dataChanged.emit();
      }
    });
  }

  deleteRecord(id: string): void {
    if (confirm('確定要刪除這筆交易紀錄嗎？')) {
      this.stockService.removeInventory(id).subscribe({ next: () => this.dataChanged.emit() });
    }
  }

  deleteInventoryGroup(event: Event, symbol: string): void {
    event.stopPropagation();
    if (confirm(`確定要刪除 ${symbol} 的所有庫存紀錄嗎？`)) {
      this.stockService.removeInventoryGroup(symbol).subscribe({ next: () => this.dataChanged.emit() });
    }
  }
}