// Shapes match the Java API responses, so real data can replace the sample data without changes

/** GET /v1/accounts/{id} */
export interface Account {
  id: number;
  accountId: string;
  holderName: string;
  cashBalance: number;
  status: 'ACTIVE' | 'SUSPENDED' | 'CLOSED';
}

/** GET /v1/accounts/{id}/positions */
export interface Position {
  positionId: number;
  symbol: string;
  quantity: number;
  averageCost: number;
  status: 'OPEN' | 'PARTIALLY_CLOSED' | 'CLOSED' | 'LIQUIDATED';
}
