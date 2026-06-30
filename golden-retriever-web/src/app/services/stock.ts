import { Injectable, Inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';

export interface DashboardResponse {
  updatedAt: string;
  portfolio: PortfolioItem[];
  groupedInventory: GroupedInventory[];
  gold: GoldPrice;
  benchmarks: { [key: string]: BenchmarkItem };
  allocation: AllocationDto;
}

export interface AllocationDto {
  byCurrency: { [key: string]: number };
  byAsset: { [key: string]: number };
  byType: { [key: string]: number };
}

export interface BenchmarkItem {
  symbol: string;
  name: string;
  price: number;
  changePercent: number;
}

export interface GoldResponse {
  gold: GoldPrice;
}

export interface RatesResponse {
  rates: { [key: string]: { price: number; prevClose: number } };
  currencyOrder: string[];
}

export interface PortfolioItem {
  symbol: string;
  name: string;
  currency: string;
  price: number;
  prevClose: number;
  high?: number;
  low?: number;
  fiftyTwoWeekHigh?: number;
  fiftyTwoWeekLow?: number;
  volume?: number;
  avgVolume?: number;
  marketStatus: string;
  postMarketPrice: number;
  rate: number;
  costTWD: number;
  avgCost: number;
  avgCostTWD: number;
  link: string;
  volStatus?: string;
  range52w?: number;
  nav?: number;
  premium?: number;
  peRatio?: number;
  dividendYield?: number;
  pbRatio?: number;
  foreignBuy?: number;
  trustBuy?: number;
}

export interface InventoryRecord {
  id: string;
  symbol: string;
  price: number;
  shares: number;
  date: string;
  exchangeRate: number;
}

export interface GroupedInventory {
  symbol: string;
  name: string;
  isTW: boolean;
  totalShares: number;
  totalCostTWD: number;
  totalCostOriginal: number;
  records: InventoryRecord[];
  currentPrice: number;
  marketStatus: string;
  postMarketPrice: number;
  currentRate: number;
  marketValueTWD: number;
  roi: number;
  plAmountTWD: number;
  pricePLTWD: number;
  exchangePLTWD: number;
  totalDividendsTWD: number;
  hasValidRate: boolean;
  aiScore?: number;
  roe?: number;
  peRatio?: number;
  dividendYield?: number;
  pbRatio?: number;
  bias?: number;
  foreignBuy?: number;
  trustBuy?: number;
  fiftyTwoWeekHigh?: number;
  fiftyTwoWeekLow?: number;
  volume?: number;
  avgVolume?: number;
  nav?: number;       // ETF Net Asset Value
  premium?: number;   // ETF Premium/Discount (%)
}

export interface GoldPrice {
  symbol: string;
  priceUSD: number;
  priceTWD: number;
  change: number;
  changePercent: number;
  updateTime: string;
  isFallback: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class StockService {
  private isBrowser: boolean;

  constructor(
    private http: HttpClient,
    @Inject(PLATFORM_ID) platformId: Object
  ) {
    this.isBrowser = isPlatformBrowser(platformId);
  }

  getDashboardData(): Observable<DashboardResponse> {
    const fugleKey = this.isBrowser ? (localStorage.getItem('FUGLE_API_KEY') || '') : '';
    const headers = { 'X-Fugle-Key': fugleKey };
    return this.http.get<DashboardResponse>(`/api/stocks?_t=${new Date().getTime()}`, { headers });
  }

  getGoldData(): Observable<GoldResponse> {
    return this.http.get<GoldResponse>(`/api/gold?_t=${new Date().getTime()}`);
  }

  getRatesData(): Observable<RatesResponse> {
    return this.http.get<RatesResponse>(`/api/rates?_t=${new Date().getTime()}`);
  }

  addStock(symbol: string, newName: string = ''): Observable<any> {
    return this.http.post('/api/add-stock', { symbol, newName });
  }

  removeStock(symbol: string): Observable<any> {
    return this.http.post('/api/remove-stock', { symbol });
  }

  addInventory(data: any): Observable<any> {
    return this.http.post('/api/add-inventory', data);
  }

  removeInventory(id: string): Observable<any> {
    return this.http.post('/api/remove-inventory', { id });
  }

  renameStock(symbol: string, newName: string): Observable<any> {
    return this.http.post('/api/rename-stock', { symbol, newName });
  }

  searchStock(query: string): Observable<any> {
    return this.http.get(`/api/search?q=${query}`);
  }

  removeInventoryGroup(symbol: string): Observable<any> {
    return this.http.post('/api/remove-inventory-group', { symbol });
  }

  getReferenceData(symbol: string, date: string): Observable<any> {
    return this.http.get(`/api/inventory/reference-data?symbol=${symbol}&date=${date}`);
  }

  getChartData(symbol: string, source: string, fugleKey: string, period: string): Observable<any> {
    return this.http.get(`/api/chart?symbol=${symbol}&source=${source}&fugleKey=${fugleKey}&period=${period}`);
  }

  saveConfig(key: string, value: string, desc: string = ''): Observable<any> {
    return this.http.post('/api/save-config', { key, value, desc });
  }

  getConfig(key: string): Observable<{ value: string }> {
    return this.http.get<{ value: string }>(`/api/config/${key}`);
  }

  addCurrency(currency: string): Observable<any> {
    return this.http.post('/api/add-currency', { currency });
  }

  removeCurrency(currency: string): Observable<any> {
    return this.http.post('/api/remove-currency', { currency });
  }

  reorderCurrencies(newOrder: string[]): Observable<any> {
    return this.http.post('/api/reorder-currencies', { newOrder });
  }

  // [NEW] FinLab 量化接口
  getQuantTopStocks(count: number = 10, capital: number = 1000000): Observable<any[]> {
    return this.http.get<any[]>(`/api/quant/top-stocks?count=${count}&capital=${capital}`);
  }

  diagnoseStock(symbol: string): Observable<any> {
    return this.http.get<any>(`/api/quant/diagnose?symbol=${symbol}`);
  }

  getFugleQuota(): Observable<{ remaining: number }> {
    return this.http.get<{ remaining: number }>('/api/system/fugle-quota');
  }
}