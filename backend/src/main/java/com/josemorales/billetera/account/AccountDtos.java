package com.josemorales.billetera.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AccountDtos {
    private AccountDtos() {}

    public record CreateAccountRequest(@NotBlank @Size(max = 80) String name) {}

    public record AccountResponse(Long id, String name, long balance, Instant createdAt) {
        public static AccountResponse from(Account a) {
            return new AccountResponse(a.getId(), a.getName(), a.getBalance(), a.getCreatedAt());
        }
    }
}
