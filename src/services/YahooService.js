const YahooFinance = require('yahoo-finance2').default;
const yahooFinance = new YahooFinance({ suppressNotices: ['yahooSurvey'] });
const logger = require('../utils/logger');
const config = require('../config/config');

// 讓 Yahoo 服務可以直接呼叫台股搜尋，保持 Controller 介面不變
const TwStockService = require('./TwStockService');
const twStock = new TwStockService();

class YahooService {
    constructor() {
        this.quoteCache = {};
        this.ratesCache = { data: {}, timestamp: 0 };
        this.chartCache = {}; // K 線專屬快取

        setInterval(() => {
            const now = Date.now();
            for (const sym in this.quoteCache) {
                if (now - this.quoteCache[sym].timestamp > config.CACHE_MAX_AGE) delete this.quoteCache[sym];
            }
            for (const sym in this.chartCache) {
                if (now - this.chartCache[sym].timestamp > config.CACHE_MAX_AGE) delete this.chartCache[sym];
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

    getMarketStatus(symbol) {
        const twDate = new Date(new Date().toLocaleString('en-US', { timeZone: 'Asia/Taipei' }));
        const day = twDate.getDay();
        const h = twDate.getHours();
        const m = twDate.getMinutes();
        const isTW = symbol.includes('.TW') || symbol.includes('.TWO');

        if (isTW) {
            const isTradingHours = (h === 9 && m >= 0) || (h >= 10 && h <= 12) || (h === 13 && m <= 30);
            return (day >= 1 && day <= 5 && isTradingHours) ? 'OPEN' : 'CLOSED';
        } else {
            const isNightTrading = (h === 21 && m >= 30) || (h >= 22 && h <= 23);
            const isMorningTrading = (h >= 0 && h < 5);
            let isTradingDay = false;
            if (isNightTrading && (day >= 1 && day <= 5)) isTradingDay = true;
            if (isMorningTrading && (day >= 2 && day <= 6)) isTradingDay = true;
            return isTradingDay ? 'OPEN' : 'CLOSED';
        }
    }

    async fetchExchangeRates(targetCurrencies = []) {
        const now = Date.now();
        if (now - this.ratesCache.timestamp < config.CACHE_RATES_GOLD && Object.keys(this.ratesCache.data).length > 0) {
            return this.ratesCache.data;
        }

        const curList = (targetCurrencies.length > 0) ? targetCurrencies : ['USD'];
        const symbols = curList.map(c => `${c}TWD=X`);
        const results = {};

        try {
            const quotes = await yahooFinance.quote(symbols, {}, { fetchOptions: { headers: this.getHeaders() } });
            const list = Array.isArray(quotes) ? quotes : [quotes];
            list.forEach(q => {
                const code = q.symbol.substring(0, 3);
                results[code] = { price: q.regularMarketPrice || 0, prevClose: q.regularMarketPreviousClose || 0 };
            });
            this.ratesCache = { data: results, timestamp: now };
        } catch (e) {
            logger.error(`[YahooService] 匯率抓取失敗: ${e.message}`);
            return this.ratesCache.data;
        }
        return results;
    }

    async fetchQuotes(symbols) {
        if (!symbols || !symbols.length) return {};
        const results = {};
        const symbolsToFetch = [];
        const now = Date.now();

        symbols.forEach(sym => {
            const cached = this.quoteCache[sym];
            const status = this.getMarketStatus(sym);
            const cacheTTL = status === 'OPEN' ? config.CACHE_TRADING_OPEN : config.CACHE_TRADING_CLOSED;

            if (cached && (now - cached.timestamp < cacheTTL)) results[sym] = cached.data;
            else symbolsToFetch.push(sym);
        });

        if (symbolsToFetch.length > 0) {
            try {
                const quotes = await yahooFinance.quote(symbolsToFetch, {}, { fetchOptions: { headers: this.getHeaders() } });
                const list = Array.isArray(quotes) ? quotes : [quotes];

                list.forEach(q => {
                    const data = {
                        price: q.regularMarketPrice || 0,
                        prevClose: q.regularMarketPreviousClose || 0,
                        high: q.regularMarketDayHigh || q.dayHigh || 0,
                        low: q.regularMarketDayLow || q.dayLow || 0,
                        currency: q.currency,
                        postMarketPrice: q.postMarketPrice || q.preMarketPrice || null
                    };
                    results[q.symbol] = data;
                    this.quoteCache[q.symbol] = { data: data, timestamp: now };
                });
            } catch (e) { logger.error(`[YahooService] 股價抓取異常: ${e.message}`); }
        }
        Object.keys(results).forEach(sym => results[sym].marketStatus = this.getMarketStatus(sym));
        return results;
    }

    async fetchChartData(symbol) {
        const now = Date.now();
        if (this.chartCache[symbol] && (now - this.chartCache[symbol].timestamp < config.CACHE_CHART)) {
            return this.chartCache[symbol].data;
        }

        try {
            const end = new Date();
            const start = new Date();
            start.setMonth(start.getMonth() - 6);

            const result = await yahooFinance.chart(symbol, { period1: start, period2: end, interval: '1d' }, { fetchOptions: { headers: this.getHeaders() } });
            if (!result || !result.quotes || result.quotes.length === 0) throw new Error('查無歷史數據');

            const formattedData = result.quotes
                .filter(item => item.open !== null && item.close !== null)
                .map(item => ({
                    time: item.date.toISOString().split('T')[0],
                    open: item.open, high: item.high, low: item.low, close: item.close
                }));

            formattedData.sort((a, b) => new Date(a.time) - new Date(b.time));
            this.chartCache[symbol] = { data: formattedData, timestamp: now };
            return formattedData;
        } catch (e) {
            if (e.message.includes('invalid json') || e.message.includes('Too Many Requests')) throw new Error('存取太頻繁，請稍後再試 (Yahoo 防護中)');
            throw new Error('無法載入 K 線圖資料');
        }
    }

    async search(query) {
        if (!query) return { quotes: [] };
        const isTwStockQuery = /[\u4e00-\u9fa5]/.test(query) || /^\d+$/.test(query) || /^\d+\.TW[O]?$/i.test(query);

        if (isTwStockQuery) {
            // 優雅地借用剛拆分出去的台股專武，讓 Controller 的呼叫介面完全不變！
            const twResult = await twStock.searchTwse(query);
            if (twResult && twResult.quotes && twResult.quotes.length > 0) return twResult;
        }

        try {
            return await yahooFinance.search(query, { quotesCount: 5, newsCount: 0 }, { fetchOptions: { headers: this.getHeaders() } });
        } catch (e) { return { quotes: [] }; }
    }
}

module.exports = YahooService;