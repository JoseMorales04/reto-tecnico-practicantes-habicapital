package com.josemorales.billetera.ledger;

import com.josemorales.billetera.ledger.LedgerDtos.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class LedgerController {
    private final LedgerService ledger;
    private final IdempotentCall idempotent;

    public LedgerController(LedgerService ledger, IdempotentCall idempotent) {
        this.ledger = ledger;
        this.idempotent = idempotent;
    }

    @PostMapping("/accounts/{id}/deposits")
    public OperationResponse deposit(@PathVariable Long id,
                                     @RequestHeader("Idempotency-Key") String key,
                                     @Valid @RequestBody DepositRequest req) {
        Operation op = idempotent.run(key, () -> ledger.deposit(id, req.amount(), req.description(), key));
        return OperationResponse.from(op);
    }

    @PostMapping("/transfers")
    public OperationResponse transfer(@RequestHeader("Idempotency-Key") String key,
                                      @Valid @RequestBody TransferRequest req) {
        Operation op = idempotent.run(key, () -> ledger.transfer(req.fromAccountId(), req.toAccountId(),
                req.amount(), req.description(), key));
        return OperationResponse.from(op);
    }

    @GetMapping("/accounts/{id}/movements")
    public List<MovementResponse> history(@PathVariable Long id) {
        return ledger.history(id).stream().map(MovementResponse::from).toList();
    }
}
