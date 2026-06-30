// [MODIFIED] 原因: 徹底移除 Electron 的 require，改用純 Web 環境支援的 import 模式
import { APP_CONFIG } from './config.js';
import { state, dom } from './state.js';
import * as UI from './ui.js';
import * as Chart from './chart.js';

// ==========================================
// 🚀 初始化與 UI 綁定
// ==========================================
// 1. 讀取預設名稱與圖示
document.getElementById('app-title-text').innerText = APP_CONFIG.appName;
document.getElementById('app-logo-img').src = APP_CONFIG.logoPath;

/**
 * [NEW] 原本是 ipcRenderer.on('reset-ui')，
 * 現在定義為純 JavaScript 函數，供網頁內部邏輯調用。
 */
const resetUI = () => {
    state.isExpanded = false;
    state.isSearching = false;
    if (dom.panel) dom.panel.classList.remove('show');
    UI.closeAllModals();
    if (dom.sysMenu) dom.sysMenu.style.display = 'none';
};

// ==========================================
// 🖱️ 事件綁定區
// ==========================================

// 左側面板事件
document.getElementById('toggle-btn').addEventListener('click', UI.toggleMode);

document.getElementById('toggle-btn').addEventListener('contextmenu', (e) => {
    e.preventDefault();
    // [MODIFIED] 原因: 網頁版無法呼叫作業系統的 Dog Menu，改為顯示網頁版右鍵選單或忽略
    console.log("觸發右鍵選單 (網頁版不支援 OS 原生選單)");
});

document.getElementById('btn-close-panel').onclick = UI.toggleMode;
document.getElementById('pin-btn').onclick = UI.togglePin;
document.getElementById('btn-settings').onclick = UI.toggleInventoryPanel;
document.getElementById('btn-back').onclick = UI.toggleInventoryPanel;

// 啟動頁籤切換功能
UI.bindTabEvents();

// 系統選單與搜尋事件
document.getElementById('btn-sys-menu').onclick = (e) => {
    e.stopPropagation();
    const isShowing = dom.sysMenu.style.display === 'block';
    dom.sysMenu.style.display = isShowing ? 'none' : 'block';
    const ctxMenu = document.getElementById('context-menu');
    if (ctxMenu) ctxMenu.style.display = 'none';

    // [新增] 打開選單時自動填入存過的名鑰
    if (!isShowing) {
        const savedKey = localStorage.getItem('FUGLE_API_KEY') || '';
        const input = document.getElementById('setting-fugle-input');
        if (input) input.value = savedKey;
    }
};

document.getElementById('btn-settings-save').onclick = UI.saveSettings;

// 搜尋輸入事件 (Debounced 防止過度頻繁呼叫 Java API)
dom.stockInput.oninput = UI.handleStockInputDebounced;
document.getElementById('btn-search').onclick = () => {
    const query = dom.stockInput.value.trim();
    if (query) UI.searchAndRender(query, 'main');
};

// 庫存面板事件
document.getElementById('inv-symbol-search').oninput = (e) => UI.handleInvSearchDebounced(e.target.value);
document.getElementById('btn-inv-search').onclick = UI.searchInvStock;
document.getElementById('btn-add-inv').onclick = UI.addInventory;

if (dom.invSortSelect) {
    dom.invSortSelect.onchange = (e) => {
        state.invSortMode = e.target.value;
        UI.renderInventoryList(); // 直接重新渲染排序
    };
}

// 綁定「新增庫存」表單的展開收合
const btnToggleInvForm = document.getElementById('btn-toggle-inv-form');
if (btnToggleInvForm) btnToggleInvForm.onclick = UI.toggleInvForm;

// 系統匯入匯出 (現在會與 Java 後端互動)
document.getElementById('sys-add-curr').onclick = () => {
    if (dom.sysMenu) dom.sysMenu.style.display = 'none';
    const modal = document.getElementById('currency-modal');
    if (modal) modal.style.display = 'flex';
};
document.getElementById('sys-share').onclick = () => {
    if (dom.sysMenu) dom.sysMenu.style.display = 'none';
    UI.handleExportShare();
};
document.getElementById('sys-import').onclick = () => {
    if (dom.sysMenu) dom.sysMenu.style.display = 'none';
    document.getElementById('import-file').click();
};
document.getElementById('import-file').onchange = UI.handleFileImport;

// Modals 確認與取消按鈕
document.getElementById('btn-alert-ok').onclick = () => {
    const modal = document.getElementById('alert-modal');
    if (modal) modal.style.display = 'none';
};
document.getElementById('btn-confirm-cancel').onclick = UI.closeAllModals;
document.getElementById('btn-confirm-ok').onclick = UI.confirmAction;
document.getElementById('btn-rename-cancel').onclick = UI.closeAllModals;
document.getElementById('btn-rename-ok').onclick = UI.confirmRename;
document.getElementById('btn-curr-cancel').onclick = UI.closeAllModals;
document.getElementById('btn-curr-ok').onclick = UI.confirmAddCurrency;

// ==========================================
// 🖱️ 右鍵選單事件 (網頁自訂 HTML 選單)
// ==========================================
document.getElementById('ctx-hide').onclick = () => {
    // [MODIFIED] 原因: 網頁版無法隱藏至 Tray，將此按鈕功能改為單純收合面板
    UI.toggleMode();
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

// 當瀏覽器標籤失去焦點時的行為
window.addEventListener('blur', () => {
    const ctxMenu = document.getElementById('context-menu');
    if (ctxMenu) ctxMenu.style.display = 'none';

    // 檢查是否有 Modal 開著，如果有，則不進行自動收合
    const modals = ['chart-modal', 'fugle-modal', 'rename-modal', 'confirm-modal', 'alert-modal'];
    const isAnyModalOpen = modals.some(id => {
        const el = document.getElementById(id);
        return el && (el.style.display === 'flex' || el.style.display === 'block');
    });

    if (isAnyModalOpen) return;

    // [MODIFIED] 原因: 網頁版寬度判斷，若非固定模式則自動收合
    if (window.innerWidth > 100 && !state.isPinned) {
        // UI.toggleMode(); // 網頁版可視需求決定是否保留此行為
    }
});

console.log("🚀 Main JS 已完成網頁版遷移，目前運行於純網頁環境。");