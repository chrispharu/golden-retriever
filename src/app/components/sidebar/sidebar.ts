import { Component, Input, OnInit, Output, EventEmitter, OnDestroy, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StockService, PortfolioItem } from '../../services/stock';
import { ChartModalComponent } from '../chart-modal/chart-modal';
import { Subject, Subscription } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, FormsModule, ChartModalComponent],
  templateUrl: './sidebar.html',
  styleUrls: ['./sidebar.scss']
})
export class SidebarComponent implements OnInit, OnDestroy {
  @Input() portfolio: PortfolioItem[] = [];
  @Output() dataChanged = new EventEmitter<void>();

  @ViewChild('sharesInput') sharesInput?: ElementRef;

  public newStockSymbol: string = '';
  public searchResults: any[] = [];
  public contextSymbol: string | null = null;
  public chartSymbol: string = '';
  public isChartVisible: boolean = false;

  public quickAddSymbol: string | null = null;
  public quickAddData: any = { price: null, shares: null, date: new Date().toISOString().split('T')[0], exchangeRate: 1.0 };
  public loadingRef: boolean = false;
  
  public fundamentalSymbols: Set<string> = new Set();

  private searchSubject = new Subject<string>();
  private searchSubscription?: Subscription;

  constructor(private stockService: StockService) {}

  ngOnInit() {
    this.searchSubscription = this.searchSubject.pipe(
      debounceTime(300),
      distinctUntilChanged()
    ).subscribe(query => {
      this.executeSearch(query);
    });
  }

  ngOnDestroy() {
    this.searchSubscription?.unsubscribe();
  }

  onSearchInput(query: string): void {
    if (!query.trim()) {
      this.searchResults = [];
      return;
    }
    this.searchSubject.next(query);
  }

  private executeSearch(query: string): void {
    this.stockService.searchStock(query).subscribe({
      next: (res) => {
        this.searchResults = res.results || [];
      },
      error: () => {
        this.searchResults = [];
      }
    });
  }

  calculateTotal(): number {
    return this.portfolio.reduce((sum, item) => sum + (item.costTWD || 0), 0);
  }

  getDailyChange(): { amount: number; percent: number } {
    const totalCurrent = this.portfolio.reduce((sum, item) => sum + (item.price * item.rate * (item.costTWD / (item.avgCostTWD || 1))), 0);
    const totalPrev = this.portfolio.reduce((sum, item) => sum + (item.prevClose * item.rate * (item.costTWD / (item.avgCostTWD || 1))), 0);
    const amount = totalCurrent - totalPrev;
    const percent = totalPrev > 0 ? (amount / totalPrev) : 0;
    return { amount, percent };
  }

  getRangePosition(current: number, high: number, low: number): number {
    if (!high || !low || high === low) return 50;
    const pos = ((current - low) / (high - low)) * 100;
    return Math.min(Math.max(pos, 0), 100);
  }

  addStock(): void {
    if (!this.newStockSymbol) return;
    this.stockService.addStock(this.newStockSymbol).subscribe(() => {
      this.newStockSymbol = '';
      this.searchResults = [];
      this.dataChanged.emit();
    });
  }

  selectSearchResult(symbol: string, name: string): void {
    this.newStockSymbol = symbol;
    this.addStock();
  }

  onRename(symbol: string): void {
    const newName = prompt(`修改 ${symbol} 的顯示名稱：`);
    if (newName && newName.trim()) {
      this.stockService.renameStock(symbol, newName.trim()).subscribe(() => {
        this.dataChanged.emit();
      });
    }
  }

  onRemove(symbol: string): void {
    if (confirm(`確定要將 ${symbol} 從清單中移除嗎？`)) {
      this.stockService.removeStock(symbol).subscribe(() => {
        this.dataChanged.emit();
      });
    }
  }

  openChart(symbol: string): void {
    this.chartSymbol = symbol;
    this.isChartVisible = true;
  }

  toggleQuickAdd(event: Event, stock: PortfolioItem) {
    event.stopPropagation();
    if (this.quickAddSymbol === stock.symbol) {
      this.quickAddSymbol = null;
    } else {
      this.quickAddSymbol = stock.symbol;
      this.quickAddData = {
        symbol: stock.symbol,
        name: stock.name,
        price: stock.price,
        shares: 0,
        date: new Date().toISOString().split('T')[0],
        exchangeRate: stock.rate || 1
      };
      this.fetchReferenceData();
      
      setTimeout(() => {
        this.sharesInput?.nativeElement.focus();
      }, 100);
    }
  }

  toggleFundamental(symbol: string) {
    if (this.fundamentalSymbols.has(symbol)) {
      this.fundamentalSymbols.delete(symbol);
    } else {
      this.fundamentalSymbols.add(symbol);
    }
  }

  fetchReferenceData(): void {
    if (!this.quickAddSymbol) return;
    this.loadingRef = true;
    this.stockService.getReferenceData(this.quickAddSymbol, this.quickAddData.date).subscribe({
      next: (res) => {
        this.quickAddData.price = res.price;
        this.quickAddData.exchangeRate = res.exchangeRate;
        this.loadingRef = false;
      },
      error: () => this.loadingRef = false
    });
  }

  submitQuickAdd(event: Event): void {
    event.stopPropagation();
    this.stockService.addInventory(this.quickAddData).subscribe(() => {
      this.quickAddSymbol = null;
      this.dataChanged.emit();
    });
  }

  closeChartModal(): void { this.isChartVisible = false; }

  openStockLink(url: string | undefined): void {
    if (url) window.open(url, '_blank');
  }
}