const express = require('express');
const router = express.Router();
const StockController = require('../controllers/StockController');
const SystemController = require('../controllers/SystemController');

// router.get('/data', StockController.getDashboardData);
// 1. 儀表板 (拆分為三個獨立 API 提升載入速度)
router.get('/gold', StockController.getGoldData);
router.get('/rates', StockController.getRatesData);
router.get('/stocks', StockController.getStocksData);
// 2. 搜尋
router.get('/search', StockController.searchStock);

// [系統操作]
router.get('/export/share', SystemController.exportShareList);   // 分享清單
router.post('/import', SystemController.importData);             // 匯入

// 3. 庫存操作
router.post('/add-inventory', StockController.addInventory);
router.post('/remove-inventory', StockController.removeInventory);
router.post('/remove-inventory-group', StockController.removeInventoryGroup);

// 4. 股票清單
router.post('/add-stock', StockController.addStock);
router.post('/remove-stock', StockController.removeStock);
router.post('/rename-stock', StockController.renameStock);
router.post('/reorder-stocks', StockController.reorderStocks);

// 5. 匯率
router.post('/add-currency', StockController.addCurrency);
router.post('/remove-currency', StockController.removeCurrency);
router.post('/reorder-currencies', StockController.reorderCurrencies);

// 6. log
router.post('/log', StockController.logClientError);
// 7.K線圖路由
router.get('/chart', StockController.getChartData);

module.exports = router;