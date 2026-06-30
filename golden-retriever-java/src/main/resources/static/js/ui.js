// const { ipcRenderer } = require('electron');
import { state, dom } from './state.js';
import * as API from './api.js';
import { APP_CONFIG } from './config.js';

let searchTimeout = null;

// export function toggleMode() {
//     state.isExpanded = !state.isExpanded;
//     if (state.isExpanded) {
//         ipcRenderer.send('resize-window', APP_CONFIG.window.expanded);
//         setTimeout(() => {
//             dom.panel.classList.add('show');
//             if (!state.isSearching) loadData();
//             dom.stockInput.focus();Y
//         }, 100);
//     } else {
//         dom.panel.classList.remove('show');
//         state.isSearching = false;
//         dom.stockInput.value = '';
//         dom.invPanel.style.display = 'none';
//         closeAllModals();
//         setTimeout(() => ipcRenderer.send('resize-window', APP_CONFIG.window.collapsed), 50);
//     }
// }
// 處理展開與收合的切換
export function toggleMode() {
    if (!dom.panel) return;
    
    const isShowing = dom.panel.classList.contains('show');
    if (isShowing) {
        dom.panel.classList.remove('show');
        state.isSearching = false;
        dom.stockInput.value = '';
        dom.invPanel.style.display = 'none';
        closeAllModals();
    } else {
        dom.panel.classList.add('show');
        loadData();
        dom.stockInput.focus();
    }
}
export function togglePin() {
    state.isPinned = !state.isPinned;
    const btn = document.getElementById('pin-btn');
    if (state.isPinned) btn.classList.add('active'); else btn.classList.remove('active');
}

export function toggleInventoryPanel() {
    const isShowing = dom.invPanel.style.display === 'flex';
    if (isShowing) {
        dom.invPanel.style.display = 'none';
        loadData();
    } else {
        dom.invPanel.style.display = 'flex';
        document.getElementById('inv-symbol-search').value = '';
        document.getElementById('inv-symbol-locked').style.display = 'none';
        document.getElementById('inv-search-results').style.display = 'none';
        state.selectedInvSymbol = null;
        state.selectedInvName = null;

        document.getElementById('inv-price').value = '';
        document.getElementById('inv-shares').value = '';
        document.getElementById('inv-rate').value = '';
        document.getElementById('inv-date').value = '';

        if (dom.invSortSelect) dom.invSortSelect.value = 'default';
        state.invSortMode = 'default';

        loadInventoryList();
    }
}

export function loadData() {
    if (state.isSearching) return;

    document.getElementById('gold-area').innerHTML = `
        <div class="gold-title">🪙 黃金存摺</div>
        <div class="gold-info"><div class="text-muted-sm">載入中...</div></div>`;
    document.getElementById('rate-area').innerHTML = `<div class="status-msg sm">匯率載入中...</div>`;
    document.getElementById('data-area').innerHTML = `<div class="status-msg">股票載入中...</div>`;

    API.fetchGold()
        .then(data => renderGold(data.gold))
        .catch(e => {
            document.getElementById('gold-area').innerHTML = `<div class="status-msg sm error">❌ 黃金載入失敗: ${e.message}</div>`;
            API.logClientError('Dashboard-FetchGold', e);
        });

    API.fetchRates()
        .then(data => {
            state.data.rates = data.rates;
            renderRates(data.rates, data.currencyOrder);
        })
        .catch(e => {
            document.getElementById('rate-area').innerHTML = `<div class="status-msg sm error">❌ 匯率載入失敗: ${e.message}</div>`;
            API.logClientError('Dashboard-FetchRates', e);
        });

    API.fetchStocks()
        .then(data => {
            state.data.portfolio = data.portfolio;
            state.data.inventory = data.inventory;
            state.lastUpdatedAt = data.updatedAt; // 紀錄更新時間供頁籤切換使用

            // [新增] 不再直接呼叫 renderStocks，而是交給「過濾中繼站」處理
            renderPortfolioList();
        })
        .catch(e => {
            document.getElementById('data-area').innerHTML = `<div class="status-msg error">❌ 股票載入失敗<br><span class="text-muted-sm">${e.message}</span></div>`;
            API.logClientError('Dashboard-FetchStocks', e);
        });
}

