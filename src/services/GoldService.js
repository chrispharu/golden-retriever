const axios = require('axios');
const cheerio = require('cheerio');
const YahooFinance = require('yahoo-finance2').default;
const yahooFinance = new YahooFinance(); // 備援用的 Yahoo API
const logger = require('../utils/logger');
const config = require('../config/config');

class GoldService {
    constructor() {
        this.goldCache = { data: {}, timestamp: 0 };
    }

    getHeaders() {
        return {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36',
            'Accept-Language': 'zh-TW,zh;q=0.9,en-US;q=0.8,en;q=0.7'
        };
    }

    async fetchGold() {
        const now = Date.now();
        if (now - this.goldCache.timestamp < config.CACHE_RATES_GOLD && this.goldCache.data.current) {
            return this.goldCache.data;
        }

        let current = 0, high = 0, trendDiff = 0;
        let isFallback = false;

        const twDate = new Date(new Date().toLocaleString('en-US', { timeZone: 'Asia/Taipei' }));
        const yyyy = twDate.getFullYear();
        const mm = String(twDate.getMonth() + 1).padStart(2, '0');
        const dd = String(twDate.getDate()).padStart(2, '0');
        const todayStrTw = `${yyyy}${mm}${dd}`;
        const ymTw = `${yyyy}${mm}`;

        let chartTodayPrice = 0, chartPrevPrice = 0;

        // --- 防護網 1：圖表 ---
        try {
            const { data: chartData } = await axios.get('https://rate.bot.com.tw/gold/chart/year/TWD', { headers: this.getHeaders(), timeout: 10000 });
            if (chartData && chartData.nodes && chartData.nodes.length > 0) {
                const prices = chartData.nodes.filter(n => n.x && n.x.replace(/\D/g, '').startsWith(ymTw)).map(n => parseFloat(n.y));
                if (prices.length > 0) high = Math.max(...prices);

                for (let i = chartData.nodes.length - 1; i >= 0; i--) {
                    const n = chartData.nodes[i];
                    if (n.x && n.x.replace(/\D/g, '').startsWith(todayStrTw)) {
                        chartTodayPrice = parseFloat(n.y);
                        if (i > 0) chartPrevPrice = parseFloat(chartData.nodes[i - 1].y);
                        break;
                    }
                }
            }
        } catch (e) { logger.warn(`[GoldService] 台銀歷史圖表異常: ${e.message}`); }

        // --- 防護網 2：首頁現價 ---
        try {
            const { data: currentData } = await axios.get('https://rate.bot.com.tw/gold?Lang=zh-TW', { headers: this.getHeaders(), timeout: 10000 });
            const $ = cheerio.load(currentData);
            current = parseFloat($('td.text-right').eq(1).text().trim()) || 0;
        } catch (e) { logger.warn(`[GoldService] 台銀首頁異常: ${e.message}`); }

        // --- 備援邏輯 ---
        if (current === 0 && chartTodayPrice > 0) {
            current = chartTodayPrice;
            trendDiff = current - chartPrevPrice;
        }

        if (current === 0) {
            try {
                const quotes = await yahooFinance.quote(['GC=F', 'USDTWD=X'], {}, { fetchOptions: { headers: this.getHeaders() } });
                const goldQuote = quotes.find(q => q.symbol === 'GC=F');
                const rateQuote = quotes.find(q => q.symbol === 'USDTWD=X');

                if (goldQuote && rateQuote) {
                    const goldTwdPerGram = (goldQuote.regularMarketPrice * rateQuote.regularMarketPrice) / 31.1034768;
                    current = Math.round(goldTwdPerGram);
                    isFallback = true;
                    const prevGoldTwd = ((goldQuote.regularMarketPreviousClose || goldQuote.regularMarketPrice) * (rateQuote.regularMarketPreviousClose || rateQuote.regularMarketPrice)) / 31.1034768;
                    trendDiff = current - Math.round(prevGoldTwd);
                }
            } catch (e) { logger.error(`[GoldService] 黃金 Yahoo 備援也失敗: ${e.message}`); }
        }

        if (current > high) high = current;
        const result = { current, prevClose: current - trendDiff, high, isFallback };

        if (current > 0) {
            this.goldCache = { data: result, timestamp: now };
        }
        return result;
    }
}

module.exports = GoldService;