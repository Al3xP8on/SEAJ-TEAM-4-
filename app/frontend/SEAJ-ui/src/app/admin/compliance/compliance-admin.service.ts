import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';

export type ComplianceDecision = 'BLOCKED' | 'NEEDS_ACKNOWLEDGEMENT' | 'ALLOWED';
export interface ComplianceResult {
  rule: string;
  outcome: 'PASS' | 'WARN' | 'BLOCK';
  reason: string | null;
  acknowledged: boolean;
}
export interface ComplianceSubmission {
  checkGroupId: string;
  checkedAt: string;
  accountId: string;
  symbol: string;
  side: 'BUY' | 'SELL';
  quantity: number;
  price: number;
  decision: ComplianceDecision;
  orderId: string | null;
  results: ComplianceResult[];
}
export interface RestrictedInstrument {
  symbol: string;
  name: string;
  reason: string;
  addedBy: string;
  addedOn: string;
}
export type RiskProfile = 'CONSERVATIVE' | 'BALANCED' | 'AGGRESSIVE';
export interface AccountRiskProfile {
  id: number;
  accountId: string;
  holderName: string;
  username: string;
  status: 'ACTIVE' | 'SUSPENDED' | 'CLOSED';
  riskProfile: RiskProfile;
}
export interface SubmissionFilters {
  decision: ComplianceDecision | '';
  accountId: string;
  symbol: string;
}

/** Admin-only compliance API: the audit trail and the restricted list */
@Injectable({ providedIn: 'root' })
export class ComplianceAdminService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/admin/compliance';

  submissions(filters: SubmissionFilters, limit = 50) {
    let params = new HttpParams().set('limit', limit);
    if (filters.decision) params = params.set('decision', filters.decision);
    if (filters.accountId.trim()) params = params.set('accountId', filters.accountId.trim());
    if (filters.symbol.trim()) params = params.set('symbol', filters.symbol.trim());
    return this.http.get<ComplianceSubmission[]>(`${this.base}/checks`, { params });
  }

  restricted() {
    return this.http.get<RestrictedInstrument[]>(`${this.base}/restricted`);
  }

  restrict(symbol: string, reason: string) {
    return this.http.post<RestrictedInstrument>(`${this.base}/restricted`, {
      symbol: symbol.trim().toUpperCase(),
      reason: reason.trim(),
    });
  }

  riskProfiles() {
    return this.http.get<AccountRiskProfile[]>(`${this.base}/risk-profiles`);
  }

  setRiskProfile(id: number, riskProfile: RiskProfile) {
    return this.http.put<AccountRiskProfile>(`${this.base}/risk-profiles/${id}`, { riskProfile });
  }

  unrestrict(symbol: string) {
    return this.http.delete<void>(`${this.base}/restricted/${encodeURIComponent(symbol)}`);
  }
}
