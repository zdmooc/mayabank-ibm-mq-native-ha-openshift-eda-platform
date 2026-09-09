package fr.mayabank;

import java.math.BigDecimal;
import java.util.UUID;

/** Contrat pédagogique v1, sans données bancaires réelles. */
public record Payment(String id, BigDecimal amount, String currency) {
    public Payment {
        if (id == null || !UUID.fromString(id).toString().equals(id))
            throw new IllegalArgumentException("invalid payment id");
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2
                || amount.compareTo(new BigDecimal("1000000")) > 0)
            throw new IllegalArgumentException("invalid amount");
        if (!"EUR".equals(currency)) throw new IllegalArgumentException("unsupported currency");
        amount = amount.setScale(2);
    }

    public String encode() { return "v1|" + id + "|" + amount.toPlainString() + "|" + currency; }

    public static Payment decode(String body) {
        if (body == null || body.length() > 256) throw new IllegalArgumentException("invalid payload size");
        String[] fields = body.split("\\|", -1);
        if (fields.length != 4 || !"v1".equals(fields[0])) throw new IllegalArgumentException("invalid schema");
        // Évite les exposants énormes et les montants non canoniques.
        if (!fields[2].matches("[0-9]{1,7}([.][0-9]{1,2})?")) throw new IllegalArgumentException("invalid amount syntax");
        return new Payment(fields[1], new BigDecimal(fields[2]), fields[3]);
    }
}
