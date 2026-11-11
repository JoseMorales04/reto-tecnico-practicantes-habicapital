import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { Observable } from 'rxjs';
import { ApiService, IdempotencyKey, errorMessage } from '../../core/api.service';
import { Account, Movement, PendingShare, Split } from '../../core/models';
import { CopPipe } from '../../core/cop.pipe';

const MAX_AMOUNT = 10_000_000;
const amountValidators = [Validators.required, Validators.min(1), Validators.max(MAX_AMOUNT), Validators.pattern(/^\d+$/)];

@Component({
  selector: 'app-account-detail',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, CopPipe, DatePipe],
  templateUrl: './account-detail.page.html'
})
export class AccountDetailPage implements OnInit {
  private api = inject(ApiService);
  private fb = inject(FormBuilder);

  /** Viene de la ruta /cuentas/:id */
  id = input.required<string>();
  accountId = computed(() => Number(this.id()));

  account = signal<Account | null>(null);
  others = signal<Account[]>([]);
  movements = signal<Movement[]>([]);
  pending = signal<PendingShare[]>([]);
  splits = signal<Split[]>([]);
  message = signal<{ text: string; ok: boolean } | null>(null);
  busy = signal(false);

  private depositKey = new IdempotencyKey();
  private transferKey = new IdempotencyKey();
  private payKeys = new Map<number, IdempotencyKey>();

  depositForm = this.fb.nonNullable.group({
    amount: ['', amountValidators],
    description: ['', Validators.maxLength(140)]
  });

  transferForm = this.fb.nonNullable.group({
    toAccountId: ['', Validators.required],
    amount: ['', amountValidators],
    description: ['', Validators.maxLength(140)]
  });

  splitForm = this.fb.nonNullable.group({
    description: ['', [Validators.required, Validators.maxLength(140)]],
    totalAmount: ['', amountValidators],
    mode: ['equal' as 'equal' | 'custom'],
    includeCreator: [true],
    participants: this.fb.array<boolean>([]),
    customAmounts: this.fb.array<string>([])
  });

  get participants(): FormArray { return this.splitForm.controls.participants; }
  get customAmounts(): FormArray { return this.splitForm.controls.customAmounts; }

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    const id = this.accountId();
    this.api.account(id).subscribe({ next: a => this.account.set(a), error: e => this.fail(e) });
    this.api.movements(id).subscribe(m => this.movements.set(m));
    this.api.pendingShares(id).subscribe(p => this.pending.set(p));
    this.api.splitsCreatedBy(id).subscribe(s => this.splits.set(s));
    this.api.accounts().subscribe(all => {
      const others = all.filter(a => a.id !== id);
      if (others.length !== this.others().length) {
        this.participants.clear();
        this.customAmounts.clear();
        others.forEach(() => {
          this.participants.push(this.fb.nonNullable.control(false));
          this.customAmounts.push(this.fb.nonNullable.control(''));
        });
      }
      this.others.set(others);
    });
  }

  deposit(): void {
    if (this.depositForm.invalid) return;
    const v = this.depositForm.getRawValue();
    this.run(this.api.deposit(this.accountId(), Number(v.amount), v.description, this.depositKey.get()),
      this.depositKey, 'Saldo cargado', () => this.depositForm.reset());
  }

  transfer(): void {
    if (this.transferForm.invalid) return;
    const v = this.transferForm.getRawValue();
    this.run(this.api.transfer(this.accountId(), Number(v.toAccountId), Number(v.amount), v.description,
      this.transferKey.get()), this.transferKey, 'Transferencia enviada', () => this.transferForm.reset());
  }

  pay(share: PendingShare): void {
    const key = this.payKeys.get(share.shareId) ?? new IdempotencyKey();
    this.payKeys.set(share.shareId, key);
    this.run(this.api.payShare(share.shareId, key.get()), key, `Le pagaste a ${share.creditorName}`);
  }

  decline(share: PendingShare): void {
    this.run(this.api.declineShare(share.shareId), null, 'Cobro rechazado');
  }

  createSplit(): void {
    if (this.splitForm.invalid) return;
    const v = this.splitForm.getRawValue();
    const others = this.others();
    const base = { creatorAccountId: this.accountId(), description: v.description, totalAmount: Number(v.totalAmount) };
    const req = v.mode === 'equal'
      ? { ...base, includeCreator: v.includeCreator,
          participantIds: others.filter((_, i) => v.participants[i]).map(a => a.id) }
      : { ...base,
          shares: others.map((a, i) => ({ accountId: a.id, amount: Number(v.customAmounts[i]) }))
                        .filter(s => s.amount > 0) };
    this.run(this.api.createSplit(req), null, 'Cobro creado: cada participante ya ve lo que debe',
      () => this.splitForm.reset({ mode: v.mode, includeCreator: true }));
  }

  /** Vista previa de la división en partes iguales, con la misma regla de redondeo del backend. */
  equalPreview(): string {
    const v = this.splitForm.getRawValue();
    const total = Number(v.totalAmount);
    const debtors = v.participants.filter(Boolean).length;
    const people = debtors + (v.includeCreator ? 1 : 0);
    if (!total || !debtors || !people) return '';
    const base = Math.floor(total / people);
    const rest = total % people;
    const fmt = (n: number) => new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(n);
    if (v.includeCreator) {
      return `Cada participante paga ${fmt(base)}; tú pones ${fmt(base + rest)}.`;
    }
    return rest === 0 ? `Cada participante paga ${fmt(base)}.`
      : `${rest} participante(s) pagan ${fmt(base + 1)} y el resto ${fmt(base)}, para cuadrar el total exacto.`;
  }

  movementLabel(m: Movement): string {
    const other = m.counterpartyAccountId ? this.nameOf(m.counterpartyAccountId) : '';
    switch (m.type) {
      case 'DEPOSIT': return 'Carga de saldo';
      case 'TRANSFER_IN': return `Recibido de ${other}`;
      case 'TRANSFER_OUT': return `Enviado a ${other}`;
    }
  }

  nameOf(id: number): string {
    return this.others().find(a => a.id === id)?.name ?? `cuenta #${id}`;
  }

  private run(obs: Observable<unknown>, key: IdempotencyKey | null, okText: string, after?: () => void) {
    this.busy.set(true);
    obs.subscribe({
      next: () => {
        key?.settle();
        this.message.set({ text: okText, ok: true });
        this.busy.set(false);
        after?.();
        this.reload();
      },
      error: (e: HttpErrorResponse) => {
        key?.settle(e);
        this.fail(e);
        this.busy.set(false);
        this.reload();
      }
    });
  }

  private fail(e: HttpErrorResponse): void {
    this.message.set({ text: errorMessage(e), ok: false });
  }
}
