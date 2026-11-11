package com.josemorales.billetera.settlement;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/settlements")
public class SettlementController {
    private final SettlementService service;

    public SettlementController(SettlementService service) {
        this.service = service;
    }

    /** Ejemplo: GET /api/settlements/plan?accountIds=1,2,3 */
    @GetMapping("/plan")
    public SettlementService.Plan plan(@RequestParam List<Long> accountIds) {
        return service.plan(accountIds);
    }
}
