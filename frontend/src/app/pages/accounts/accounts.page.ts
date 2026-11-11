import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiService, errorMessage } from '../../core/api.service';
import { Account, AuditReport, SettlementPlan } from '../../core/models';
import { CopPipe } from '../../core/cop.pipe';

@Component({
  selector: 'app-accounts',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, CopPipe],
  template: `
    <section class="card audit" [class.bad]="(audit() && !audit()!.ok) || auditError()">
      @if (audit(); as a) {
        <strong>{{ a.ok ? 'Auditoría OK' : 'Auditoría con diferencias' }}</strong>
        <span>Saldos: {{ a.totalBalances | cop }} · Cargas: {{ a.totalDeposits | cop }}</span>
        <small>La suma de todos los saldos debe ser igual a toda la plata cargada.</small>
      } @else if (auditError()) {
        <strong>No se pudo revisar la auditoría</strong>
        <span>{{ auditError() }}</span>
      } @else {
        <span>Revisando la auditoría…</span>
      }
    </section>

    <section class="card">
      <h2>Cuentas</h2>
      <form class="row" [formGroup]="form" (ngSubmit)="create()">
        <input formControlName="name" placeholder="Nombre de la persona" maxlength="80" />
        <button type="submit" [disabled]="name.invalid || busy()">Crear cuenta</button>
      </form>
      @if (error()) { <p class="error">{{ error() }}</p> }

      @if (accounts().length === 0) {
        <p class="muted">Todavía no hay cuentas. Crea la primera, por ejemplo para quien pagó la cena.</p>
      } @else {
        <ul class="list">
          @for (a of accounts(); track a.id) {
            <li>
              <a [routerLink]="['/cuentas', a.id]">
                <span>#{{ a.id }} · {{ a.name }}</span>
                <strong>{{ a.balance | cop }}</strong>
              </a>
            </li>
          }
        </ul>
      }
    </section>

    @if (accounts().length >= 2) {
      <section class="card">
        <h2>¿Quién le paga a quién?</h2>
        <p class="muted small">Si en un grupo hay varias vacas pendientes, calcula el plan con menos transferencias para quedar a paz y salvo.</p>
        <div class="chips">
          @for (a of accounts(); track a.id) {
            <label class="chip"><input type="checkbox" [checked]="group().has(a.id)" (change)="toggle(a.id)" /> {{ a.name }}</label>
          }
        </div>
        <div class="row" style="margin-top:10px">
          <button (click)="computePlan()" [disabled]="group().size < 2">Calcular plan</button>
        </div>
        @if (plan(); as p) {
          @if (p.payments.length === 0) {
            <p class="hint">Este grupo ya está a paz y salvo.</p>
          } @else {
            <p class="hint">En vez de {{ p.pendingShares }} pagos sueltos, bastan {{ p.payments.length }}:</p>
            <ul class="list">
              @for (pay of p.payments; track $index) {
                <li class="settle"><span>{{ nameOf(pay.fromAccountId) }} le paga a {{ nameOf(pay.toAccountId) }}</span><strong>{{ pay.amount | cop }}</strong></li>
              }
            </ul>
          }
        }
      </section>
    }
  `
})
export class AccountsPage implements OnInit {
  private api = inject(ApiService);

  accounts = signal<Account[]>([]);
  audit = signal<AuditReport | null>(null);
  auditError = signal<string | null>(null);
  group = signal<Set<number>>(new Set());
  plan = signal<SettlementPlan | null>(null);
  error = signal<string | null>(null);
  busy = signal(false);
  name = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(80)] });
  form = new FormGroup({ name: this.name });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.accounts().subscribe({ next: a => this.accounts.set(a), error: e => this.error.set(errorMessage(e)) });
    this.api.audit().subscribe({
      next: r => { this.audit.set(r); this.auditError.set(null); },
      error: (e: HttpErrorResponse) => this.auditError.set(errorMessage(e))
    });
  }

  toggle(id: number): void {
    const next = new Set(this.group());
    next.has(id) ? next.delete(id) : next.add(id);
    this.group.set(next);
    this.plan.set(null);
  }

  computePlan(): void {
    this.api.settlementPlan([...this.group()]).subscribe({
      next: p => { this.plan.set(p); this.error.set(null); },
      error: (e: HttpErrorResponse) => this.error.set(errorMessage(e))
    });
  }

  nameOf(id: number): string {
    return this.accounts().find(a => a.id === id)?.name ?? `cuenta #${id}`;
  }

  create(): void {
    if (this.name.invalid) return;
    this.busy.set(true);
    this.api.createAccount(this.name.value.trim()).subscribe({
      next: () => { this.name.reset(); this.error.set(null); this.busy.set(false); this.load(); },
      error: (e: HttpErrorResponse) => { this.error.set(errorMessage(e)); this.busy.set(false); }
    });
  }
}
