import { Component, Input, Output, EventEmitter, OnChanges, SimpleChanges, OnDestroy, ViewChild, ElementRef, HostListener, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StockService } from '../../services/stock';
import { createChart, IChartApi, ISeriesApi, CandlestickSeries, LineSeries, ColorType } from 'lightweight-charts';

@Component({
  selector: 'app-chart-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chart-modal.html',
  styleUrls: ['./chart-modal.scss']
})
export class ChartModalComponent implements OnChanges, OnDestroy {
  @Input() symbol: string = '';
  @Input() isVisible: boolean = false;
  @Output() closeEvent = new EventEmitter<void>();

  @ViewChild('chartContainer') chartContainer!: ElementRef;

  private chart: IChartApi | null = null;
  private candlestickSeries: ISeriesApi<"Candlestick"> | null = null;
  private ma5Series: ISeriesApi<"Line"> | null = null;
  private ma20Series: ISeriesApi<"Line"> | null = null;
  private ma60Series: ISeriesApi<"Line"> | null = null;

  public currentSource: string = 'yahoo';
  public currentPeriod: string = '1d'; // 預設日K
  public fugleKey: string = '';
  public isLoading: boolean = false;
  public errorMessage: string = '';

  private chartData: any[] = [];

  constructor(
    private readonly stockService: StockService,
    private readonly cdr: ChangeDetectorRef
  ) { }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['isVisible'] && this.isVisible && this.symbol) {
      this.currentSource = (this.symbol.includes('.TW') || this.symbol.includes('.TWO')) ? 'twse' : 'yahoo';
      this.currentPeriod = '1d'; // 切換股票時重置為日K
      this.loadAndRenderChart();
    }

    if (changes['isVisible'] && !this.isVisible) {
      this.destroyChart();
    }
  }

  @HostListener('window:resize')
  onResize() {
    if (this.chart && this.chartContainer) {
      this.chart.applyOptions({
        width: this.chartContainer.nativeElement.clientWidth,
        height: this.chartContainer.nativeElement.clientHeight
      });
    }
  }

  ngOnDestroy(): void {
    this.destroyChart();
  }

  setChartSource(source: string): void {
    this.currentSource = source;
    this.loadAndRenderChart();
  }

  setChartPeriod(period: string): void {
    this.currentPeriod = period;
    this.loadAndRenderChart();
  }

  loadAndRenderChart(): void {
    this.isLoading = true;
    this.errorMessage = '';
    this.cdr.detectChanges();

    if (this.currentSource === 'fugle' && !this.fugleKey) {
      this.stockService.getConfig('FUGLE_API_KEY').subscribe(res => {
        if (res.value) {
          this.fugleKey = res.value;
        }
        this.executeChartRequest();
      });
    } else {
      this.executeChartRequest();
    }
  }

  private executeChartRequest(): void {
    if (this.currentSource === 'fugle' && this.fugleKey) {
      const cleanKey = this.fugleKey.trim();
      this.stockService.saveConfig('FUGLE_API_KEY', cleanKey, '富果 API 金鑰').subscribe();
    }

    // 傳遞週期參數
    this.stockService.getChartData(this.symbol, this.currentSource, this.fugleKey, this.currentPeriod).subscribe({
      next: (res) => {
        this.isLoading = false;
        if (res.success && res.data && res.data.length > 0) {
          this.chartData = res.data;
          requestAnimationFrame(() => {
            this.renderChartGraphic();
            this.cdr.detectChanges();
          });
        } else {
          this.errorMessage = res.error || '無效資料';
          this.destroyChart();
          this.cdr.detectChanges();
        }
      },
      error: () => {
        this.isLoading = false;
        this.errorMessage = '網路錯誤';
        this.destroyChart();
        this.cdr.detectChanges();
      }
    });
  }

  private calculateSMA(data: any[], period: number) {
    const result = [];
    for (let i = 0; i < data.length; i++) {
      if (i < period - 1) continue;
      let sum = 0;
      for (let j = 0; j < period; j++) {
        sum += data[i - j].close;
      }
      result.push({ time: data[i].time, value: sum / period });
    }
    return result;
  }

  renderChartGraphic(): void {
    if (!this.chartContainer) return;
    this.destroyChart();

    const container = this.chartContainer.nativeElement;
    const width = container.clientWidth || 800;
    const height = container.clientHeight || 400;

    this.chart = createChart(container, {
      width: width,
      height: height,
      layout: { background: { type: ColorType.Solid, color: '#ffffff' }, textColor: '#333' },
      grid: { vertLines: { color: '#f4f4f4' }, horzLines: { color: '#f4f4f4' } },
      timeScale: { borderColor: '#cccccc', timeVisible: true }
    });

    this.candlestickSeries = this.chart.addSeries(CandlestickSeries, {
      upColor: '#ef5350', downColor: '#26a69a',
      borderVisible: false, wickUpColor: '#ef5350', wickDownColor: '#26a69a'
    });

    const formattedData = this.chartData.map(d => ({
      time: d.time,
      open: Number(d.open),
      high: Number(d.high),
      low: Number(d.low),
      close: Number(d.close)
    }));

    this.candlestickSeries.setData(formattedData);

    this.ma5Series = this.chart.addSeries(LineSeries, { color: '#2196F3', lineWidth: 1, title: 'MA5', lastValueVisible: false, priceLineVisible: false });
    this.ma5Series.setData(this.calculateSMA(formattedData, 5));

    this.ma20Series = this.chart.addSeries(LineSeries, { color: '#FF9800', lineWidth: 1, title: 'MA20', lastValueVisible: false, priceLineVisible: false });
    this.ma20Series.setData(this.calculateSMA(formattedData, 20));

    this.ma60Series = this.chart.addSeries(LineSeries, { color: '#9C27B0', lineWidth: 1, title: 'MA60', lastValueVisible: false, priceLineVisible: false });
    this.ma60Series.setData(this.calculateSMA(formattedData, 60));

    this.chart.timeScale().fitContent();
    setTimeout(() => {
      if (this.chart && container) {
        this.chart.resize(container.clientWidth, container.clientHeight);
      }
    }, 50);
  }

  destroyChart(): void {
    if (this.chart) {
      this.chart.remove();
      this.chart = null;
    }
  }

  closeModal(): void {
    this.closeEvent.emit();
  }
}
