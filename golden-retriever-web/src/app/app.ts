import { Component, signal, OnInit, OnDestroy, Inject, PLATFORM_ID, computed } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SidebarComponent } from './components/sidebar/sidebar';
import { InventoryComponent } from './components/inventory/inventory';
import { QuantComponent } from './components/quant/quant';
import { StockService, DashboardResponse, GoldResponse, RatesResponse } from './services/stock';
import { interval, Subscription } from 'rxjs';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule, SidebarComponent, InventoryComponent, QuantComponent],
  templateUrl: './app.html',
  styleUrls: ['./app.scss']
})
export class AppComponent implements OnInit, OnDestroy {
  public dashboardData = signal<DashboardResponse | null>(null);
  public goldData = signal<GoldResponse | null>(null);
  public ratesData = signal<RatesResponse | null>(null);
  public fugleKey = signal('');
  public isLoading = signal(true);
  
  public fugleQuota = signal<number | null>(null);

  public isSettingsVisible = signal(false);
  public isCurrencyModalVisible = signal(false);
  public newCurrencyCode = signal('USD');

  private refreshSub?: Subscription;
  private quotaSub?: Subscription;
  private isBrowser: boolean;

  constructor(
    private readonly stockService: StockService,
    @Inject(PLATFORM_ID) platformId: Object
  ) {
    this.isBrowser = isPlatformBrowser(platformId);
  }

  ngOnInit() {
    if (this.isBrowser) {
      this.fugleKey.set(localStorage.getItem('FUGLE_API_KEY') || '');
      this.refreshAllData();
      
      // 每 30 秒靜默刷新數據，不觸發全螢幕 Loading
      this.refreshSub = interval(30000).subscribe(() => this.refreshAllData(true));
      
      this.refreshQuota();
      this.quotaSub = interval(10000).subscribe(() => this.refreshQuota());
    }
  }

  ngOnDestroy() {
    this.refreshSub?.unsubscribe();
    this.quotaSub?.unsubscribe();
  }

  public refreshQuota() {
    this.stockService.getFugleQuota().subscribe({
      next: (res) => this.fugleQuota.set(res.remaining)
    });
  }

  public refreshAllData(quiet: boolean = false) {
    if (!quiet) this.isLoading.set(true);
    
    this.stockService.getDashboardData().subscribe({
      next: (data) => {
        this.dashboardData.set(data);
        if (!quiet) this.isLoading.set(false);
      },
      error: () => {
        if (!quiet) this.isLoading.set(false);
      }
    });

    this.stockService.getGoldData().subscribe(data => this.goldData.set(data));
    this.stockService.getRatesData().subscribe(data => this.ratesData.set(data));
  }

  public openSettings() { this.isSettingsVisible.set(true); }

  public saveSettings() {
    const key = this.fugleKey();
    if (this.isBrowser) {
      if (key) localStorage.setItem('FUGLE_API_KEY', key);
      else localStorage.removeItem('FUGLE_API_KEY');
    }
    this.stockService.saveConfig('FUGLE_API_KEY', key, 'Fugle Market Data API Key').subscribe(() => {
      this.isSettingsVisible.set(false);
      this.refreshAllData();
    });
  }

  public openAddCurrencyModal() { this.isCurrencyModalVisible.set(true); }

  public confirmAddCurrency() {
    this.stockService.addCurrency(this.newCurrencyCode()).subscribe(() => {
      this.isCurrencyModalVisible.set(false);
      this.refreshAllData();
    });
  }

  public removeCurrency(code: string, event: Event) {
    event.stopPropagation();
    if (confirm(`確定要移除 ${code} 嗎？`)) {
      this.stockService.removeCurrency(code).subscribe(() => {
        this.refreshAllData();
      });
    }
  }
}