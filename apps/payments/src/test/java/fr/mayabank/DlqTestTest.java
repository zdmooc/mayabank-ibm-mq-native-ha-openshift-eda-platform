package fr.mayabank;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DlqTestTest {
    @Test void preservesHeaderAndUtf8Payload() throws Exception {
        byte[] id = new byte[24]; id[0] = 7;
        var message = DlqTest.envelope("LAB.MISSING.TEST", id, "épreuve", 2085);
        DlqTest.validate(message, "LAB.MISSING.TEST", id, "épreuve");
    }
    @Test void rejectsAlteredPayload() throws Exception {
        byte[] id = new byte[24];
        var message = DlqTest.envelope("LAB.MISSING.TEST", id, "original", 2085);
        assertThrows(IllegalStateException.class,
                () -> DlqTest.validate(message, "LAB.MISSING.TEST", id, "altered"));
    }
    @Test void rejectsWrongReason() throws Exception {
        byte[] id = new byte[24];
        var message = DlqTest.envelope("LAB.MISSING.TEST", id, "original", 2035);
        assertThrows(IllegalStateException.class,
                () -> DlqTest.validate(message, "LAB.MISSING.TEST", id, "original"));
    }
}
