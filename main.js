const { app, BrowserWindow, ipcMain, screen, globalShortcut, shell, Tray, Menu } = require('electron');
const path = require('path');
const { fork } = require('child_process');
const http = require('http'); // [NEW] 用於偵測伺服器狀態

process.env.IS_ELECTRON = 'true';
// app.disableHardwareAcceleration();
// app.commandLine.appendSwitch('wm-window-animations-disabled');

let win;
let serverProcess;
let tray = null;
let isQuitting = false;

function createWindow() {
    const primaryDisplay = screen.getPrimaryDisplay();
    const { width, height } = primaryDisplay.workAreaSize;

    win = new BrowserWindow({
        width: 100,
        height: 100,
        x: width - 120, // 右下角
        y: height - 120,
        frame: false,
        transparent: true,
        alwaysOnTop: true,
        resizable: false,
        skipTaskbar: true,
        icon: path.join(__dirname, 'public', 'content', 'DSC05068.jpg'),
        webPreferences: {
            nodeIntegration: true,
            contextIsolation: false
        }
    });
    // 攔截關閉
    win.on('close', (event) => {
        if (!isQuitting) {
            event.preventDefault();
            win.hide();
        }
    });

    // [FIX] 啟動流程優化：靜默等待伺服器就緒
    // 不再讓視窗反覆載入，而是主程序確認 Server OK 後才載入一次
    checkServerAndLoad('http://localhost:3000');
    win.webContents.on('before-input-event', (event, input) => {
        // 當按下 F12 時，切換獨立的開發者工具
        if (input.key === 'F12' && input.type === 'keyDown') {
            if (win.webContents.isDevToolsOpened()) {
                win.webContents.closeDevTools();
            } else {
                win.webContents.openDevTools({ mode: 'detach' });
            }
        }
    });

}

// [NEW] 伺服器輪詢函式
function checkServerAndLoad(url) {
    const check = () => {
        const req = http.get(url, (res) => {
            if (res.statusCode === 200) {
                // 伺服器活著，讓視窗載入
                if (win) win.loadURL(url);
            } else {
                // 雖然連上但狀態不對，重試
                setTimeout(check, 1000);
            }
        });

        req.on('error', (err) => {
            // 連線失敗 (ECONNREFUSED)，代表還沒啟動，1秒後重試
            // 這裡不會觸發 Yahoo API，因為只是 Ping localhost
            setTimeout(check, 1000);
        });

        req.end();
    };
    check();
}

function startServer() {
    const scriptPath = path.join(__dirname, 'web.js');
    serverProcess = fork(scriptPath, [], {
        stdio: 'inherit',
        env: { ...process.env, IS_ELECTRON: true }
    });
}

function createTray() {
    const iconPath = path.join(__dirname, 'public', 'content', 'DSC05068.jpg');
    tray = new Tray(iconPath);
    tray.setToolTip('狗狗金控-毛毛小幫手');

    const contextMenu = Menu.buildFromTemplate([
        { label: '顯示狗狗', click: () => win.show() },
        { label: '隱藏狗狗', click: () => win.hide() },
        { type: 'separator' },
        {
            label: '結束程式',
            click: () => {
                isQuitting = true;
                if (serverProcess) serverProcess.kill();
                app.quit();
            }
        }
    ]);
    tray.setContextMenu(contextMenu);
    tray.on('click', () => {
        if (win.isVisible()) win.hide();
        else win.show();
    });
}

app.whenReady().then(() => {
    startServer();
    createWindow();
    createTray();

    globalShortcut.register('Ctrl+Alt+H', () => {
        if (win) {
            win.show();
            win.focus();
            win.webContents.send('reset-ui');
        }
    });
});

app.on('before-quit', () => {
    isQuitting = true;
    if (serverProcess) serverProcess.kill();
});

ipcMain.on('resize-window', (event, arg) => {
    const primaryDisplay = screen.getPrimaryDisplay();
    const { width, height } = primaryDisplay.workAreaSize;

    const targetW = Math.round(arg.width);
    const targetH = Math.round(arg.height);
    const newX = Math.round(width - targetW - 20);
    const newY = Math.round(height - targetH - 20);

    if (win) {
        // 1. 直接改變實體大小
        win.setBounds({ x: newX, y: newY, width: targetW, height: targetH });

        // 2. [黑魔法] 強制 Windows DWM 重繪！
        // 瞬間將透明度設為 0.99，這對肉眼幾乎無感，但能逼迫作業系統清除殘影緩衝區
        win.setOpacity(0.99);

        // 50 毫秒後再悄悄恢復為 1
        setTimeout(() => {
            if (win) win.setOpacity(1);
        }, 50);
    }
});

ipcMain.on('open-url', (event, url) => shell.openExternal(url));

ipcMain.on('close-app', () => {
    if (serverProcess) serverProcess.kill();
    app.exit(0);
});

ipcMain.on('hide-to-tray', () => {
    if (win) win.hide();
});

ipcMain.on('open-dog-menu', () => {
    const template = [
        {
            label: '📉 縮小',
            click: () => { if (win) win.webContents.send('reset-ui'); }
        },
        { type: 'separator' },
        {
            label: '❌ 關閉',
            click: () => {
                isQuitting = true;
                if (serverProcess) serverProcess.kill();
                app.quit();
            }
        }
    ];
    const menu = Menu.buildFromTemplate(template);
    menu.popup({ window: win });
});

app.on('will-quit', () => {
    globalShortcut.unregisterAll();
    if (serverProcess) serverProcess.kill();
});

app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') app.quit();
});
