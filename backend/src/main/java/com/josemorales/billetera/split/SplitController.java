package com.josemorales.billetera.split;

import com.josemorales.billetera.split.SplitDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SplitController {
    private final SplitService service;

    public SplitController(SplitService service) {
        this.service = service;
    }

    @PostMapping("/splits")
    @ResponseStatus(HttpStatus.CREATED)
    public SplitResponse create(@Valid @RequestBody CreateSplitRequest req) {
        BillSplit split = service.create(req);
        return SplitResponse.from(service.get(split.getId()), service.accountNames());
    }

    @GetMapping("/splits/{id}")
    public SplitResponse get(@PathVariable Long id) {
        return SplitResponse.from(service.get(id), service.accountNames());
    }

    @GetMapping("/accounts/{id}/splits")
    public List<SplitResponse> createdBy(@PathVariable Long id) {
        Map<Long, String> names = service.accountNames();
        return service.createdBy(id).stream().map(s -> SplitResponse.from(s, names)).toList();
    }

    @GetMapping("/accounts/{id}/pending-shares")
    public List<PendingShareResponse> pending(@PathVariable Long id) {
        return service.pendingFor(id);
    }

    @PostMapping("/splits/shares/{shareId}/pay")
    public SplitResponse pay(@PathVariable Long shareId, @RequestHeader("Idempotency-Key") String key) {
        SplitShare share = service.payShare(shareId, key);
        return SplitResponse.from(service.get(share.getSplit().getId()), service.accountNames());
    }

    @PostMapping("/splits/shares/{shareId}/decline")
    public SplitResponse decline(@PathVariable Long shareId) {
        SplitShare share = service.declineShare(shareId);
        return SplitResponse.from(service.get(share.getSplit().getId()), service.accountNames());
    }
}
