import { Component, Input, OnChanges, SimpleChanges, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StockService, GroupedInventory } from '../../services/stock';
import { ChartModalComponent } from '../chart-modal/chart-modal';

@Component({
  selector: 'app-inventory',
  standalone: true,
  imports: [CommonModule, FormsModule, ChartModalComponent],
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
  
  diagnosisData: Map<string, any> = new Map();
  loadingDiagnosis: Set<string> = new Set();
  
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
      // 假設 group.records 包含所有明細，我們需要計算昨收總值 vs 現價總值
      // 這裡簡單計算：總市值 - (昨收 * 總股數 * 匯率)
      const prevValueTWD = (group.totalShares || 0) * (group.records?.[0]?.exchangeRate || 1) * (group.currentPrice - (group.currentPrice * (group.roi / 100))); 
      // 實際上後端應該提供更精確的昨收數據，這裡先做簡易估算
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

  // [RESTORE] 開啟 K 線圖
  openChart(event: Event, symbol: string): void {
    event.stopPropagation();
    this.chartSymbol = symbol;
    this.isChartVisible = true;
  }

  toggleExpand(symbol: string): void {
    if (this.expandedSymbols.has(symbol)) this.expandedSymbols.delete(symbol);
    else this.expandedSymbols.add(symbol);
  }

  toggleQuant(event: Event, symbol: string): void {
    event.stopPropagation();
    if (this.diagnosisData.has(symbol)) {
      this.diagnosisData.get(symbol).isVisible = !this.diagnosisData.get(symbol).isVisible;
      return;
    }
    this.loadingDiagnosis.add(symbol);
    this.stockService.diagnoseStock(symbol).subscribe({
      next: (res) => {
        if (res.diagnosis && res.diagnosis[symbol.replace('.TW','')]) {
          const data = res.diagnosis[symbol.replace('.TW','')];
          this.diagnosisData.set(symbol, { ...data, isVisible: true });
        }
        this.loadingDiagnosis.delete(symbol);
      },
      error: () => this.loadingDiagnosis.delete(symbol)
    });
  }

  applyFilterAndSort(): void {
    if (!this.groupedInventory) return;

    // 偵測數值變動
    this.groupedInventory.forEach(item => {
      const prev = this.prevValues.get(item.symbol);
      if (prev !== undefined && prev !== item.marketValueTWD) {
        const direction = item.marketValueTWD > prev ? 'up' : 'down';
        this.valueChanges.set(item.symbol, direction);
        setTimeout(() => this.valueChanges.delete(item.symbol), 1000); // 1秒後移除動畫
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
          // 連續模式：保留代碼與名稱，清空股數與價格，並重新獲取參考數據
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