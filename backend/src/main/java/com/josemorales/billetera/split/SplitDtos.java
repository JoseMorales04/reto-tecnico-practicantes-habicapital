package com.josemorales.billetera.split;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class SplitDtos {
    private SplitDtos() {}

    /** Parte personalizada: cuánto debe un participante en particular. */
    public record ShareInput(@NotNull Long accountId, @NotNull @Positive Long amount) {}

    /**
     * Para dividir en partes iguales se envían participantIds (e includeCreator si el creador también consumió).
     * Para montos distintos por persona se envía shares; lo que no cubran las partes lo asume el creador.
     */
    public record CreateSplitRequest(
            @NotNull Long creatorAccountId,
            @NotBlank @Size(max = 140) String description,
            @NotNull @Positive Long totalAmount,
            List<Long> participantIds,
            Boolean includeCreator,
            List<@Valid ShareInput> shares) {}

    public record ShareResponse(Long id, Long debtorAccountId, String debtorName, long amount, long roundingExtra,
                                String status, Long operationId, Instant resolvedAt) {}

    public record SplitResponse(Long id, Long creatorAccountId, String creatorName, String description,
                                long totalAmount, long creatorShare, long creatorRoundingExtra, long collected,
                                long pending, String status, Instant createdAt, List<ShareResponse> shares) {

        static SplitResponse from(BillSplit s, Map<Long, String> names) {
            List<ShareResponse> shares = s.getShares().stream()
                    .map(sh -> new ShareResponse(sh.getId(), sh.getDebtorAccountId(),
                            names.get(sh.getDebtorAccountId()), sh.getAmount(), sh.getRoundingExtra(), sh.getStatus().name(),
                            sh.getOperationId(), sh.getResolvedAt()))
                    .toList();
            return new SplitResponse(s.getId(), s.getCreatorAccountId(), names.get(s.getCreatorAccountId()),
                    s.getDescription(), s.getTotalAmount(), s.getCreatorShare(), s.getCreatorRoundingExtra(), s.collected(), s.pending(),
                    s.getStatus().name(), s.getCreatedAt(), shares);
        }
    }

    /** Lo que le aparece a un deudor: qué debe, a quién y por qué. */
    public record PendingShareResponse(Long shareId, Long splitId, String description, Long creditorAccountId,
                                       String creditorName, long amount, Instant createdAt) {}
}
