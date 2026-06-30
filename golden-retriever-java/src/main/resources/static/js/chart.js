import { state } from './state.js';
import * as API from './api.js';

export let currentChart = null;
export let currentChartSource = 'twse';
export let currentCandleSeries = null;
export let lastHistoricalCandle = null;
export let liveUpdateInterval = null;
export let maSeriesMap = {}; // 用來儲存均線實體
export let globalChartData = []; // 儲存歷史資料供即時運算

// 數學魔法：簡單移動平均線 (SMA) 計算機
function calculateSMA(data, period) {
    const result = [];
    for (let i = 0; i < data.length; i++) {
        if (i < period - 1) continue; // 前幾天資料不足，不畫線
        let sum = 0;
        for (let j = 0; j < period; j++) {
            sum += data[i - j].close;
        }
        result.push({ time: data[i].time, value: sum / period });
    }
    return result;
}
export function setCurrentChartSource(source) {
    currentChartSource = source;
}

export function clearLiveUpdateInterval() {
    if (liveUpdateInterval) {
        clearInterval(liveUpdateInterval);
        liveUpdateInterval = null;
    }
}

export function updateLiveCandle() {
    if (!currentCandleSeries || !state.ctxSymbol || !lastHistoricalCandle) return;

    const stock = state.data?.portfolio?.find(s => s.symbol === state.ctxSymbol);
    if (!stock) return;

    const parsePrice = (val) => {
        if (val === undefined || val === null || val === '-') return NaN;
        return parseFloat(val.toString().replace(/,/g, ''));
    };

    const currentPrice = parsePrice(stock.price);
    const prevClose = parsePrice(stock.prevClose);

    let lastTimeStr = '';
    if (typeof lastHistoricalCandle.time === 'object') {
        const ty = lastHistoricalCandle.time.year;
        const tm = String(lastHistoricalCandle.time.month).padStart(2, '0');
        const td = String(lastHistoricalCandle.time.day).padStart(2, '0');
        lastTimeStr = `${ty}-${tm}-${td}`;
    } else {
        lastTimeStr = lastHistoricalCandle.time;
    }

    const today = new Date();
    const yyyy = today.getFullYear();
    const mm = String(today.getMonth() + 1).padStart(2, '0');
    const dd = String(today.getDate()).padStart(2, '0');
    const todayStr = `${yyyy}-${mm}-${dd}`;

    let targetTime = lastHistoricalCandle.time;
    let targetOpen = lastHistoricalCandle.open;

    if (todayStr > lastTimeStr) {
        targetTime = todayStr;
        targetOpen = (!isNaN(prevClose) && prevClose > 0) ? prevClose : (currentPrice || lastHistoricalCandle.close);
    }

    const validPrice = !isNaN(currentPrice) ? currentPrice : targetOpen;

    let currentHigh = parsePrice(stock.high);
    if (isNaN(currentHigh) || currentHigh <= 0) currentHigh = validPrice;

    let currentLow = parsePrice(stock.low);
    if (isNaN(currentLow) || currentLow <= 0) currentLow = validPrice;

    const finalHigh = Math.max(currentHigh, validPrice, targetOpen);
    const finalLow = Math.min(currentLow, validPrice, targetOpen);

    try {
        // 注入完美的跳動數據！
        currentCandleSeries.update({
            time: targetTime,
            open: targetOpen,
            high: finalHigh,
            low: finalLow,
            close: validPrice
        });

        // 讓均線末端也跟著現價跳動！
        if (globalChartData.length > 0) {
            // 拷貝一份最後的資料並覆蓋現價
            const liveData = [...globalChartData];
            if (targetTime === liveData[liveData.length - 1].time) {
                liveData[liveData.length - 1].close = validPrice; // 更新今天
            } else {
                liveData.push({ time: targetTime, close: validPrice }); // 插入今天
            }

            // 更新每一條均線的最後一個點
            Object.keys(maSeriesMap).forEach(periodStr => {
                const period = parseInt(periodStr);
                if (liveData.length >= period) {
                    let sum = 0;
                    for (let j = 0; j < period; j++) sum += liveData[liveData.length - 1 - j].close;
                    maSeriesMap[period].update({ time: targetTime, value: sum / period });
                }
            });
        }

    } catch (e) {
        console.error('K棒與均線更新失敗:', e);
    }
}

function askFugleKey() {
    return new Promise((resolve) => {
        const modal = document.getElementById('fugle-modal');
        const input = document.getElementById('fugle-key-input');
        const btnOk = document.getElementById('btn-fugle-ok');
        const btnCancel = document.getElementById('btn-fugle-cancel');

        input.value = '';
        modal.style.display = 'flex';
        input.focus();

        const cleanup = () => {
            modal.style.display = 'none';
            btnOk.onclick = null;
            btnCancel.onclick = null;
        };

        btnOk.onclick = () => {
            cleanup();
            resolve(input.value.trim());
        };

        btnCancel.onclick = () => {
            cleanup();
            resolve(null);
        };
    });
}