function renderGold(gold) {
    if (!gold) return;
    const goldArea = document.getElementById('gold-area');

    if (!gold.current || gold.current === 0) {
        goldArea.innerHTML = `
            <div class="gold-title">🪙 黃金存摺</div>
            <div class="gold-info">
                <div class="gold-error-price">⚠️ 報價異常</div>
                <div class="gold-error-high">暫無資料</div>
            </div>`;
        return;
    }

    const diff = (gold.current || 0) - (gold.prevClose || 0);
    const goldClass = diff > 0 ? 'trend-up' : (diff < 0 ? 'trend-down' : '');
    const isFallbackClass = gold.isFallback ? 'fallback' : '';
    const titleText = gold.isFallback ? '🪙 國際黃金(期貨換算估值)' : '🪙 黃金存摺';

    goldArea.innerHTML = `
        <div class="gold-title ${isFallbackClass}">${titleText}</div>
        <div class="gold-info">
            <div class="gold-info-right">
                <span class="gold-price ${goldClass}">$${(gold.current || 0).toLocaleString()}</span>
            </div>
            <div class="gold-high">Max: ${(gold.high || 0).toLocaleString()}</div>
        </div>`;
}

function renderRates(rates, order = []) {
    const rateArea = document.getElementById('rate-area');
    let rateTags = '';
    const displayOrder = order.length > 0 ? order : Object.keys(rates || {});
    displayOrder.forEach(key => {
        const r = rates[key] || {};
        const val = r.price || 0;
        const prev = r.prevClose || 0;

        let colorClass = '';
        if (val > prev) colorClass = 'text-danger-bold';
        else if (val < prev) colorClass = 'text-success-bold';

        rateTags += `
        <div class="rate-tag draggable" draggable="true" data-type="currency" data-id="${key}">
            <span class="${colorClass}">${key}: ${val.toFixed(2)}</span>
            <span class="rate-del" onclick="askDelete('currency', '${key}')">×</span>
        </div>`;
    });
    // [MODIFIED] 將新增按鈕移到最前方，增加易用性
    rateArea.innerHTML = `<button class="rate-add-btn" id="btn-add-curr" title="新增幣別">+</button>` + rateTags;
    document.getElementById('btn-add-curr').onclick = () => dom.modals.currency.style.display = 'flex';
    enableDragAndDrop('rate-area');
}

// ==========================================
// 🚀 [新增] 依據目前頁籤，過濾並渲染股票清單
// ==========================================
export function renderPortfolioList() {
    const listArea = document.getElementById('data-area');
    if (!listArea) return;

    // 1. 取得剛剛暫存在 state 裡的完整資料
    let displayList = state.data.portfolio || [];
    const currentTab = state.currentTab || 'all'; // 預設為全部

    // 2. 🧠 依據頁籤進行智慧過濾
    if (currentTab === 'tw') {
        displayList = displayList.filter(s => s.symbol.includes('.TW') || s.symbol.includes('.TWO'));
    } else if (currentTab === 'us') {
        displayList = displayList.filter(s => !s.symbol.includes('.TW') && !s.symbol.includes('.TWO'));
    }

    // 3. 渲染沒有資料的狀態
    if (displayList.length === 0) {
        listArea.innerHTML = `<div class="status-msg">此分類尚無股票</div>`;
        return;
    }

    // 4. 將過濾後的資料，交給原本的渲染器印出 HTML
    renderStocks(displayList, state.lastUpdatedAt);
}

