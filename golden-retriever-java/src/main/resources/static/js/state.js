
export const state = {
    isExpanded: false,
    isSearching: false,
    isPinned: false,
    data: { rates: {}, portfolio: [], inventory: [] },
    selectedInvSymbol: null,
    selectedInvName: null,
    ctxSymbol: null,
    pendingAction: null,
    invSortMode: 'default' ,// 記錄目前的排序模式
    currentTab: 'all',        // 目前選中的頁籤 (預設為 'all')
    cachedPortfolio: [] ,      // 暫存後端抓回來的最新股票陣列
    currentInvTab: 'all',
    cachedInventory: []
};

export const dom = {
    panel: document.getElementById('panel'),
    mainView: document.getElementById('main-view'),
    invPanel: document.getElementById('inventory-panel'),
    stockInput: document.getElementById('stock-input'),
    invList: document.getElementById('inv-list-area'),
    sysMenu: document.getElementById('system-menu'),
    invSortSelect: document.getElementById('inv-sort-select'), // 排序下拉選單
    modals: {
        alert: document.getElementById('alert-modal'),
        confirm: document.getElementById('confirm-modal'),
        rename: document.getElementById('rename-modal'),
        currency: document.getElementById('currency-modal'),
        fugle: document.getElementById('fugle-modal')
    }
};