package com.josemorales.billetera.settlement;

import com.josemorales.billetera.account.AccountRepository;
import com.josemorales.billetera.common.BusinessException;
import com.josemorales.billetera.split.SplitShare;
import com.josemorales.billetera.split.SplitShareRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * "¿Quién le paga a quién?": con varias vacas pendientes en un grupo, cada persona puede deber y
 * a la vez le pueden deber. En vez de pagar parte por parte, se calcula el saldo neto de cada uno y
 * se propone un plan con pocas transferencias.
 *
 * Algoritmo voraz con dos colas de prioridad (montículos): en cada paso, quien más debe le paga a
 * quien más le deben, por el menor de los dos montos. Cada paso deja al menos a una persona en cero,
 * así que el plan tiene como máximo (personas con saldo distinto de cero) - 1 transferencias.
 * Encontrar el mínimo exacto es un problema NP-difícil; este voraz es la solución práctica.
 */
@Service
public class SettlementService {
    private final SplitShareRepository shares;
    private final AccountRepository accounts;

    public SettlementService(SplitShareRepository shares, AccountRepository accounts) {
        this.shares = shares;
        this.accounts = accounts;
    }

    public record Payment(Long fromAccountId, Long toAccountId, long amount) {}

    public record Plan(Map<Long, Long> netBalances, int pendingShares, List<Payment> payments) {}

    @Transactional(readOnly = true)
    public Plan plan(Collection<Long> accountIds) {
        Set<Long> group = new LinkedHashSet<>(accountIds);
        if (group.size() < 2) {
            throw BusinessException.badRequest("GROUP_TOO_SMALL", "El grupo necesita al menos dos personas");
        }
        for (Long id : group) {
            if (!accounts.existsById(id)) throw BusinessException.notFound("La cuenta " + id);
        }

        // Saldo neto: positivo = le deben; negativo = debe.
        Map<Long, Long> net = new LinkedHashMap<>();
        group.forEach(id -> net.put(id, 0L));
        List<SplitShare> pending = shares.findPendingWithinGroup(group);
        for (SplitShare s : pending) {
            Long creditor = s.getSplit().getCreatorAccountId();
            net.merge(creditor, s.getAmount(), Math::addExact);
            net.merge(s.getDebtorAccountId(), -s.getAmount(), Math::addExact);
        }

        List<Payment> payments = simplify(net);
        return new Plan(net, pending.size(), payments);
    }

    /** Recibe saldos netos que suman cero y devuelve las transferencias que los dejan a todos en cero. */
    static List<Payment> simplify(Map<Long, Long> net) {
        long total = net.values().stream().mapToLong(Long::longValue).sum();
        if (total != 0) {
            throw new IllegalStateException("Los saldos netos no suman cero: " + total);
        }
        // Montículos de máximos: el que más debe y el que más le deben quedan arriba.
        Comparator<long[]> byAmountDesc = (a, b) -> Long.compare(b[1], a[1]);
        PriorityQueue<long[]> creditors = new PriorityQueue<>(byAmountDesc);
        PriorityQueue<long[]> debtors = new PriorityQueue<>(byAmountDesc);
        net.forEach((id, amount) -> {
            if (amount > 0) creditors.add(new long[]{id, amount});
            if (amount < 0) debtors.add(new long[]{id, -amount});
        });

        List<Payment> payments = new ArrayList<>();
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            long[] c = creditors.poll();
            long[] d = debtors.poll();
            long amount = Math.min(c[1], d[1]);
            payments.add(new Payment(d[0], c[0], amount));
            if (c[1] > amount) creditors.add(new long[]{c[0], c[1] - amount});
            if (d[1] > amount) debtors.add(new long[]{d[0], d[1] - amount});
        }
        return payments;
    }
}
