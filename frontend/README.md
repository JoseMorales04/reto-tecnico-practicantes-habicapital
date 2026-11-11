# Frontend · Vaca

Angular 18 con componentes standalone, signals y formularios reactivos.

```bash
npm install
npm start   # http://localhost:4200, espera el backend en http://localhost:8080
```

- `src/app/core/api.service.ts`: llamadas a la API y la llave de idempotencia por intento de operación.
- `src/app/pages/accounts`: lista de cuentas, creación y estado de la auditoría.
- `src/app/pages/account-detail`: saldo, carga, transferencia, la vaca (dividir cuentas), cobros pendientes e historial.
