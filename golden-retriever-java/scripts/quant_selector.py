import json
import sys
import io
import argparse
import pandas as pd
import numpy as np
import requests
from finlab import data
from finlab.ml import feature, label
from lightgbm import LGBMRegressor

# [FIX] 強制設定標準輸出編碼為 UTF-8
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--count', type=int, default=10)
    parser.add_argument('--capital', type=int, default=1000000)
    parser.add_argument('--inventory', type=str, default='')
    parser.add_argument('--mode', type=str, default='market', choices=['market', 'diagnose'])
    args = parser.parse_args()
    
    inventory_list = [s.replace('.TW', '').replace('.TWO', '').strip() for s in args.inventory.split(',') if s.strip()]

    try:
        # --- 1. 抓取 FinLab 基礎數據 ---
        roe = data.get('fundamental_features:ROE稅後')
        pe = data.get('price_earning_ratio:本益比')
        rev_yoy = data.get('monthly_revenue:去年同月增減(%)')
        margin = data.get('fundamental_features:營業利益率')
        foreign = data.get('institutional_investors_trading_summary:外陸資買賣超股數(不含外資自營商)')
        trust = data.get('institutional_investors_trading_summary:投信買賣超股數')
        try:
            large_holders = data.get('etl:inventory:大於一千張佔比')
        except:
            large_holders = data.get('etl:inventory:大於四百張佔比')
        close = data.get('price:收盤價')
        vol = data.get('price:成交股數')
        bias = (close / close.average(20) - 1) * 100

        # --- 2. AI 模型 (維持模型預測) ---
        features_dict = {
            'pe': pe, 'roe': roe, 'rev_growth': rev_yoy,
            'chip': (foreign + trust).rolling(3).sum(), 'bias': bias
        }
        X = feature.combine(features_dict, resample='ME', sample_filter=vol > 200000)
        y = label.excess_over_mean(X.index, resample='ME', period=1)
        mask = ~(X.isna().any(axis=1) | y.isna())
        X_clean, y_clean = X[mask], y[mask]
        model = LGBMRegressor(n_estimators=50, learning_rate=0.1, max_depth=5, random_state=42, verbose=-1)
        train_mask = X_clean.index.get_level_values('datetime') < '2024-01-01'
        model.fit(X_clean[train_mask], y_clean[train_mask])

        # --- 3. 提取評分 ---
        last_date = X.index.get_level_values('datetime').max()
        X_all = feature.combine(features_dict, resample='ME').xs(last_date, level='datetime').dropna()
        y_pred = model.predict(X_all)
        scores = pd.Series(y_pred, index=X_all.index)
        min_s, max_s = y_pred.min(), y_pred.max()

        def get_stock_details(symbol, is_market_recommend=False):
            if symbol not in roe.columns: return None
            raw_s = float(scores[symbol]) if symbol in scores.index else 0
            conf = round(float((raw_s - min_s) / (max_s - min_s + 1e-9) * 100), 1)
            
            large_val = large_holders[symbol].dropna()
            large_ratio = round(float(large_val.iloc[-1]), 2) if not large_val.empty else 0
            large_change = round(float(large_val.iloc[-1] - large_val.iloc[-2]), 2) if len(large_val) >= 2 else 0
            
            suggested_shares = 0
            if is_market_recommend:
                capital_per_stock = args.capital / args.count
                curr_price = float(close[symbol].iloc[-1])
                suggested_shares = int(capital_per_stock / curr_price) if curr_price > 0 else 0

            return {
                "symbol": f"{symbol}.TW",
                "roe": round(float(roe[symbol].iloc[-1]), 2),
                "pe": round(float(pe[symbol].iloc[-1]), 2),
                "rev_yoy": round(float(rev_yoy[symbol].iloc[-1]), 2),
                "margin": round(float(margin[symbol].iloc[-1]), 2),
                "bias": round(float(bias[symbol].iloc[-1]), 2),
                "foreign_buy": int(foreign[symbol].iloc[-1]),
                "trust_buy": int(trust[symbol].iloc[-1]),
                "large_holder_ratio": large_ratio,
                "large_holder_change": large_change,
                "ai_score": conf,
                "suggested_shares": suggested_shares
            }

        # --- 4. 模式輸出 ---
        if args.mode == 'market':
            cond_quality = roe > roe.quantile_row(0.7)
            cond_value = (pe < pe.quantile_row(0.3)) & (pe > 0)
            latest_cond = (cond_quality & cond_value).iloc[-1]
            valid_symbols = latest_cond[latest_cond].index.intersection(scores.index)
            top_symbols = scores[valid_symbols].sort_values(ascending=False).head(args.count).index
            print(json.dumps({"stocks": [get_stock_details(s, True) for s in top_symbols]}, ensure_ascii=False))
        else:
            diagnosis = {}
            for s in inventory_list:
                details = get_stock_details(s)
                if details: diagnosis[s] = details
            print(json.dumps({"diagnosis": diagnosis}, ensure_ascii=False))

    except Exception as e:
        print(json.dumps({"error": str(e)}, ensure_ascii=False))
        sys.exit(0)

if __name__ == "__main__":
    main()
