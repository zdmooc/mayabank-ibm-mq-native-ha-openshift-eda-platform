package fr.mayabank;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class PaymentTest {
    private static final String ID = "3c2a9bee-947c-4b6a-bc0a-862f75063a58";
    @Test void validRoundTrip() {
        Payment payment = new Payment(ID, new BigDecimal("12.30"), "EUR");
        assertEquals(payment, Payment.decode(payment.encode()));
    }
    @Test void rejectsInvalidAndOversizedAmounts() {
        for (String amount : new String[]{"0", "-1", "1.001", "1000001", "1E999999999", "NaN", ""})
            assertThrows(IllegalArgumentException.class, () -> Payment.decode("v1|" + ID + "|" + amount + "|EUR"));
    }
    @Test void rejectsSchemaAndAdditionalFields() {
        assertThrows(IllegalArgumentException.class, () -> Payment.decode("v2|" + ID + "|1|EUR"));
        assertThrows(IllegalArgumentException.class, () -> Payment.decode("v1|" + ID + "|1|EUR|extra"));
    }
    @Test void rejectsUnsupportedCurrencyAndIdentity() {
        assertThrows(IllegalArgumentException.class, () -> Payment.decode("v1|" + ID + "|1|USD"));
        assertThrows(IllegalArgumentException.class, () -> Payment.decode("v1|invalid|1|EUR"));
    }
    @Test void rejectsMissingAndHugePayloads() {
        assertThrows(IllegalArgumentException.class, () -> Payment.decode(null));
        assertThrows(IllegalArgumentException.class, () -> Payment.decode("a".repeat(257)));
    }
    @Test void acceptsBoundaryAndNormalizesCents() {
        assertEquals(new BigDecimal("1000000.00"), Payment.decode("v1|" + ID + "|1000000|EUR").amount());
        assertEquals(new BigDecimal("1.00"), Payment.decode("v1|" + ID + "|1|EUR").amount());
    }
}
