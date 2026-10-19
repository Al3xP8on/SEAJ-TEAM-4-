export type OrderStatus = 'NEW' | 'PENDING' | 'FILLED' | 'CANCELLED' | 'REJECTED';
export interface TradingAccount {
  id: number;
  accountId: string;
  name: string;
  cashBalance: number;
  status: 'ACTIVE' | 'SUSPENDED' | 'CLOSED';
}
export interface Instrument {
  symbol: string;
  name: string;
  assetClass: string;
  currency: string;
  tradable: boolean;
}
export interface Quote {
  symbol: string;
  bidPrice: number;
  askPrice: number;
  lastPrice: number;
  priceTimeStamp: string;
}
export interface MarketBar {
  timestamp: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}
export interface MarketSnapshot {
  symbol: string;
  price: number;
  timestamp: string;
  previousClose: number | null;
  changePercent: number | null;
  dailyBar: MarketBar | null;
  quote: Quote | null;
}
export interface DailyPrice {
  symbol: string;
  priceDate: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}
export interface MarketOverview {
  configured: boolean;
  feed: string;
  updatedAt: string | null;
  message: string;
  snapshots: MarketSnapshot[];
  unsupportedSymbols: string[];
}
export type MarketTimeframe = '1Min' | '6Hour' | '1Day';

export interface MarketChart {
  symbol: string;
  feed: string;
  timeframe: MarketTimeframe;
  bars: MarketBar[];
  message: string;
}
export interface Order {
  orderId: string;
  accountId: string;
  accountHolderName: string;
  ticker: string;
  instrumentName: string;
  side: 'BUY' | 'SELL';
  quantity: number;
  price: number;
  totalValue: number;
  status: OrderStatus;
  createdAt: string;
  orderType: 'MARKET' | 'LIMIT';
  executionPrice: number | null;
}
export interface OrderRequest {
  accountId: string;
  symbol: string;
  side: 'BUY' | 'SELL';
  quantity: number;
  orderType: 'MARKET' | 'LIMIT';
  price?: number;
  timeInForce: 'GTC';
  idempotencyKey: string;
}
export interface OrderHistory {
  orderId: string;
  status: OrderStatus;
  timestamp: string;
  reason: string | null;
}
