package com.josemorales.billetera.account;

import com.josemorales.billetera.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AccountService {
    private final AccountRepository accounts;

    public AccountService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional
    public Account create(String name) {
        return accounts.save(new Account(name.trim()));
    }

    @Transactional(readOnly = true)
    public Account get(Long id) {
        return accounts.findById(id).orElseThrow(() -> BusinessException.notFound("La cuenta " + id));
    }

    @Transactional(readOnly = true)
    public List<Account> list() {
        return accounts.findAll();
    }
}
