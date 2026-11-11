import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Account, AuditReport, CreateSplitRequest, Movement, PendingShare, SettlementPlan, Split
} from './models';

const API = 'http://localhost:8080/api';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);

  private withKey(key: string) {
    return { headers: new HttpHeaders({ 'Idempotency-Key': key }) };
  }

  accounts(): Observable<Account[]> { return this.http.get<Account[]>(`${API}/accounts`); }
  account(id: number): Observable<Account> { return this.http.get<Account>(`${API}/accounts/${id}`); }
  createAccount(name: string): Observable<Account> { return this.http.post<Account>(`${API}/accounts`, { name }); }

  deposit(id: number, amount: number, description: string, key: string) {
    return this.http.post(`${API}/accounts/${id}/deposits`, { amount, description }, this.withKey(key));
  }

  transfer(fromAccountId: number, toAccountId: number, amount: number, description: string, key: string) {
    return this.http.post(`${API}/transfers`, { fromAccountId, toAccountId, amount, description }, this.withKey(key));
  }

  movements(id: number): Observable<Movement[]> { return this.http.get<Movement[]>(`${API}/accounts/${id}/movements`); }

  createSplit(req: CreateSplitRequest): Observable<Split> { return this.http.post<Split>(`${API}/splits`, req); }
  splitsCreatedBy(id: number): Observable<Split[]> { return this.http.get<Split[]>(`${API}/accounts/${id}/splits`); }
  pendingShares(id: number): Observable<PendingShare[]> {
    return this.http.get<PendingShare[]>(`${API}/accounts/${id}/pending-shares`);
  }
  payShare(shareId: number, key: string): Observable<Split> {
    return this.http.post<Split>(`${API}/splits/shares/${shareId}/pay`, {}, this.withKey(key));
  }
  declineShare(shareId: number): Observable<Split> {
    return this.http.post<Split>(`${API}/splits/shares/${shareId}/decline`, {});
  }

  audit(): Observable<AuditReport> { return this.http.get<AuditReport>(`${API}/audit`); }

  settlementPlan(accountIds: number[]): Observable<SettlementPlan> {
    return this.http.get<SettlementPlan>(`${API}/settlements/plan`, { params: { accountIds: accountIds.join(',') } });
  }
}

/** Mensaje legible a partir del error de la API (ProblemDetail) o de red. */
export function errorMessage(err: HttpErrorResponse): string {
  if (err.status === 0) {
    return 'No hay conexión con el servidor. Puedes reintentar: no se cobrará dos veces.';
  }
  return err.error?.detail ?? 'Ocurrió un error inesperado.';
}

/**
 * Llave de idempotencia por intento de operación.
 * Se conserva si falla la red (reintentar con la misma llave nunca cobra dos veces)
 * y se cambia cuando la operación termina, bien o con un error de negocio.
 */
export class IdempotencyKey {
  private current: string | null = null;

  get(): string {
    this.current ??= crypto.randomUUID();
    return this.current;
  }

  settle(err?: HttpErrorResponse): void {
    if (!err || err.status !== 0) {
      this.current = null;
    }
  }
}
