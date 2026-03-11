const DataService = require('../database/DataService');
const logger = require('../utils/logger');

const YahooService = require('../services/YahooService');
const TwStockService = require('../services/TwStockService');
const GoldService = require('../services/GoldService');

const db = new DataService();
const yahoo = new YahooService();
const twStock = new TwStockService(); // 台灣專武
const goldApi = new GoldService();    // 黃金與爬蟲專武
const StockController = {
    getGoldData: async (req, res, next) => {
        try {
            const gold = await goldApi.fetchGold();
            res.json({ gold });
        } catch (e) { next(e); }
    },

    getRatesData: async (req, res, next) => {
        try {
            const userData = db.getData();
            const currencies = userData.currencies || ['USD'];
            const rates = await yahoo.fetchExchangeRates(currencies);
            res.json({ rates, currencyOrder: currencies });
        } catch (e) { next(e); }
    },

    getStocksData: async (req, res, next) => {
        try {
            const userData = db.getData();
            const stocks = userData.stocks || [];
            const inventory = userData.inventory || [];
            const currencies = userData.currencies || ['USD'];

            const uniqueSymbols = new Set([
                ...stocks.map(s => s.symbol),
                ...inventory.map(i => i.symbol)
            ]);
            const symbols = Array.from(uniqueSymbols);

            // --- 雙引擎智能分流 (保留稍早的富果完美邏輯) ---
            const fugleKey = req.headers['x-fugle-key'] || '';
            const twSymbols = symbols.filter(s => s.includes('.TW') || s.includes('.TWO'));
            const usSymbols = symbols.filter(s => !s.includes('.TW') && !s.includes('.TWO'));

            let twQuotesPromise = Promise.resolve({});
            if (twSymbols.length > 0) {
                if (fugleKey) twQuotesPromise = twStock.fetchFugleQuotes(twSymbols, fugleKey);
                else { usSymbols.push(...twSymbols); twSymbols.length = 0; }
            }

            const [ratesResult, usStockResult, twStockResult] = await Promise.allSettled([
                yahoo.fetchExchangeRates(currencies),
                yahoo.fetchQuotes(usSymbols),
                twQuotesPromise
            ]);

            const ratesMap = ratesResult.status === 'fulfilled' ? ratesResult.value : {};
            const stockData = {
                ...(usStockResult.status === 'fulfilled' ? usStockResult.value : {}),
                ...(twStockResult.status === 'fulfilled' ? twStockResult.value : {})
            };

            // ==========================================
            // 🚀 [重構核心] 庫存運算與 Grouping 全部在後端完成！
            // ==========================================
            const groupedInvMap = {};

            inventory.forEach(inv => {
                if (!inv.symbol) return;
                const isTW = inv.symbol.includes('TW');

                // 解析名稱
                let resolvedName = inv.name;
                if (!resolvedName) {
                    const watchlistData = stocks.find(s => s.symbol === inv.symbol);
                    resolvedName = (watchlistData && watchlistData.name) ? watchlistData.name : inv.symbol;
                }

                // 建立群組
                if (!groupedInvMap[inv.symbol]) {
                    groupedInvMap[inv.symbol] = {
                        symbol: inv.symbol,
                        name: resolvedName,
                        isTW: isTW,
                        totalShares: 0,
                        totalCostTWD: 0,
                        totalCostOriginal: 0,
                        records: []
                    };
                }

                // 處理匯率與成本
                const p = parseFloat(inv.price) || 0;
                const s = parseFloat(inv.shares) || 0;
                let r = parseFloat(inv.exchangeRate);

                if (!r || r <= 0) {
                    if (isTW) r = 1;
                    else {
                        const rateObj = ratesMap['USD'];
                        r = (rateObj && rateObj.price) ? rateObj.price : 0;
                    }
                }

                if (p > 0 && s > 0) {
                    groupedInvMap[inv.symbol].totalShares += s;
                    groupedInvMap[inv.symbol].totalCostTWD += (p * s * r);
                    groupedInvMap[inv.symbol].totalCostOriginal += (p * s);
                }
                groupedInvMap[inv.symbol].records.push({ ...inv, usedRate: r });
            });

            // 計算最終的 ROI 與市值
            const groupedInventory = Object.values(groupedInvMap).map(g => {
                const mkt = stockData[g.symbol] || { price: 0, prevClose: 0, high: 0, low: 0, marketStatus: 'CLOSED' };
                let currentRate = 1;
                if (!g.isTW) {
                    const rateObj = ratesMap['USD'];
                    currentRate = (rateObj && rateObj.price) ? rateObj.price : 0;
                }

                const currentPrice = mkt.price || 0;
                let marketValueTWD = 0, roi = 0;
                const hasValidRate = currentRate > 0 || g.isTW;

                if (hasValidRate) {
                    marketValueTWD = currentPrice * g.totalShares * currentRate;
                    const profitLossTWD = marketValueTWD - g.totalCostTWD;
                    roi = g.totalCostTWD > 0 ? (profitLossTWD / g.totalCostTWD) * 100 : 0;
                } else {
                    marketValueTWD = -Infinity;
                    roi = -Infinity;
                }

                return {
                    ...g,
                    currentPrice,
                    marketStatus: mkt.marketStatus,
                    postMarketPrice: mkt.postMarketPrice || null,
                    currentRate,
                    marketValueTWD,
                    roi,
                    hasValidRate
                };
            });

            // 處理左側觀察清單
            const portfolio = stocks.map(s => {
                const mkt = stockData[s.symbol] || { price: 0, prevClose: 0, high: 0, low: 0 };
                const rateObj = ratesMap[s.currency];
                const ratePrice = (rateObj && rateObj.price) ? rateObj.price : 0;

                let avgCost = 0, avgCostTWD = 0;
                const gStats = groupedInvMap[s.symbol];
                if (gStats && gStats.totalShares > 0) {
                    avgCost = gStats.totalCostOriginal / gStats.totalShares;
                    avgCostTWD = gStats.totalCostTWD / gStats.totalShares;
                }

                return {
                    ...s,
                    price: mkt.price, prevClose: mkt.prevClose, high: mkt.high || 0, low: mkt.low || 0,
                    marketStatus: mkt.marketStatus || 'CLOSED',
                    postMarketPrice: mkt.postMarketPrice || null,
                    rate: ratePrice,
                    costTWD: Math.round(mkt.price * ratePrice),
                    avgCost, avgCostTWD,
                    link: s.symbol.includes('TW') ? `https://tw.stock.yahoo.com/quote/${s.symbol}` : `https://finance.yahoo.com/quote/${s.symbol}`
                };
            });

            res.json({
                updatedAt: new Date().toLocaleString('zh-TW', { hour12: false }),
                portfolio,
                groupedInventory // 🎉 將已經算好的完美庫存陣列丟給前端！
            });
        } catch (e) { next(e); }
    },
    // ---  K 線圖 API ---
    // --- 提供 K 線圖 API (支援 A+B 雙引擎分流) ---
    getChartData: async (req, res, next) => {
        try {
            const { symbol, source, fugleKey } = req.query;
            let data = [];
            const isTW = symbol.includes('.TW') || symbol.includes('.TWO');

            if (isTW && source === 'twse') {
                // 引擎 A: 台灣官方 (本月)
                data = await twStock.fetchTwseMonthChart(symbol);
            } else if (isTW && source === 'fugle') {
                // 引擎 B: 富果 API (近半年)
                if (!fugleKey) throw new Error('尚未設定富果 API Key');
                data = await twStock.fetchFugleChart(symbol, fugleKey);
            } else {
                // 預設引擎: Yahoo (美股專用)
                data = await yahoo.fetchChartData(symbol);
            }

            // [修改] 直接回傳 JSON，把錯誤交給 catch 處理
            res.json({ success: true, data });
        } catch (e) {
            // 這裡不再丟給 next(e)，而是直接回傳安全包裝的錯誤給前端
            res.status(400).json({ success: false, error: e.message });
        }
    },
    _modifyData: (modifier) => { const data = db.getData(); modifier(data); db.save(data); },
    addInventory: (req, res, next) => { try { const { symbol, name, price, shares, date, exchangeRate } = req.body; StockController._modifyData(data => { if (!data.inventory) data.inventory = []; data.inventory.push({ id: Date.now().toString(), symbol, name: name || symbol, price: parseFloat(price) || 0, shares: parseFloat(shares) || 0, exchangeRate: parseFloat(exchangeRate) || 1, date: date || '' }); }); logger.info(`[Inventory] Add: ${symbol} (${name})`); res.json({ success: true }); } catch (e) { next(e); } },
    removeInventory: (req, res, next) => { try { StockController._modifyData(data => { data.inventory = data.inventory.filter(i => i.id !== req.body.id); }); res.json({ success: true }); } catch (e) { next(e); } },
    removeInventoryGroup: (req, res, next) => { try { StockController._modifyData(data => { data.inventory = data.inventory.filter(i => i.symbol !== req.body.symbol); }); res.json({ success: true }); } catch (e) { next(e); } },
    addStock: async (req, res, next) => { try { const { symbol } = req.body; const currentData = db.getData(); if (currentData.stocks.some(s => s.symbol === symbol)) return res.json({ success: false }); const currency = symbol.includes('TW') ? 'TWD' : 'USD'; let name = symbol; try { const results = await yahoo.search(symbol); if (results.quotes && results.quotes.length > 0) { name = results.quotes[0].shortname || results.quotes[0].longname || symbol; } } catch (e) { } StockController._modifyData(data => { data.stocks.push({ symbol, name, currency }); }); logger.info(`[Stock] Add: ${symbol} (${name})`); res.json({ success: true }); } catch (e) { next(e); } },
    removeStock: (req, res, next) => { try { StockController._modifyData(data => { data.stocks = data.stocks.filter(s => s.symbol !== req.body.symbol); }); res.json({ success: true }); } catch (e) { next(e); } },
    renameStock: (req, res, next) => { try { StockController._modifyData(data => { const s = data.stocks.find(x => x.symbol === req.body.symbol); if (s) s.name = req.body.newName; }); res.json({ success: true }); } catch (e) { next(e); } },
    reorderStocks: (req, res, next) => { try { const { newOrder } = req.body; StockController._modifyData(data => { const nextStocks = []; newOrder.forEach(sym => { const found = data.stocks.find(s => s.symbol === sym); if (found) nextStocks.push(found); }); data.stocks.forEach(s => { if (!nextStocks.find(x => x.symbol === s.symbol)) nextStocks.push(s); }); data.stocks = nextStocks; }); res.json({ success: true }); } catch (e) { next(e); } },
    searchStock: async (req, res, next) => { try { const result = await yahoo.search(req.query.q); const data = result.quotes ? result.quotes.filter(q => q.isYahooFinance !== false).map(q => ({ symbol: q.symbol, name: q.shortname || q.longname || q.symbol, exch: q.exchange })) : []; res.json({ results: data }); } catch (e) { next(e); } },
    addCurrency: (req, res, next) => { try { const c = req.body.currency.toUpperCase(); StockController._modifyData(data => { if (!data.currencies.includes(c)) data.currencies.push(c); }); res.json({ success: true }); } catch (e) { next(e); } },
    removeCurrency: (req, res, next) => { try { StockController._modifyData(data => { data.currencies = data.currencies.filter(c => c !== req.body.currency); }); res.json({ success: true }); } catch (e) { next(e); } },
    reorderCurrencies: (req, res, next) => { try { StockController._modifyData(data => { data.currencies = req.body.newOrder; }); res.json({ success: true }); } catch (e) { next(e); } },

    // --- [NEW] 接收並記錄前端回傳的錯誤 ---
    logClientError: (req, res) => {
        const { context, message, stack } = req.body;
        logger.error(`[Frontend Error] 發生位置: ${context} | 訊息: ${message}\n堆疊: ${stack}`);
        res.json({ success: true });
    }
};

module.exports = StockController;