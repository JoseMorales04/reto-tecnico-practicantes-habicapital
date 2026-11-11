import { Routes } from '@angular/router';
import { AccountsPage } from './pages/accounts/accounts.page';
import { AccountDetailPage } from './pages/account-detail/account-detail.page';

export const routes: Routes = [
  { path: '', component: AccountsPage },
  { path: 'cuentas/:id', component: AccountDetailPage },
  { path: '**', redirectTo: '' }
];
