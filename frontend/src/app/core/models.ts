export interface Account {
  id: number;
  name: string;
  balance: number;
  createdAt: string;
}

export interface Movement {
  id: number;
  type: 'DEPOSIT' | 'TRANSFER_IN' | 'TRANSFER_OUT';
  amount: number;
  balanceAfter: number;
  counterpartyAccountId: number | null;
  description: string | null;
  operationId: number;
  createdAt: string;
}

export interface Share {
  id: number;
  debtorAccountId: number;
  debtorName: string;
  amount: number;
  status: 'PENDING' | 'PAID' | 'DECLINED';
  operationId: number | null;
  resolvedAt: string | null;
}

export interface Split {
  id: number;
  creatorAccountId: number;
  creatorName: string;
  description: string;
  totalAmount: number;
  creatorShare: number;
  collected: number;
  pending: number;
  status: 'OPEN' | 'COMPLETED';
  createdAt: string;
  shares: Share[];
}

export interface PendingShare {
  shareId: number;
  splitId: number;
  description: string;
  creditorAccountId: number;
  creditorName: string;
  amount: number;
  createdAt: string;
}

export interface AuditReport {
  ok: boolean;
  totalBalances: number;
  totalDeposits: number;
  mismatches: { accountId: number; balance: number; ledgerSum: number }[];
  paidSharesWithoutTransfer: number;
}

export interface CreateSplitRequest {
  creatorAccountId: number;
  description: string;
  totalAmount: number;
  participantIds?: number[];
  includeCreator?: boolean;
  shares?: { accountId: number; amount: number }[];
}
