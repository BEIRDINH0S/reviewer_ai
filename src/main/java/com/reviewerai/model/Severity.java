package com.reviewerai.model;

/** Gravité d'un {@link Finding}, du plus grave au moins grave. */
public enum Severity {
    /** Bug certain ou quasi certain : NPE, ressource non fermée, condition inversée. */
    HIGH,
    /** Comportement douteux : cas limite non géré, exception avalée. */
    MEDIUM,
    /** Remarque mineure : lisibilité, nommage, code mort. */
    LOW;

    /** Parsing tolérant de la valeur renvoyée par le LLM (casse et libellés approximatifs). */
    public static Severity parse(String raw) {
        if (raw == null) return LOW;
        return switch (raw.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "HIGH", "CRITICAL", "BLOCKER", "ERROR" -> HIGH;
            case "MEDIUM", "MAJOR", "WARNING", "WARN" -> MEDIUM;
            default -> LOW;
        };
    }
}
