import { Component } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink],
  template: `
    <header class="topbar">
      <a routerLink="/" class="brand">Vaca<span>·</span>Billetera</a>
      <span class="tag">Plata entre personas, sin perder un peso</span>
    </header>
    <main class="container">
      <router-outlet />
    </main>
  `
})
export class AppComponent {}