// 原本的渲染邏輯 (完全保留您的版本，只負責印出 HTML)
function renderStocks(portfolio, updatedAt) {
    const displayArea = document.getElementById('data-area');
    let stockHtml = '';
    (portfolio || []).forEach(s => {
        const diff = (s.price || 0) - (s.prevClose || 0);
        let pClass = 'trend-flat';
        if (s.price > 0 && s.prevClose > 0) {
            if (diff > 0.0001) pClass = 'trend-up';
            else if (diff < -0.0001) pClass = 'trend-down';
        }
        const isTW = s.symbol.includes('TW');

        const statusDot = s.marketStatus === 'OPEN'
            ? '<span class="market-dot open" title="盤中"></span>'
            : '<span class="market-dot closed" title="已收盤"></span>';

        let postMarketHtml = '';
        if (s.marketStatus === 'CLOSED' && s.postMarketPrice) {
            postMarketHtml = `<div class="post-market-text">盤後: $${s.postMarketPrice.toLocaleString()}</div>`;
        }

        let costHtml = '';
        if (!isTW) {
            if (s.rate > 0) {
                costHtml = `<div class="cost" style="font-size:11px;color:#34495e;margin-top:2px;">NT$ ${(s.costTWD || 0).toLocaleString()} <span style="color:#999; font-size:10px;">(Ex:${s.rate.toFixed(2)})</span></div>`;
            } else {
                costHtml = `<div class="cost text-danger-bold" style="font-size:10px; margin-top:2px;">⚠️匯率異常</div>`;
            }
        }

        stockHtml += `
        <div class="row draggable" draggable="true" data-type="stock" data-symbol="${s.symbol}" data-id="${s.symbol}">
            <div class="stock-col-left">
                <div class="font-bold">${statusDot}${s.name}</div>
                <div class="text-muted-sm">${s.symbol}</div>
            </div>
            <div class="pointer-none" style="text-align: right;">
                <div class="price ${pClass}">$${(s.price || 0).toLocaleString()}</div>
                ${postMarketHtml}
                ${costHtml}
            </div>
        </div>`;
    });

    if (updatedAt) {
        stockHtml += `<div class="update-time">更新時間: ${updatedAt}</div>`;
    }

    displayArea.innerHTML = stockHtml;

    displayArea.querySelectorAll('.row').forEach(row => {
        const sym = row.dataset.symbol;
        const link = state.data.portfolio.find(s => s.symbol === sym)?.link;
        // row.onclick = () => ipcRenderer.send('open-url', link);
        row.onclick = () => { if (link) window.open(link, '_blank'); };
        row.oncontextmenu = (e) => {
            e.preventDefault();
            e.stopPropagation();
            state.ctxSymbol = sym;

            const menu = document.getElementById('context-menu');
            menu.style.display = 'block';

            let x = e.clientX;
            let y = e.clientY;

            const menuWidth = menu.offsetWidth;
            const menuHeight = menu.offsetHeight;
            const windowWidth = window.innerWidth;
            const windowHeight = window.innerHeight;

            if (y + menuHeight > windowHeight) y = windowHeight - menuHeight - 8;
            if (x + menuWidth > windowWidth) x = windowWidth - menuWidth - 8;

            const containerRect = document.getElementById('app-container').getBoundingClientRect();
            x -= containerRect.left;
            y -= containerRect.top;

            menu.style.left = x + 'px';
            menu.style.top = y + 'px';

            if (dom.sysMenu) dom.sysMenu.style.display = 'none';
        };
    });

    enableDragAndDrop('data-area');
}