export async function showChartModal() {
    document.getElementById('context-menu').style.display = 'none';
    if (!state.ctxSymbol) return;

    const modal = document.getElementById('chart-modal');
    modal.style.display = 'flex';

    const isTW = state.ctxSymbol.includes('.TW') || state.ctxSymbol.includes('.TWO');
    document.getElementById('chart-tw-controls').style.display = isTW ? 'flex' : 'none';

    currentChartSource = isTW ? 'twse' : 'yahoo';
    await loadAndRenderChart();
}

export async function loadAndRenderChart() {
    const title = document.getElementById('chart-title');
    const container = document.getElementById('chart-container');
    const isTW = state.ctxSymbol.includes('.TW') || state.ctxSymbol.includes('.TWO');

    title.innerText = `${state.ctxSymbol} - 載入中...`;
    container.innerHTML = '<div class="status-msg">正在抓取歷史數據...</div>';

    if (isTW) {
        document.getElementById('btn-chart-twse').style.opacity = currentChartSource === 'twse' ? '1' : '0.4';
        document.getElementById('btn-chart-fugle').style.opacity = currentChartSource === 'fugle' ? '1' : '0.4';
    }

    try {
        const baseUrl = window.location.origin.includes('file://') ? 'http://localhost:3000' : '';
        let url = `${baseUrl}/api/chart?symbol=${encodeURIComponent(state.ctxSymbol)}&source=${currentChartSource}`;

        if (currentChartSource === 'fugle') {
            let key = localStorage.getItem('FUGLE_API_KEY');
            if (!key) {
                key = await askFugleKey();
                if (!key) throw new Error('已取消輸入富果 API Key');
                localStorage.setItem('FUGLE_API_KEY', key);
            }
            url += `&fugleKey=${encodeURIComponent(key)}`;
        }

        const res = await fetch(url);
        const json = await res.json();

        if (!json.success) {
            if (json.error && json.error.includes('富果 API Key 錯誤')) localStorage.removeItem('FUGLE_API_KEY');
            throw new Error(json.error || '無法取得資料');
        }
        if (json.data.length === 0) throw new Error('這支股票沒有歷史數據');

        lastHistoricalCandle = json.data[json.data.length - 1];
        container.innerHTML = '';
        let timeDesc = currentChartSource === 'twse' ? '本月日線 (證交所)' : (currentChartSource === 'fugle' ? '近半年日線 (富果)' : '近半年日線');
        title.innerText = `${state.ctxSymbol} ${timeDesc}`;

        if (currentChart) {
            currentChart.remove();
            currentChart = null;
        }

        currentChart = LightweightCharts.createChart(container, {
            width: container.clientWidth,
            height: container.clientHeight,
            layout: { backgroundColor: '#ffffff', textColor: '#333' },
            grid: { vertLines: { color: '#f0f0f0' }, horzLines: { color: '#f0f0f0' } },
            timeScale: { borderColor: '#ccc', timeVisible: true }
        });

        currentCandleSeries = currentChart.addCandlestickSeries({
            upColor: '#e74c3c', downColor: '#27ae60', borderVisible: false, wickUpColor: '#e74c3c', wickDownColor: '#27ae60'
        });

        currentCandleSeries.setData(json.data);
        globalChartData = json.data; // 儲存起來

        // ==========================================
        // 📈 繪製技術分析均線 (5, 10, 20, 60)
        // ==========================================
        const maLines = [
            { period: 5, color: '#2962FF', title: '5MA' },   // 藍色周線
            { period: 10, color: '#FF6D00', title: '10MA' }, // 橘色雙周
            { period: 20, color: '#9C27B0', title: '20MA' }, // 紫色月線
            { period: 60, color: '#009688', title: '60MA' }  // 綠色季線
        ];

        maSeriesMap = {};
        maLines.forEach(line => {
            const series = currentChart.addLineSeries({
                color: line.color,
                lineWidth: 1.5,
                crosshairMarkerVisible: false,
                priceLineVisible: false, // 隱藏右軸的均線現價線，避免太亂
                title: line.title
            });
            series.setData(calculateSMA(json.data, line.period));
            maSeriesMap[line.period] = series;
        });

        currentChart.timeScale().fitContent();
        currentChart.timeScale().fitContent();

        if (liveUpdateInterval) clearInterval(liveUpdateInterval);
        liveUpdateInterval = setInterval(updateLiveCandle, 1000);

    } catch (e) {
        title.innerText = e.message.includes('存取太頻繁') ? '📈 K 線圖 - 無法載入' : `${state.ctxSymbol} - 載入失敗`;
        container.innerHTML = `<div class="status-msg error text-danger-bold">❌ ${e.message}</div>`;
        if (e.message.includes('存取太頻繁')) {
            container.innerHTML += `<div class="chart-error-note">註：由於 Yahoo API 對歷史 K 線圖的頻繁存取限制，我們暫時無法顯示完整歷史資料。註，盤中今日雖然無法畫出最後一根K棒，但它實際上在與主畫面的即時股價同步跳動中，並不影響您對指標突破的判斷。</div>`;
        }
        API.logClientError('ShowChart', e);
    }
}