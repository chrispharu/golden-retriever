const DataService = require('../database/DataService');
const logger = require('../utils/logger');

// 注意：這裡我們需要確保使用同一個 DataService 實例，或 DataService 本身能處理同步
// 由於 DataService.js 每次 new 都會讀檔，我們這裡 new 一個新的也能運作，
// 但在 Controller 層級共用一個 db 實例會更好。不過為了簡化，這裡維持 new。
const db = new DataService();

const SystemController = {
    // 匯出：僅分享清單 (不含庫存)
    exportShareList: (req, res, next) => {
        try {
            const data = db.getData(); // 拿最新
            const shareData = {
                stocks: data.stocks,
                currencies: data.currencies,
                _meta: { type: 'SHARE', date: new Date().toISOString() }
            };

            res.json(shareData);
            logger.info('[System] Share list exported');
        } catch (e) { next(e); }
    },

    // 匯入：目前僅支援分享檔 (因為庫存是私人的)
    // 若未來要支援完整還原，邏輯可再加回來，但目前需求是精簡
    importData: (req, res, next) => {
        try {
            const uploadData = req.body;
            if (!uploadData || typeof uploadData !== 'object') {
                throw new Error('無效的資料格式');
            }

            // 重新從硬碟載入一次，確保基底是最新的
            const currentData = db.loadFromDisk();

            // 合併邏輯：只合併股票清單
            const newStocks = uploadData.stocks || [];
            const newCurrencies = uploadData.currencies || [];
            let addedCount = 0;

            newStocks.forEach(newS => {
                const exists = currentData.stocks.some(s => s.symbol === newS.symbol);
                if (!exists) {
                    currentData.stocks.push(newS);
                    addedCount++;
                }
            });

            newCurrencies.forEach(c => {
                if (!currentData.currencies.includes(c)) currentData.currencies.push(c);
            });

            // 儲存合併後的結果
            db.save(currentData);

            logger.info(`[System] Imported ${addedCount} new stocks.`);
            res.json({ success: true, message: `已匯入 ${addedCount} 支新標的`, type: 'SHARE' });

        } catch (e) { next(e); }
    }
};

module.exports = SystemController;