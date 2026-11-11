import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiService, errorMessage } from '../../core/api.service';
import { Account, AuditReport } from '../../core/models';
import { CopPipe } from '../../core/cop.pipe';

@Component({
  selector: 'app-accounts',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, CopPipe],
  template: `
    <section class="card audit" [class.bad]="audit() && !audit()!.ok">
      @if (audit(); as a) {
        <strong>{{ a.ok ? 'Auditoría OK' : 'Auditoría con diferencias' }}</strong>
        <span>Saldos: {{ a.totalBalances | cop }} · Cargas: {{ a.totalDeposits | cop }}</span>
        <small>La suma de todos los saldos debe ser igual a toda la plata cargada.</small>
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
  `
})
export class AccountsPage implements OnInit {
  private api = inject(ApiService);

  accounts = signal<Account[]>([]);
  audit = signal<AuditReport | null>(null);
  error = signal<string | null>(null);
  busy = signal(false);
  name = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(80)] });
  form = new FormGroup({ name: this.name });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.accounts().subscribe({ next: a => this.accounts.set(a), error: e => this.error.set(errorMessage(e)) });
    this.api.audit().subscribe({ next: r => this.audit.set(r) });
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