export function enableDragAndDrop(containerId) {
    const container = document.getElementById(containerId);
    if (!container) return;

    const draggables = container.querySelectorAll('.draggable');
    draggables.forEach(draggable => {
        draggable.addEventListener('dragstart', () => draggable.classList.add('dragging'));
        draggable.addEventListener('dragend', async () => {
            draggable.classList.remove('dragging');
            document.querySelectorAll('.over').forEach(el => el.classList.remove('over'));

            const type = draggable.dataset.type;
            const newOrder = [];
            container.querySelectorAll(`.draggable[data-type="${type}"]`).forEach(el => {
                newOrder.push(el.dataset.id);
            });

            try {
                if (type === 'stock') await API.post('reorder-stocks', { newOrder });
                if (type === 'currency') await API.post('reorder-currencies', { newOrder });
            } catch (e) { console.error('排序失敗', e); }
        });
    });

    container.addEventListener('dragover', e => {
        e.preventDefault();
        const afterElement = getDragAfterElement(container, e.clientY);
        const draggable = document.querySelector('.dragging');
        if (draggable) {
            if (afterElement == null) {
                container.appendChild(draggable);
            } else {
                container.insertBefore(draggable, afterElement);
                afterElement.classList.add('over');
            }
        }
    });

    container.addEventListener('dragleave', e => {
        if (e.target.classList.contains('draggable')) {
            e.target.classList.remove('over');
        }
    });
}

