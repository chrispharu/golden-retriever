const axios = require('axios');
const logger = require('../utils/logger');
const config = require('../config/config');
class TwStockService {
    constructor() {
        this.quoteCache = {}; // 建立台股專屬快取
        setInterval(() => {
            const now = Date.now();
            for (const sym in this.quoteCache) {
                if (now - this.quoteCache[sym].timestamp > config.CACHE_MAX_AGE) {
                    delete this.quoteCache[sym];
                }
            }
        }, config.CACHE_CLEAN_INTERVAL);
    }
    getHeaders() {
        const agents = [
            'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
            'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36',
            'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/115.0'
        ];
        return {
            'User-Agent': agents[Math.floor(Math.random() * agents.length)],
            'Accept-Language': 'zh-TW,zh;q=0.9,en-US;q=0.8,en;q=0.7'
        };
    }

    async searchTwse(query) {
        try {
            const cleanQuery = query.replace(/\.TW[O]?$/i, '');
            const url = `https://mis.twse.com.tw/stock/api/getStockNames.jsp?n=${encodeURIComponent(cleanQuery)}&lang=zh_tw`;
            const { data } = await axios.get(url, { headers: this.getHeaders() });

            if (data && data.datas && Array.isArray(data.datas)) {
                return {
                    quotes: data.datas.map(item => {
                        const suffix = item.t === 'otc' ? '.TWO' : '.TW';
                        return {
                            symbol: `${item.c}${suffix}`,
                            shortname: item.n,
                            longname: item.n,
                            exchange: item.t === 'otc' ? 'TWO' : 'TW',
                            isYahooFinance: true
                        };
                    })
                };
            }
        } catch (e) { logger.warn(`[TwStock] TWSE 搜尋失敗: ${e.message}`); }
        return null;
    }

    async fetchTwseMonthChart(symbol) {
        try {
            const isOTC = symbol.includes('.TWO');
            const stockNo = symbol.split('.')[0];
            const date = new Date();
            const yyyy = date.getFullYear();
            const mm = String(date.getMonth() + 1).padStart(2, '0');
            const rocYear = yyyy - 1911;

            let url = isOTC
                ? `https://www.tpex.org.tw/web/stock/aftertrading/daily_trading_info/st43_result.php?l=zh-tw&d=${rocYear}/${mm}&stk_no=${stockNo}`
                : `https://www.twse.com.tw/exchangeReport/STOCK_DAY?response=json&date=${yyyy}${mm}01&stockNo=${stockNo}`;

            logger.info(`[TwStock] 抓取台股官方本月 K 線: ${stockNo}`);
            const { data } = await axios.get(url, { headers: this.getHeaders(), timeout: 10000 });

            let rawData = [];
            if (isOTC && data.aaData) rawData = data.aaData;
            else if (!isOTC && data.data) rawData = data.data;

            if (!rawData || rawData.length === 0) throw new Error('本月尚無交易數據或代碼錯誤');

            const formatted = rawData.map(row => {
                const parts = row[0].split('/');
                const gregorianYear = parseInt(parts[0]) + 1911;
                return {
                    time: `${gregorianYear}-${parts[1]}-${parts[2]}`,
                    open: parseFloat(row[3].replace(/,/g, '')),
                    high: parseFloat(row[4].replace(/,/g, '')),
                    low: parseFloat(row[5].replace(/,/g, '')),
                    close: parseFloat(row[6].replace(/,/g, ''))
                };
            }).filter(item => !isNaN(item.open) && !isNaN(item.close));

            return formatted;
        } catch (e) {
            logger.error(`[TwStock] 官方 K 線抓取失敗 (${symbol}): ${e.message}`);
            throw new Error('無法取得證交所資料，請稍後再試');
        }
    }

    async fetchFugleChart(symbol, apiKey) {
        try {
            const stockNo = symbol.split('.')[0];
            const start = new Date();
            start.setMonth(start.getMonth() - 6);
            const fromDate = start.toISOString().split('T')[0];
            const toDate = new Date().toISOString().split('T')[0];

            logger.info(`[Fugle] 抓取富果近半年 K 線: ${stockNo}`);
            const url = `https://api.fugle.tw/marketdata/v1.0/stock/historical/candles/1d?symbol=${stockNo}&from=${fromDate}&to=${toDate}`;
            const { data } = await axios.get(url, { headers: { 'X-API-KEY': apiKey }, timeout: 10000 });

            if (!data.data || data.data.length === 0) throw new Error('查無富果 K 線數據');

            const formatted = data.data.map(item => ({
                time: item.date,
                open: item.open,
                high: item.high,
                low: item.low,
                close: item.close
            }));

            formatted.sort((a, b) => new Date(a.time) - new Date(b.time));
            return formatted;
        } catch (e) {
            logger.error(`[Fugle] API 抓取失敗 (${symbol}): ${e.response?.data?.message || e.message}`);
            if (e.response?.status === 401) throw new Error('富果 API Key 錯誤或無效');
            throw new Error('無法取得富果資料，請確認 API 狀態');
        }
    }
    // 🚀 [NEW] 富果盤中即時報價引擎
    async fetchFugleQuotes(symbols, apiKey) {
        if (!symbols || symbols.length === 0 || !apiKey) return {};
        const results = {};
        const now = Date.now();
        const symbolsToFetch = [];

        const twDate = new Date(new Date().toLocaleString('en-US', { timeZone: 'Asia/Taipei' }));
        const day = twDate.getDay();
        const h = twDate.getHours();
        const m = twDate.getMinutes();
        const isTradingHours = (day >= 1 && day <= 5) && ((h === 9 && m >= 0) || (h >= 10 && h <= 12) || (h === 13 && m <= 30));

        // 如果是盤中，快取 15 秒 (省額度但保持流暢)
        // 如果已收盤或假日，快取 1 小時 (3600000毫秒，極致省額度！)
        const cacheTTL = isTradingHours ? config.CACHE_TRADING_OPEN : config.CACHE_TRADING_CLOSED;

        symbols.forEach(sym => {
            const cached = this.quoteCache[sym];
            // 使用智慧計算出來的 TTL 來防護
            if (cached && (now - cached.timestamp < cacheTTL)) {
                results[sym] = cached.data;
            } else {
                symbolsToFetch.push(sym);
            }
        });

        if (symbolsToFetch.length > 0) {
            // 富果 API 一次只能查一檔，所以我們用 Promise.all 並行抓取
            await Promise.allSettled(symbolsToFetch.map(async (sym) => {
                try {
                    const stockNo = sym.split('.')[0];
                    const url = `https://api.fugle.tw/marketdata/v1.0/stock/intraday/quote/${stockNo}`;
                    const { data } = await axios.get(url, { headers: { 'X-API-KEY': apiKey } });

                    // [修正] 富果 v1.0 回傳的 JSON 直接就在 data 裡面，不用往深層剝
                    if (data && data.symbol) {
                        const q = data;

                        // 將富果的欄位精準對應到我們的系統格式
                        const quoteData = {
                            price: q.lastPrice || q.closePrice || q.previousClose || 0,
                            prevClose: q.previousClose || 0,
                            high: q.highPrice || 0,
                            low: q.lowPrice || 0,
                            currency: 'TWD',
                            marketStatus: q.isClose ? 'CLOSED' : 'OPEN'
                        };
                        results[sym] = quoteData;
                        this.quoteCache[sym] = { data: quoteData, timestamp: now };
                    }
                } catch (e) {
                    logger.error(`[Fugle] 報價抓取失敗 (${sym}): ${e.message}`);
                }
            }));
        }
        return results;
    }
}

module.exports = TwStockService;