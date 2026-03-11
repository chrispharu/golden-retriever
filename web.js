console.log('>>> [DEBUG] web.js 開始執行');
const express = require('express');
const cors = require('cors');
const path = require('path');
const rateLimit = require('express-rate-limit');
const logger = require('./src/utils/logger');
const config = require('./src/config/config');
const apiRoutes = require('./src/routes/api');

const app = express();

// ==========================================
// Middleware
// ==========================================
app.use(cors({
    origin: process.env.ALLOWED_ORIGINS?.split(',') || '*',
    credentials: true
}));

const apiLimiter = rateLimit({
    windowMs: 1 * 60 * 1000,
    max: 60,
    message: { error: '請求過於頻繁' },
    standardHeaders: true,
    legacyHeaders: false,
});
app.use('/api/', apiLimiter);

app.use(express.json());

// Log Request
app.use((req, res, next) => {
    logger.info(`[HTTP] ${req.method} ${req.url} from ${req.ip}`);
    next();
});

// ==========================================
// Routes
// ==========================================
console.log('>>> [DEBUG] 準備啟動 Server');
// 1. API 路由
app.use('/api', apiRoutes);

// 2. 前端靜態檔案 (Angular/HTML)
// 假設你將 Angular Build 出來的檔案放在 public 資料夾
app.use(express.static(path.join(__dirname, 'public')));

// 3. SPA 路由重導向 (讓 Angular 路由生效)
app.get(/(.*)/, (req, res) => {
    // 如果不是 API 請求，就回傳 index.html
    if (!req.path.startsWith('/api')) {
        const indexPath = path.join(__dirname, 'public', 'index.html');
        // 檢查檔案是否存在，避免無限迴圈
        const fs = require('fs');
        if (fs.existsSync(indexPath)) {
            res.sendFile(indexPath);
        } else {
            res.status(404).send('Frontend not build yet. Please check public/ folder.');
        }
    }
});

// ==========================================
// Error Handling
// ==========================================
app.use((err, req, res, next) => {
    logger.error(`[Global Error] ${err.message}\nStack: ${err.stack}`);
    res.status(500).json({
        error: '系統發生錯誤',
        message: process.env.NODE_ENV === 'development' ? err.message : '請聯繫管理員'
    });
});

app.listen(config.PORT, '0.0.0.0', () => {
    logger.info(`[Server] Running on port ${config.PORT} (MVC Mode)`);
});