function getDragAfterElement(container, y) {
    const draggableElements = [...container.querySelectorAll('.draggable:not(.dragging)')];
    return draggableElements.reduce((closest, child) => {
        const box = child.getBoundingClientRect();
        const offset = y - box.top - box.height / 2;
        if (offset < 0 && offset > closest.offset) {
            return { offset: offset, element: child };
        } else {
            return closest;
        }
    }, { offset: Number.NEGATIVE_INFINITY }).element;
}
export async function loadInventoryList() {
    const listArea = document.getElementById('inv-list-area');
    try {
        if (!listArea) return;
        listArea.innerHTML = '<div class="status-msg">載入中...</div>';

        // 1. 只負責跟後端要資料
        const stocksData = await API.fetchStocks();

        // 2. 存入快取大腦
        state.cachedInventory = stocksData.groupedInventory || [];

        // 3. 交給渲染器處理
        renderInventoryList();

    } catch (e) {
        console.error(e);
        if (listArea) listArea.innerHTML = `<div class="status-msg error">❌ 發生錯誤:<br><span class="text-muted-sm">${e.message}</span></div>`;
        API.logClientError('InventoryList-Load', e);
    }
}
export function renderInventoryList() {
    const listArea = document.getElementById('inv-list-area');
    if (!listArea) return;

    // 1. 從快取拿出完整資料
    let displayList = [...(state.cachedInventory || [])];
    const currentTab = state.currentInvTab || 'all';

    // 2. 🧠 頁籤智慧過濾
    if (currentTab === 'tw') {
        displayList = displayList.filter(g => g.isTW); // 後端已經幫我們算好 isTW 了！
    } else if (currentTab === 'us') {
        displayList = displayList.filter(g => !g.isTW);
    }

    if (displayList.length === 0) {
        listArea.innerHTML = '<div class="status-msg">尚無符合的庫存紀錄</div>';
        return;
    }

    // 3. 排序邏輯 (只針對過濾後的資料排序)
    // if (state.invSortMode === 'roi_desc') displayList.sort((a, b) => b.roi - a.roi);
    // else if (state.invSortMode === 'roi_asc') displayList.sort((a, b) => a.roi - b.roi);
    // else if (state.invSortMode === 'val_desc') displayList.sort((a, b) => b.marketValueTWD - a.marketValueTWD);
    // else if (state.invSortMode === 'sym_asc') displayList.sort((a, b) => a.symbol.localeCompare(b.symbol));

    // 4. 無情的 HTML 印表機
    let html = '';
    displayList.forEach(g => {
        const statusDot = g.marketStatus === 'OPEN'
            ? '<span class="market-dot open" title="盤中"></span>'
            : '<span class="market-dot closed" title="已收盤"></span>';

        let displayMarketValue = '---';
        let displayPlStr = '---';
        let displayRoiStr = '---';
        let plClass = '';

        if (g.hasValidRate) {
            const profitLossTWD = g.marketValueTWD - g.totalCostTWD;
            plClass = profitLossTWD >= 0 ? 'pos' : 'neg';
            displayPlStr = profitLossTWD >= 0 ? `+${Math.round(profitLossTWD).toLocaleString()}` : Math.round(profitLossTWD).toLocaleString();
            displayRoiStr = isNaN(g.roi) ? '---%' : (g.roi >= 0 ? `+${g.roi.toFixed(2)}%` : `${g.roi.toFixed(2)}%`);
            displayMarketValue = isNaN(g.marketValueTWD) ? '---' : Math.round(g.marketValueTWD).toLocaleString();
        } else {
            displayMarketValue = '⚠️匯率異常';
            plClass = 'neg';
        }

        let avgDisplay = '0';
        let avgLabel = g.isTW ? '均價(NT)' : '均價(US)';

        if (g.totalShares > 0) {
            if (g.isTW) {
                const avgTWD = g.totalCostTWD / g.totalShares;
                avgDisplay = Math.round(avgTWD).toLocaleString();
            } else {
                const avgOrg = g.totalCostOriginal / g.totalShares;
                avgDisplay = avgOrg.toFixed(2);
            }
        }

        let postMarketHtml = '';
        if (g.marketStatus === 'CLOSED' && g.postMarketPrice) {
            postMarketHtml = `<div class="post-market-text">盤後: $${g.postMarketPrice.toFixed(2)}</div>`;
        }

        html += `
            <div class="inv-card">
                <div class="inv-header">
                    <div>
                        <div class="inv-symbol inv-sym-lg">${statusDot}${g.name}</div> <div class="inv-sym-sm">${g.symbol}</div>
                    </div>
                    <div class="inv-header-right">
                        <div class="inv-pl ${plClass} inv-pl-lg">${displayPlStr}</div>
                        <div class="inv-pl ${plClass} inv-pl-sm">${displayRoiStr}</div>
                    </div>
                </div>
                <div class="inv-grid">
                    <div class="inv-cell"><span class="inv-label">${avgLabel}</span><span class="inv-val">${avgDisplay}</span></div>
                    <div class="inv-cell">
                        <span class="inv-label">現價(${g.isTW ? 'NT' : 'US'})</span>
                        <span class="inv-val">${g.currentPrice.toFixed(2)}${postMarketHtml}</span>
                    </div>
                    <div class="inv-cell"><span class="inv-label">庫存</span><span class="inv-val">${g.totalShares}</span></div>
                    <div class="inv-cell"><span class="inv-label">市值(NT)</span><span class="inv-val ${!g.hasValidRate ? 'error' : ''}">${displayMarketValue}</span></div>
                </div>
                <div class="inv-actions">
                    <div class="inv-detail-btn" onclick="toggleDetails(this)">明細 (${g.records.length}) ▼</div>
                    <div class="inv-bulk-del" onclick="askRemoveInventoryGroup('${g.symbol}')">🗑 刪除全部</div>
                </div>
                <div class="inv-logs">
                    ${g.records.map(r => `
                        <div class="inv-log-item">
                            <span>${r.date || '--'}</span>
                            <span>$${r.price} x ${r.shares} (匯:${r.usedRate || '⚠️'})</span>
                           <span class="inv-log-del" onclick="confirmInlineDelete(this, '${r.id}')">×</span>
                        </div>
                    `).join('')}
                </div>
            </div>
        `;
    });
    listArea.innerHTML = html;
}

export function handleStockInputDebounced() {
    const val = dom.stockInput.value.trim();
    if (!val) {
        state.isSearching = false;
        loadData();
        return;
    }
    if (searchTimeout) clearTimeout(searchTimeout);
    searchTimeout = setTimeout(() => {
        searchAndRender(val.toUpperCase(), 'main');
    }, APP_CONFIG.searchDebounceMs);
}

