import { Component, Input, Output, EventEmitter, OnInit, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-risk-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './risk-modal.html',
  styleUrls: ['./risk-modal.scss']
})
export class RiskModalComponent implements OnInit, OnChanges {
  @Input() symbol: string = '';
  @Input() isVisible: boolean = false;
  @Input() cost: number = 0;
  @Output() closeEvent = new EventEmitter<void>();

  loading: boolean = false;
  report: any = null;
  
  // AI 互動相關
  aiInsight: string = '';
  isAsking: boolean = false;

  constructor(private http: HttpClient) {}

  ngOnInit(): void {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['isVisible'] && this.isVisible && this.symbol) {
      this.fetchReport();
    }
  }

  fetchReport(): void {
    console.log(`[RiskModal] 開始抓取報告: ${this.symbol}, 傳入成本: ${this.cost}`);
    this.loading = true;
    this.report = null;
    this.aiInsight = '';
    this.http.get<any>(`/api/diagnostic/full-report?symbol=${this.symbol}`).subscribe({
      next: (res) => {
        console.log(`[RiskModal] 收到數據:`, res);
        this.report = res;
        this.loading = false;
        // 生成即時靜態洞察 (放寬成本檢查，若為 0 則不計算 PnL)
        if (res && res.risk) {
          this.aiInsight = this.generateInsight(res);
        }
      },
      error: (err) => {
        console.error(`[RiskModal] 抓取失敗:`, err);
        this.aiInsight = '數據載入失敗，請稍後再試。';
        this.loading = false;
      }
    });
  }

  generateInsight(data: any): string {
    const hasCost = this.cost && !isNaN(this.cost) && this.cost > 0;
    const pnl = hasCost ? ((data.price - this.cost) / this.cost) * 100 : 0;
    
    const volLevel = data.risk.hv20 > 40 ? '偏高' : data.risk.hv20 > 25 ? '正常' : '平穩';
    const sharpeLevel = data.risk.sharpe > 1.5 ? '優秀' : data.risk.sharpe > 1.0 ? '良好' : '普通';
    
    let text = `【診斷報告】\n`;
    if (hasCost) {
      text += `標的目前損益為 ${pnl.toFixed(2)}%，`;
    } else {
      text += `標的當前價格為 ${data.price}，`;
    }
    
    text += `波動率處於 ${volLevel} 區間。\n`;
    text += `夏普值為 ${data.risk.sharpe.toFixed(2)} (${sharpeLevel})，代表單位風險獲利能力 ${sharpeLevel === '優秀' ? '極佳' : '尚可'}。\n`;
    text += `VaR 顯示有 95% 機率單日跌幅不超過 ${data.risk.dailyVaR95.toFixed(2)}%。\n`;
    text += `建議：${hasCost && pnl < -5 ? '觸及初步停損警示，建議檢視基本面。' : '持倉風險受控，可繼續持有。'}`;
    
    return text;
  }

  getRangePos(): number {
    if (!this.report?.risk) return 50;
    const price = this.report.price;
    const lower = this.report.risk.lowerBond;
    const upper = this.report.risk.upperBond;
    if (!lower || !upper || upper === lower) return 50;
    const pos = ((price - lower) / (upper - lower)) * 100;
    return Math.min(Math.max(pos, 0), 100);
  }

  askSpecific(text: string): void {
    if (this.isAsking) return;
    this.isAsking = true;
    this.aiInsight = '';

    this.http.post<any>('/api/diagnostic/ask', {
      symbol: this.symbol,
      question: text
    }).subscribe({
      next: (res) => {
        this.aiInsight = res.answer;
        this.isAsking = false;
      },
      error: () => {
        this.aiInsight = '顧問暫時無法回應，請檢查 API 設定。';
        this.isAsking = false;
      }
    });
  }

  close(): void {
    this.closeEvent.emit();
  }
}
