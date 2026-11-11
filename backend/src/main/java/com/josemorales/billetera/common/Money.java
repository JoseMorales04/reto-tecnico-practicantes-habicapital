package com.josemorales.billetera.common;

/**
 * Reglas de montos. Los montos son pesos colombianos enteros guardados en long:
 * nunca double, para no tener errores de redondeo.
 */
public final class Money {
    /** Tope por operación: limita el daño de un error o de un abuso. */
    public static final long MAX_AMOUNT = 10_000_000L;

    private Money() {}

    public static void requireValidAmount(long amount) {
        if (amount <= 0) {
            throw BusinessException.badRequest("INVALID_AMOUNT", "El monto debe ser mayor que cero");
        }
        if (amount > MAX_AMOUNT) {
            throw BusinessException.badRequest("AMOUNT_TOO_LARGE",
                    "El monto supera el máximo por operación de " + MAX_AMOUNT);
        }
    }
}
