package com.josemorales.billetera.settlement;

import java.util.List;
import java.util.Map;

/** Expone el algoritmo (que es de paquete) para probarlo sin base de datos. */
public final class SettlementServiceTestAccess {
    private SettlementServiceTestAccess() {}

    public static List<SettlementService.Payment> simplify(Map<Long, Long> net) {
        return SettlementService.simplify(net);
    }
}