export function handleInvSearchDebounced(val) {
    if (!val.trim()) {
        document.getElementById('inv-search-results').style.display = 'none';
        return;
    }
    if (searchTimeout) clearTimeout(searchTimeout);
    searchTimeout = setTimeout(() => {
        searchAndRender(val.trim(), 'inv');
    }, APP_CONFIG.searchDebounceMs);
}

export async function searchAndRender(query, mode) {
    if (mode === 'main') state.isSearching = true;
    const targetDiv = mode === 'main' ? document.getElementById('data-area') : document.getElementById('inv-search-results');
    targetDiv.innerHTML = '<div class="status-msg sm">搜尋中...</div>';
    targetDiv.style.display = 'block';

    try {
        const data = await API.searchStock(query);
        if (data.results && data.results.length > 0) {
            let html = '';
            data.results.forEach(item => {
                if (mode === 'main') {
                    html += `<div class="search-result-item" data-sym="${item.symbol}"><b>${item.symbol}</b> ${item.name} <span class="exch-tag">${item.exch}</span></div>`;
                } else {
                    html += `<div class="inv-search-item" data-sym="${item.symbol}" data-name="${item.name}"><b>${item.symbol}</b> ${item.name}</div>`;
                }
            });
            targetDiv.innerHTML = html;

            if (mode === 'main') {
                targetDiv.querySelectorAll('.search-result-item').forEach(el => {
                    el.onclick = () => addStock(el.dataset.sym);
                });
            } else {
                targetDiv.querySelectorAll('.inv-search-item').forEach(el => {
                    el.onclick = () => selectInvStock(el.dataset.sym, el.dataset.name);
                });
            }
        } else {
            targetDiv.innerHTML = '<div class="status-msg sm">無結果</div>';
        }
    } catch (e) {
        targetDiv.innerHTML = `<div class="status-msg sm error">❌ 搜尋錯誤: ${e.message}</div>`;
        API.logClientError('SearchAndRender', e);
    }
}

export function searchInvStock() {
    const val = document.getElementById('inv-symbol-search').value.trim();
    if (val) searchAndRender(val, 'inv');
}

export function selectInvStock(sym, name) {
    state.selectedInvSymbol = sym;
    state.selectedInvName = name;

    const locked = document.getElementById('inv-symbol-locked');
    locked.value = `${sym} - ${name}`;
    locked.style.display = 'block';
    document.getElementById('inv-search-results').style.display = 'none';
    document.getElementById('inv-symbol-search').value = '';

    const rateInput = document.getElementById('inv-rate');
    if (sym.includes('TW')) {
        rateInput.value = 1;
        rateInput.disabled = true;
    } else {
        const rObj = state.data.rates['USD'];
        if (rObj && rObj.price > 0) {
            rateInput.value = rObj.price;
        } else {
            rateInput.value = '';
            rateInput.placeholder = '請手動輸入匯率';
        }
        rateInput.disabled = false;
    }
}

export async function addInventory() {
    const p = document.getElementById('inv-price').value;
    const s = document.getElementById('inv-shares').value;
    const r = document.getElementById('inv-rate').value;
    const d = document.getElementById('inv-date').value;

    if (!state.selectedInvSymbol) return showAlert('請先搜尋代碼');
    if (!state.selectedInvSymbol.includes('TW') && !r) return showAlert('非台股需輸入當時的換匯匯率');

    try {
        await API.post('add-inventory', {
            symbol: state.selectedInvSymbol,
            name: state.selectedInvName,
            price: p,
            shares: s,
            exchangeRate: r || 1,
            date: d
        });

        document.getElementById('inv-price').value = '';
        document.getElementById('inv-shares').value = '';
        document.getElementById('inv-rate').value = '';

        loadInventoryList();
        document.getElementById('inv-form-body').classList.add('hidden');
        document.getElementById('inv-form-toggle-icon').innerText = '▼';
        document.querySelector('.inv-form-title').innerText = '➕ 新增 (點擊展開)';
    } catch (e) {
        showAlert(`新增失敗: ${e.message}`);
        API.logClientError('AddInventory', e);
    }
}

