package com.josemorales.billetera.split;

import com.josemorales.billetera.account.Account;
import com.josemorales.billetera.account.AccountRepository;
import com.josemorales.billetera.common.BusinessException;
import com.josemorales.billetera.common.Money;
import com.josemorales.billetera.ledger.LedgerService;
import com.josemorales.billetera.ledger.Operation;
import com.josemorales.billetera.ledger.OperationRepository;
import com.josemorales.billetera.split.SplitDtos.CreateSplitRequest;
import com.josemorales.billetera.split.SplitDtos.ShareInput;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class SplitService {
    static final int MAX_PARTICIPANTS = 50;

    private final BillSplitRepository splits;
    private final SplitShareRepository shares;
    private final AccountRepository accounts;
    private final OperationRepository operations;
    private final LedgerService ledger;

    public SplitService(BillSplitRepository splits, SplitShareRepository shares, AccountRepository accounts,
                        OperationRepository operations, LedgerService ledger) {
        this.splits = splits;
        this.shares = shares;
        this.accounts = accounts;
        this.operations = operations;
        this.ledger = ledger;
    }

    @Transactional
    public BillSplit create(CreateSplitRequest req) {
        long total = req.totalAmount();
        Money.requireValidAmount(total);
        requireAccount(req.creatorAccountId());

        Map<Long, Long> debts = (req.shares() != null && !req.shares().isEmpty())
                ? customDebts(req.creatorAccountId(), total, req.shares())
                : equalDebts(req.creatorAccountId(), total, req.participantIds(),
                        req.includeCreator() == null || req.includeCreator());

        long debtsSum = debts.values().stream().mapToLong(Long::longValue).sum();
        long creatorShare = total - debtsSum;
        // Chequeo de seguridad: dividir nunca puede crear ni perder un peso.
        if (creatorShare < 0 || debtsSum + creatorShare != total) {
            throw new IllegalStateException("La división no cuadra con el total");
        }

        BillSplit split = new BillSplit(req.creatorAccountId(), req.description().trim(), total, creatorShare);
        debts.forEach(split::addShare);
        return splits.save(split);
    }

    /**
     * Partes iguales en pesos enteros. Si no da exacto, sobran unos pesos (menos que el número de personas):
     * si el creador participa, los asume él; si no, se reparten de a un peso entre los primeros deudores.
     * Así la suma siempre es exactamente el total.
     */
    Map<Long, Long> equalDebts(Long creatorId, long total, List<Long> participantIds, boolean includeCreator) {
        List<Long> debtors = validateParticipants(creatorId, participantIds);
        int people = debtors.size() + (includeCreator ? 1 : 0);
        long base = total / people;
        long remainder = total % people;
        if (base == 0) {
            throw BusinessException.badRequest("AMOUNT_TOO_SMALL", "El total es muy pequeño para dividirlo entre " + people);
        }
        Map<Long, Long> debts = new LinkedHashMap<>();
        for (int i = 0; i < debtors.size(); i++) {
            long extra = (!includeCreator && i < remainder) ? 1 : 0;
            debts.put(debtors.get(i), base + extra);
        }
        return debts;
    }

    private Map<Long, Long> customDebts(Long creatorId, long total, List<ShareInput> inputs) {
        List<Long> ids = inputs.stream().map(ShareInput::accountId).toList();
        validateParticipants(creatorId, ids);
        Map<Long, Long> debts = new LinkedHashMap<>();
        long sum = 0;
        for (ShareInput in : inputs) {
            Money.requireValidAmount(in.amount());
            sum = Math.addExact(sum, in.amount());
            debts.put(in.accountId(), in.amount());
        }
        if (sum > total) {
            throw BusinessException.badRequest("SHARES_EXCEED_TOTAL", "Las partes suman más que el total");
        }
        return debts;
    }

    private List<Long> validateParticipants(Long creatorId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw BusinessException.badRequest("NO_PARTICIPANTS", "Agrega al menos un participante");
        }
        if (ids.size() > MAX_PARTICIPANTS) {
            throw BusinessException.badRequest("TOO_MANY_PARTICIPANTS", "Máximo " + MAX_PARTICIPANTS + " participantes");
        }
        if (new HashSet<>(ids).size() != ids.size()) {
            throw BusinessException.badRequest("DUPLICATED_PARTICIPANT", "Hay participantes repetidos");
        }
        if (ids.contains(creatorId)) {
            throw BusinessException.badRequest("CREATOR_AS_DEBTOR", "Quien cobra no puede deberse a sí mismo");
        }
        ids.forEach(this::requireAccount);
        return ids;
    }

    /**
     * Paga una parte: transfiere del deudor al creador y marca la parte como pagada, en una sola transacción.
     * Si no hay saldo, no se mueve nada y la parte sigue pendiente.
     */
    @Transactional
    public SplitShare payShare(Long shareId, String idempotencyKey) {
        SplitShare share = shares.findByIdForUpdate(shareId)
                .orElseThrow(() -> BusinessException.notFound("La parte " + shareId));

        if (share.getStatus() == ShareStatus.PAID) {
            // Reintento de la misma petición: se responde igual sin cobrar de nuevo.
            Operation previous = operations.findById(share.getOperationId()).orElseThrow();
            if (previous.getIdempotencyKey().equals(idempotencyKey)) {
                return share;
            }
            throw BusinessException.conflict("ALREADY_PAID", "Esta parte ya fue pagada");
        }
        if (share.getStatus() != ShareStatus.PENDING) {
            throw BusinessException.conflict("SHARE_NOT_PENDING", "Esta parte fue rechazada");
        }

        // Una llave nueva por pago: así una operación vieja nunca se confunde con el pago de esta parte.
        if (operations.findByIdempotencyKey(idempotencyKey).isPresent()) {
            throw BusinessException.conflict("IDEMPOTENCY_KEY_REUSED", "Esa llave de idempotencia ya se usó");
        }

        BillSplit split = share.getSplit();
        Operation op = ledger.transfer(share.getDebtorAccountId(), split.getCreatorAccountId(), share.getAmount(),
                "División: " + split.getDescription(), idempotencyKey);
        share.markPaid(op.getId());
        split.refreshStatus();
        return share;
    }

    @Transactional
    public SplitShare declineShare(Long shareId) {
        SplitShare share = shares.findByIdForUpdate(shareId)
                .orElseThrow(() -> BusinessException.notFound("La parte " + shareId));
        share.decline();
        share.getSplit().refreshStatus();
        return share;
    }

    @Transactional(readOnly = true)
    public BillSplit get(Long splitId) {
        BillSplit split = splits.findById(splitId).orElseThrow(() -> BusinessException.notFound("La división " + splitId));
        split.getShares().size(); // carga las partes dentro de la transacción
        return split;
    }

    @Transactional(readOnly = true)
    public List<BillSplit> createdBy(Long accountId) {
        requireAccount(accountId);
        List<BillSplit> list = splits.findByCreatorAccountIdOrderByIdDesc(accountId);
        list.forEach(s -> s.getShares().size());
        return list;
    }

    @Transactional(readOnly = true)
    public List<SplitDtos.PendingShareResponse> pendingFor(Long accountId) {
        requireAccount(accountId);
        Map<Long, String> names = accountNames();
        return shares.findByDebtorAccountIdAndStatusOrderByIdDesc(accountId, ShareStatus.PENDING).stream()
                .map(s -> new SplitDtos.PendingShareResponse(s.getId(), s.getSplit().getId(),
                        s.getSplit().getDescription(), s.getSplit().getCreatorAccountId(),
                        names.get(s.getSplit().getCreatorAccountId()), s.getAmount(), s.getSplit().getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, String> accountNames() {
        Map<Long, String> names = new HashMap<>();
        for (Account a : accounts.findAll()) names.put(a.getId(), a.getName());
        return names;
    }

    private void requireAccount(Long id) {
        if (id == null || !accounts.existsById(id)) {
            throw BusinessException.notFound("La cuenta " + id);
        }
    }
}
