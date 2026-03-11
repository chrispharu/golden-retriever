const { ipcRenderer } = require('electron');
import { APP_CONFIG } from './config.js';
import { state, dom } from './state.js';
import * as UI from './ui.js';
import * as Chart from './chart.js';

// ==========================================
// 🚀 初始化與 UI 綁定
// ==========================================
// 1. 讀取預設名稱
document.getElementById('app-title-text').innerText = APP_CONFIG.appName;


document.getElementById('app-logo-img').src = APP_CONFIG.logoPath;

ipcRenderer.on('reset-ui', () => {
    state.isExpanded = false;
    state.isSearching = false;
    dom.panel.classList.remove('show');
    UI.closeAllModals();
    if (dom.sysMenu) dom.sysMenu.style.display = 'none';
});

// 左側面板事件
document.getElementById('toggle-btn').addEventListener('click', UI.toggleMode);
document.getElementById('toggle-btn').addEventListener('contextmenu', (e) => {
    e.preventDefault();
    ipcRenderer.send('open-dog-menu');
});
document.getElementById('btn-close-panel').onclick = UI.toggleMode;
document.getElementById('pin-btn').onclick = UI.togglePin;
document.getElementById('btn-settings').onclick = UI.toggleInventoryPanel;
document.getElementById('btn-back').onclick = UI.toggleInventoryPanel;
// 啟動頁籤切換功能！
UI.bindTabEvents();
// 系統選單與搜尋事件
document.getElementById('btn-sys-menu').onclick = (e) => {
    e.stopPropagation();
    const isShowing = dom.sysMenu.style.display === 'block';
    dom.sysMenu.style.display = isShowing ? 'none' : 'block';
    document.getElementById('context-menu').style.display = 'none';
};

dom.stockInput.oninput = UI.handleStockInputDebounced;
document.getElementById('btn-search').onclick = () => UI.searchAndRender(dom.stockInput.value.trim(), 'main');

// 庫存面板事件
document.getElementById('inv-symbol-search').oninput = (e) => UI.handleInvSearchDebounced(e.target.value);
document.getElementById('btn-inv-search').onclick = UI.searchInvStock;
document.getElementById('btn-add-inv').onclick = UI.addInventory;
if (dom.invSortSelect) {
    dom.invSortSelect.onchange = (e) => {
        state.invSortMode = e.target.value;
        UI.renderInventoryList(); // 直接重新渲染排序，不用再發 API 請求！
    };
}
// 綁定「新增庫存」表單的展開收合按鈕
const btnToggleInvForm = document.getElementById('btn-toggle-inv-form');
if (btnToggleInvForm) btnToggleInvForm.onclick = UI.toggleInvForm;
// 系統匯入匯出
document.getElementById('sys-share').onclick = () => { dom.sysMenu.style.display = 'none'; UI.handleExportShare(); };
document.getElementById('sys-import').onclick = () => { dom.sysMenu.style.display = 'none'; document.getElementById('import-file').click(); };
document.getElementById('import-file').onchange = UI.handleFileImport;

// Modals 確認與取消按鈕
document.getElementById('btn-alert-ok').onclick = () => dom.modals.alert.style.display = 'none';
document.getElementById('btn-confirm-cancel').onclick = UI.closeAllModals;
document.getElementById('btn-confirm-ok').onclick = UI.confirmAction;
document.getElementById('btn-rename-cancel').onclick = UI.closeAllModals;
document.getElementById('btn-rename-ok').onclick = UI.confirmRename;
document.getElementById('btn-curr-cancel').onclick = UI.closeAllModals;
document.getElementById('btn-curr-ok').onclick = UI.confirmAddCurrency;

// 右鍵選單事件
document.getElementById('ctx-hide').onclick = () => {
    ipcRenderer.send('hide-to-tray');
    document.getElementById('context-menu').style.display = 'none';
};
document.getElementById('ctx-rename').onclick = UI.showRenameModal;
document.getElementById('ctx-delete').onclick = () => UI.askDelete('stock', state.ctxSymbol);
document.getElementById('ctx-chart').onclick = Chart.showChartModal;

// K 線圖控制面板
document.getElementById('btn-chart-twse').onclick = () => { Chart.setCurrentChartSource('twse'); Chart.loadAndRenderChart(); };
document.getElementById('btn-chart-fugle').onclick = () => { Chart.setCurrentChartSource('fugle'); Chart.loadAndRenderChart(); };
document.getElementById('btn-chart-close').onclick = () => {
    document.getElementById('chart-modal').style.display = 'none';
    Chart.clearLiveUpdateInterval();
};

// ==========================================
// 🖱️ 全域點擊與失去焦點 (自動收合邏輯)
// ==========================================
document.addEventListener('click', (e) => {
    const ctxMenu = document.getElementById('context-menu');
    if (ctxMenu && !e.target.closest('#context-menu')) {
        ctxMenu.style.display = 'none';
    }
    if (dom.sysMenu) dom.sysMenu.style.display = 'none';
});

window.addEventListener('blur', () => {
    const ctxMenu = document.getElementById('context-menu');
    if (ctxMenu) ctxMenu.style.display = 'none';

    const modals = ['chart-modal', 'fugle-modal', 'rename-modal', 'confirm-modal', 'alert-modal'];
    const isAnyModalOpen = modals.some(id => {
        const el = document.getElementById(id);
        return el && (el.style.display === 'flex' || el.style.display === 'block');
    });

    if (isAnyModalOpen) return;

    if (window.innerWidth > 100) {
        UI.toggleMode();
    }
});
