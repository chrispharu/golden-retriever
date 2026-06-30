const { app, BrowserWindow } = require('electron');
const path = require('path');

function createWindow() {
  const win = new BrowserWindow({
    width: 1400,
    height: 900,
    webPreferences: {
      nodeIntegration: true,
      contextIsolation: false,
      devTools: true
    }
  });

  // 如果是在開發模式下，載入 Angular 本地 Live Server；否則載入 dist 中的 index.html
  // Angular 21 的預設輸出路徑通常為 dist/golden-retriever-web/browser/index.html
  const isDev = process.argv.includes('--dev') || process.env.NODE_ENV === 'development';
  
  if (isDev) {
    win.loadURL('http://localhost:4200');
    win.webContents.openDevTools();
  } else {
    const indexPath = path.join(__dirname, 'dist/golden-retriever-web/browser/index.html');
    win.loadFile(indexPath).catch(err => {
      console.error('Failed to load local HTML file:', err);
    });
  }
}

app.whenReady().then(() => {
  createWindow();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow();
    }
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});
