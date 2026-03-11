module.exports = {
    PORT: process.env.PORT || 3000,

    // --- 動態快取時間設定 (單一真相來源 Single Source of Truth) ---
    CACHE_TRADING_OPEN: 15 * 1000,           // 盤中快取：15 秒 (完美平衡流暢度與富果API額度)
    CACHE_TRADING_CLOSED: 12 * 60 * 60 * 1000, // 盤後與假日快取：12 小時 (極致省額度)
    CACHE_RATES_GOLD: 60 * 1000,             // 匯率與黃金快取：60 秒
    CACHE_CHART: 60 * 60 * 1000,             // [新增] K線圖快取：1 小時

    // --- 記憶體清理設定 ---
    CACHE_CLEAN_INTERVAL: 60 * 60 * 1000,    // 記憶體清理頻率：1 小時
    CACHE_MAX_AGE: 24 * 60 * 60 * 1000       // 快取最大存活時間：24 小時
};