export async function addStock(symbol) {
    try {
        await API.post('add-stock', { symbol });
        state.isSearching = false;
        dom.stockInput.value = '';
        loadData();
    } catch (e) {
        showAlert(`新增失敗: ${e.message}`);
        API.logClientError('AddStock', e);
    }
}

export async function handleExportShare() {
    try {
        const data = await API.exportShare();
        const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `Share_Targets_${new Date().toISOString().slice(0, 10)}.json`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        showAlert('分享檔已匯出');
    } catch (e) {
        showAlert('匯出失敗');
        API.logClientError('ExportShare', e);
    }
}

export async function handleFileImport(e) {
    const file = e.target.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = async (evt) => {
        try {
            const json = JSON.parse(evt.target.result);
            const res = await API.importData(json);
            showAlert(res.message);
            if (res.success) {
                loadData();
                loadInventoryList();
            }
        } catch (err) {
            showAlert('檔案格式錯誤或匯入失敗');
            API.logClientError('FileImport', err);
        }
        e.target.value = '';
    };
    reader.readAsText(file);
}

export function showAlert(msg) {
    document.getElementById('alert-text').innerText = msg;
    dom.modals.alert.style.display = 'flex';
}

export function closeAllModals() {
    Object.values(dom.modals).forEach(m => m.style.display = 'none');
    state.pendingAction = null;
}

export function askDelete(type, id) {
    state.pendingAction = { type, id };
    let msg = `移除 ${id}？`;
    if (type === 'inventory-group') msg = `刪除 ${id} 的所有庫存紀錄？`;
    if (type === 'inventory-item') msg = `刪除此筆交易紀錄？`;

    document.getElementById('modal-text').innerText = msg;
    dom.modals.confirm.style.display = 'flex';
}

export async function confirmAction() {
    const act = state.pendingAction;
    if (!act) return;

    try {
        if (act.type === 'stock') await API.post('remove-stock', { symbol: act.id });
        if (act.type === 'currency') await API.post('remove-currency', { currency: act.id });

        if (act.type === 'inventory-group') {
            await API.post('remove-inventory-group', { symbol: act.id });
            loadInventoryList();
        }
        if (act.type === 'inventory-item') {
            await API.post('remove-inventory', { id: act.id });
            loadInventoryList();
        }

        closeAllModals();
        loadData();
    } catch (e) {
        showAlert(`操作失敗: ${e.message}`);
        API.logClientError('ConfirmAction', e);
    }
}

export function showRenameModal() {
    document.getElementById('rename-input').value = '';
    dom.modals.rename.style.display = 'flex';
}

export async function confirmRename() {
    const newName = document.getElementById('rename-input').value.trim();
    if (newName && state.ctxSymbol) {
        try {
            await API.post('rename-stock', { symbol: state.ctxSymbol, newName });
        } catch (e) {
            showAlert(`重新命名失敗: ${e.message}`);
            API.logClientError('ConfirmRename', e);
        }
    }
    closeAllModals();
    loadData();
}

export async function confirmAddCurrency() {
    const c = document.getElementById('currency-select').value;
    try {
        await API.post('add-currency', { currency: c });
        closeAllModals();
        loadData();
    } catch (e) {
        showAlert(`新增幣別失敗: ${e.message}`);
        API.logClientError('ConfirmAddCurrency', e);
    }
}

