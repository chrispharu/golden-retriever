const winston = require('winston');
const path = require('path');
const fs = require('fs');

// 確保 logs 資料夾存在
const logDir = path.join(process.cwd(), 'logs');
if (!fs.existsSync(logDir)) {
    fs.mkdirSync(logDir);
}

// 定義日誌格式 (時間戳 + 訊息)
const logFormat = winston.format.combine(
    winston.format.timestamp({ format: 'YYYY-MM-DD HH:mm:ss' }),
    winston.format.printf(info => `[${info.timestamp}] ${info.level.toUpperCase()}: ${info.message}`)
);

const logger = winston.createLogger({
    level: 'info',
    format: logFormat,
    transports: [
        // 1. 嚴重錯誤：寫入 logs/error.log (持久化保存，這對除錯最重要)
        new winston.transports.File({
            filename: path.join(logDir, 'error.log'),
            level: 'error',
            maxsize: 5242880, // 5MB 自動切割
            maxFiles: 5,
        }),
        // 2. 所有操作：寫入 logs/combined.log (包含 info, warn, error)
        new winston.transports.File({
            filename: path.join(logDir, 'combined.log'),
            maxsize: 5242880,
            maxFiles: 5,
        }),
        // 3. 開發時同時顯示在終端機，帶有顏色
        new winston.transports.Console({
            format: winston.format.combine(
                winston.format.colorize(),
                logFormat
            )
        })
    ]
});

// 攔截未捕獲的異常 (防止程式靜默崩潰)
logger.exceptions.handle(
    new winston.transports.File({ filename: path.join(logDir, 'exceptions.log') })
);

module.exports = logger;