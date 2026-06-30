import { Component, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StockService } from '../../services/stock';

@Component({
  selector: 'app-quant',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './quant.html',
  styleUrl: './quant.scss'
})
export class QuantComponent implements OnInit {
  public topStocks = signal<any[]>([]);
  public performance = signal<any>(null);
  public isLoading = signal(false);
  public errorMessage = signal<string | null>(null);

  // 用戶自定義參數
  public stockCount = signal(10);
  public initialCapital = signal(1000000);

  // 追蹤當前資料是否為過時參數產生的
  public lastFetchedParams = signal<{count: number, capital: number} | null>(null);
  public isOutdated = computed(() => {
    const last = this.lastFetchedParams();
    if (!last) return false;
    return last.count !== this.stockCount() || last.capital !== this.initialCapital();
  });

  constructor(private stockService: StockService) {}

  ngOnInit() {
    // 改為手動呼叫
  }

  public fetchQuantData() {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.topStocks.set([]);
    this.performance.set(null);

    const currentCount = this.stockCount();
    const currentCapital = this.initialCapital();

    this.stockService.getQuantTopStocks(currentCount, currentCapital).subscribe({
      next: (res: any) => {
        if (res && res.stocks && Array.isArray(res.stocks)) {
          this.topStocks.set(res.stocks);
          this.performance.set(res.performance);
          // 紀錄本次成功獲取資料的參數
          this.lastFetchedParams.set({ count: currentCount, capital: currentCapital });
        } else if (res && res.error) {
          this.errorMessage.set(res.error);
        } else {
          this.errorMessage.set('回傳資料格式錯誤');
        }
        this.isLoading.set(false);
      },
      error: (err) => {
        console.error('Quant fetch error:', err);
        this.errorMessage.set('無法連線至量化伺服器');
        this.isLoading.set(false);
      }
    });
  }
}