// ==========================================
// 🖱️ 綁定所有頁籤點擊事件 (主畫面 + 庫存)
// ==========================================
export function bindTabEvents() {
    // 1. 綁定主畫面的頁籤
    const mainTabs = document.querySelectorAll('#portfolio-tabs .tab-item');
    if (mainTabs.length > 0) {
        mainTabs.forEach(tab => {
            tab.addEventListener('click', (e) => {
                mainTabs.forEach(t => t.classList.remove('active'));
                e.target.classList.add('active');
                state.currentTab = e.target.getAttribute('data-tab');
                renderPortfolioList();
            });
        });
    }

    // 2. 綁定庫存面板的頁籤
    const invTabs = document.querySelectorAll('#inv-tabs .tab-item');
    if (invTabs.length > 0) {
        invTabs.forEach(tab => {
            tab.addEventListener('click', (e) => {
                invTabs.forEach(t => t.classList.remove('active'));
                e.target.classList.add('active');
                state.currentInvTab = e.target.getAttribute('data-tab');
                renderInventoryList();
            });
        });
    }
}

window.toggleDetails = function (btn) {
    const card = btn.closest('.inv-card');
    const logs = card.querySelector('.inv-logs');
    if (!logs) return;

    const isCurrentlyOpen = logs.style.display === 'block';

    document.querySelectorAll('.inv-logs').forEach(el => el.style.display = 'none');
    document.querySelectorAll('.inv-detail-btn').forEach(el => el.innerText = el.innerText.replace('▲', '▼'));

    if (!isCurrentlyOpen) {
        logs.style.display = 'block';
        btn.innerText = btn.innerText.replace('▼', '▲');
    }
};

window.askRemoveInventoryGroup = function (symbol) { askDelete('inventory-group', symbol); };
window.askRemoveInventoryItem = function (id) { askDelete('inventory-item', id); };

window.confirmInlineDelete = async function (btn, id) {
    if (btn.innerText === '×') {
        btn.innerText = '確定刪除？';
        btn.classList.add('confirm-state');
        setTimeout(() => {
            if (btn && btn.innerText === '確定刪除？') {
                btn.innerText = '×';
                btn.classList.remove('confirm-state');
            }
        }, 3000);
    } else {
        btn.innerText = '刪除中...';
        try {
            await API.post('remove-inventory', { id });
            loadInventoryList();
        } catch (e) {
            showAlert(`刪除失敗: ${e.message}`);
            API.logClientError('InlineDelete', e);
        }
    }
};
export async function saveSettings() {
    // 只保留富果金鑰的讀取
    const newFugleKey = document.getElementById('setting-fugle-input').value.trim();

    // 1. 存到本地瀏覽器 (LocalStorage) - 供前端立即使用
    if (newFugleKey) {
        localStorage.setItem('FUGLE_API_KEY', newFugleKey);
    } else {
        localStorage.removeItem('FUGLE_API_KEY');
    }

    // 2. 存到後端資料庫 (Database) - 讓後端也能作為備援使用
    try {
        await API.post('save-config', {
            key: 'FUGLE_API_KEY',
            value: newFugleKey,
            desc: '富果 API 金鑰'
        });
    } catch (e) {
        console.error('後端金鑰儲存失敗', e);
    }

    const sysMenu = document.getElementById('system-menu');
    if (sysMenu) sysMenu.style.display = 'none';

    loadData();
    showAlert('設定已成功儲存！');
}
// ==========================================
// 🖱️ 庫存面板：切換新增表單的展開與收合
// ==========================================
export function toggleInvForm() {
    const body = document.getElementById('inv-form-body');
    const icon = document.getElementById('inv-form-toggle-icon');
    const title = document.querySelector('.inv-form-title');

    if (!body || !icon) return;

    // 判斷目前是否為隱藏狀態
    if (body.classList.contains('hidden')) {
        body.classList.remove('hidden'); // 展開
        icon.innerText = '▲';
        if (title) title.innerText = '➖ 隱藏';
    } else {
        body.classList.add('hidden');    // 收合
        icon.innerText = '▼';
        if (title) title.innerText = '➕ 新增 ';
    }
}