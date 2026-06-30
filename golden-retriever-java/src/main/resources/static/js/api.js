const API_URL = '/api';

export const fetchGold = () => fetch(`${API_URL}/gold`).then(r => r.json());
export const fetchRates = () => fetch(`${API_URL}/rates`).then(r => r.json());

// --- 取得主要股票與庫存資料 ---
export async function fetchStocks() {
    // 每次抓取股票時，從本地端取出富果 API Key
    const fugleKey = localStorage.getItem('FUGLE_API_KEY') || '';

    // 透過 Headers 把 Key 偷渡給後端
    const res = await fetch(`${API_URL}/stocks`, {
        headers: {
            'X-Fugle-Key': fugleKey
        }
    });

    if (!res.ok) throw new Error('獲取股票資料失敗');
    return await res.json();
}

// --- [新增] 解決剛剛的報錯：搜尋股票 ---
export async function searchStock(query) {
    const res = await fetch(`${API_URL}/search?q=${encodeURIComponent(query)}`);
    if (!res.ok) throw new Error('搜尋股票失敗');
    return await res.json();
}

// --- [新增] 獲取 K 線圖資料 (支援富果金鑰傳遞) ---
export async function fetchChartData(symbol, source) {
    const fugleKey = localStorage.getItem('FUGLE_API_KEY') || '';
    let url = `${API_URL}/chart?symbol=${encodeURIComponent(symbol)}&source=${source}`;
    if (fugleKey) url += `&fugleKey=${fugleKey}`;

    const res = await fetch(url);
    const data = await res.json();
    if (!data.success) throw new Error(data.error || '獲取 K 線資料失敗');
    return data.data; // 直接回傳陣列
}

// ==========================================
// 🚀 通用 POST 工具與其他功能
// ==========================================
export async function post(endpoint, body) {
    const res = await fetch(`${API_URL}/${endpoint}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    });
    return res.json();
}

export async function importData(jsonContent) {
    return post('import', jsonContent);
}

export async function exportShare() {
    const res = await fetch(`${API_URL}/export/share`);
    return res.json();
}

// --- 傳送前端錯誤日誌給後端 ---
export async function logClientError(context, error) {
    try {
        const errorData = {
            context: context,
            message: error.message || String(error),
            stack: error.stack || ''
        };
        await post('log', errorData);
    } catch (e) {
        // 如果連傳送 log 都失敗，就只留在前端 console (避免無限迴圈)
        console.error('Failed to send log to server:', e);
    }
}