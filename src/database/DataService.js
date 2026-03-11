const fs = require('fs');
const path = require('path');
const logger = require('../utils/logger');

class DataService {
    constructor() {
        const basePath = process.cwd();
        this.targetsFile = path.join(basePath, 'targets.json');
        this.assetsFile = path.join(basePath, 'assets.json');

        // 快取 (Memory Cache)
        this.cache = { stocks: [], currencies: [], inventory: [] };

        // 預設值
        this.defaultStocks = [
            { symbol: 'QQQ', currency: 'USD', name: 'Invesco QQQ' },
            { symbol: '2330.TW', currency: 'TWD', name: '台積電' }
        ];
        this.defaultCurrencies = ['USD', 'JPY'];

        // 初始化載入
        this.loadFromDisk();
    }

    // [Core] 從硬碟讀取並更新快取
    loadFromDisk() {
        let stocks = this.defaultStocks;
        let currencies = this.defaultCurrencies;
        let inventory = [];

        try {
            // 1. 讀取標的 (Targets)
            if (fs.existsSync(this.targetsFile)) {
                const data = JSON.parse(fs.readFileSync(this.targetsFile, 'utf8'));
                stocks = data.stocks || stocks;
                currencies = data.currencies || currencies;
            } else {
                // 若檔案不存在，初始化它
                this.atomicWrite(this.targetsFile, { stocks, currencies });
            }

            // 2. 讀取資產 (Assets)
            if (fs.existsSync(this.assetsFile)) {
                const data = JSON.parse(fs.readFileSync(this.assetsFile, 'utf8'));
                inventory = data.inventory || [];
                // 數字轉型
                inventory = inventory.map(item => ({
                    ...item,
                    shares: parseFloat(item.shares) || 1,
                    exchangeRate: parseFloat(item.exchangeRate) || (item.symbol.includes('TW') ? 1 : 0)
                }));
            } else {
                this.atomicWrite(this.assetsFile, { inventory: [] });
            }
        } catch (e) {
            logger.error(`[DataService] Load Error: ${e.message}`);
        }

        // 更新記憶體快取
        this.cache = { stocks, currencies, inventory };
        return this.cache;
    }

    // 取得資料 (直接拿快取，效能最高)
    getData() {
        return this.cache;
    }

    // 寫入資料 (接收完整物件 -> 拆分 -> 寫入硬碟 -> 更新快取)
    save(fullData) {
        try {
            const targetsData = {
                stocks: fullData.stocks || [],
                currencies: fullData.currencies || []
            };
            const assetsData = {
                inventory: fullData.inventory || []
            };

            this.atomicWrite(this.targetsFile, targetsData);
            this.atomicWrite(this.assetsFile, assetsData);

            // [關鍵] 同步更新記憶體快取，解決不用重開的問題
            this.cache = fullData;

        } catch (e) {
            logger.error(`[DataService] Save Error: ${e.message}`);
            throw e;
        }
    }

    atomicWrite(filePath, data) {
        const tempPath = filePath + '.tmp';
        const backupPath = filePath + '.bak';
        fs.writeFileSync(tempPath, JSON.stringify(data, null, 2));
        if (fs.existsSync(filePath)) fs.copyFileSync(filePath, backupPath);
        fs.renameSync(tempPath, filePath);
    }
}

module.exports = DataService;