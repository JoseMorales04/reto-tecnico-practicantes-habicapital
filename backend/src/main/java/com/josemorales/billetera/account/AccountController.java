package com.josemorales.billetera.account;

import com.josemorales.billetera.account.AccountDtos.AccountResponse;
import com.josemorales.billetera.account.AccountDtos.CreateAccountRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@Valid @RequestBody CreateAccountRequest req) {
        return AccountResponse.from(service.create(req.name()));
    }

    @GetMapping
    public List<AccountResponse> list() {
        return service.list().stream().map(AccountResponse::from).toList();
    }

    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable Long id) {
        return AccountResponse.from(service.get(id));
    }
}
